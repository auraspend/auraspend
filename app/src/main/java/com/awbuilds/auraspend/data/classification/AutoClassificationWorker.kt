package com.awbuilds.auraspend.data.classification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

/**
 * Periodically enqueues new bank SMS into the persistent [SmsMessageEntity] queue and processes
 * them one-by-one in the background. Each run is idempotent:
 *
 *  1. [SmsIngestor] snapshots new device messages (keyed on the provider `_id`, `OR IGNORE`).
 *  2. [SmsPipelineProcessor] advances every `NEW` row to SAVED / SKIPPED / FAILED, one at a time.
 *  3. [SmsProgressNotifier] reports the pass as one ongoing progress notification, replaced by a
 *     single summary when the drain finishes.
 *
 * Because the queue is the source of truth, a message saved here can never collide with a manual
 * Smart Add save (unique `sourceSmsId` index), and no inbox cursor / prefs dedup sets are needed.
 *
 * When a pass finishes with work left over it enqueues its own continuation, so a large backlog
 * keeps draining pass-by-pass even after the user swipes the app away (the in-process
 * [BackgroundClassificationCoordinator] has died by then, and the 6-hourly schedule alone would
 * need weeks). Each pass stays short on purpose: WorkManager stops a job that runs too long.
 */
class AutoClassificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        // Only run when the user opted in to automatic classification.
        if (!AutoDetect.isEnabled(applicationContext)) return Result.success()

        // The periodic schedule and consent-triggered one-shot runs use different WorkManager
        // names, so they CAN overlap. A shared mutex guarantees a single pipeline pass at a
        // time: no double LLM batches, no CPU contention, no jank.
        return pipelineMutex.withLock {
            // The SMS scan plus a per-message inference pass is heavy, so run
            // on IO rather than WorkManager's Default dispatcher. The inference engine itself
            // runs on a dedicated background-priority thread; this priority bump keeps the
            // orchestration work (DB, regex) equally polite.
            val oldPriority = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                withContext(Dispatchers.IO) {
                    doProcess().let { if (it) Result.success() else Result.retry() }
                }
            } finally {
                Process.setThreadPriority(oldPriority)
            }
        }
    }

    private suspend fun doProcess(): Boolean {
        val app = applicationContext as? AuraSpendApp ?: return true
        val dao = app.database.smsMessageDao()

        // 1. Snapshot new device messages into the queue (idempotent).
        SmsIngestor.ingest(context = applicationContext, dao = dao)

        // 2. Retention sweep for the unrecognized-SMS list. Runs on this
        //    periodic tick because it is the only reliable recurring entry
        //    point: the retention policy was documented but never called, so the
        //    table grew forever and every Drive backup carried the whole archive.
        app.unrecognizedSms.purgeOlderThan()

        // 3. Process pending rows one at a time with AI enrichment + learned-memory fast path.
        //    Built by the shared factory so this path and the in-process
        //    coordinator cannot drift apart again (they already had).
        val processor = buildSmsPipelineProcessor(
            context = applicationContext,
            app = app,
            dao = dao,
            // Breathing room between LLM generations keeps the UI at full frame rate.
            interMessageDelayMs = INTER_MESSAGE_DELAY_MS
        )
        val result = processor.processPending(maxMessages = MAX_BATCH)

        reportPass(app, result)

        // RETRY rows still exist (transient failures) but a successful pass is not a job failure;
        // the periodic schedule will pick them up again.
        return true
    }

    /**
     * Publishes the pass result to the notification shade and, when work is left, hands the
     * drain to the next pass.
     *
     * Both drivers call this same method so the notification rules exist exactly once.
     */
    private suspend fun reportPass(app: AuraSpendApp, result: SmsProcessResult) {
        val dao = app.database.smsMessageDao()
        val pendingNew = dao.countByStatus(SmsMessageStatus.NEW.name)
        val queueTotal = dao.countAll()
        val moreHistoryMayCome = SmsIngestor.hasMoreHistory(applicationContext)

        if (shouldContinueDraining(pendingNew = pendingNew, moreHistoryMayCome = moreHistoryMayCome)) {
            // Same unique name as runNow's one-shot, REPLACE: exactly one continuation is ever
            // queued, so a backlog cannot stack duplicates of itself.
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WORK_NAME_ONESHOT,
                ExistingWorkPolicy.REPLACE,
                continuationRequest()
            )
        }
        app.smsProgressNotifier.onPassFinished(
            result = result,
            pendingNew = pendingNew,
            queueTotal = queueTotal,
            moreHistoryMayCome = moreHistoryMayCome
        )
    }

    private fun continuationRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<AutoClassificationWorker>()
            .setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).build()
            )
            .build()

    companion object {
        private const val WORK_NAME = "auto_classification"

        /** App-wide gate: only one classification pass runs at any moment (worker or
         *  [BackgroundClassificationCoordinator]). */
        internal val pipelineMutex = Mutex()

        /** Messages classified per worker run. Each LLM call takes ~1-4s on-device.
         *  Kept small so a run finishes quickly and never hogs the CPU for minutes;
         *  remaining rows drain on the next periodic pass. */
        private const val MAX_BATCH = 12

        /** Pause between processed messages to keep the device responsive. */
        private const val INTER_MESSAGE_DELAY_MS = 750L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoClassificationWorker>(
                6, TimeUnit.HOURS
            ).setConstraints(
                // Never burn CPU while the battery is running low.
                Constraints.Builder().setRequiresBatteryNotLow(true).build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * Runs a single classification pass immediately (replaces any pending one-shot run so we
         * never queue duplicates). Used right after consent / SMS permission / turning the toggle on.
         */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONESHOT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoClassificationWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiresBatteryNotLow(true).build()
                    )
                    .build()
            )
        }

        /** Cancels a not-yet-started one-shot pass (used when the toggle goes off). */
        fun cancelPendingWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONESHOT)
        }

        private const val WORK_NAME_ONESHOT = "auto_classification_now"
    }
}

/**
 * Whether the worker should enqueue another pass right now.
 *
 * `pendingNew > 0` is the obvious case. [moreHistoryMayCome] is the subtle one: when the live
 * queue just drained but the history walk has not finished, the next pass would pull and
 * classify a whole new page — so stopping here would end the drain with the message backlog
 * still half-read, and the user would wait six hours for the rest. Pure so the rule is testable
 * without WorkManager.
 */
internal fun shouldContinueDraining(pendingNew: Int, moreHistoryMayCome: Boolean): Boolean =
    pendingNew > 0 || moreHistoryMayCome
