package com.awbuilds.auraspend.data.classification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.awbuilds.auraspend.AuraSpendApp
import kotlinx.coroutines.flow.first
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
 *  3. A notification summarises how many transactions were auto-saved.
 *
 * Because the queue is the source of truth, a message saved here can never collide with a manual
 * Smart Add save (unique `sourceSmsId` index), and no inbox cursor / prefs dedup sets are needed.
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

        if (result.saved > 0) {
            showNotification(result.saved)
        }

        // RETRY rows still exist (transient failures) but a successful pass is not a job failure;
        // the periodic schedule will pick them up again.
        return true
    }

    private fun showNotification(saved: Int) {
        val channelId = "auto_classification"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Transaction Detection",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for auto-detected bank transactions"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$saved new transaction${if (saved > 1) "s" else ""} saved")
            .setContentText("Detected from your bank messages. Open AuraSpend to review.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(applicationContext).notify(1001, notification)
            }
        } else {
            NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        }
    }

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
            val request = OneTimeWorkRequestBuilder<AutoClassificationWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiresBatteryNotLow(true).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONESHOT,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** Cancels a not-yet-started one-shot pass (used when the toggle goes off). */
        fun cancelPendingWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONESHOT)
        }

        private const val WORK_NAME_ONESHOT = "auto_classification_now"
    }
}