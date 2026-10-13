package com.awbuilds.auraspend.data.classification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.ContextCompat
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps draining the persistent SMS queue whenever the app's process is alive — in the
 * BACKGROUND and in the FOREGROUND alike.
 *
 * Wired to ProcessLifecycleOwner by [AuraSpendApp]: both `onStart` (app came to the front) and
 * `onStop` (app went back) call [start], which is idempotent, so the loop runs continuously
 * while the process lives. It used to stop on `onStart` ("zero LLM work while any activity is
 * visible"), which meant a user who opened the Classification screen watched a frozen message
 * list: the queue only drained once they put the app away. The owner asked to watch the
 * categorisation happen one-by-one, and that requires the work to keep going — at the same
 * polite pacing (background thread priority, a pause between messages), which is what keeps a
 * long batch from stealing frames.
 *
 * While an activity is visible the loop runs WITHOUT posting notifications: [SmsProgressNotifier]
 * gates on that, because the Classification screen already shows the same queue live. The shade
 * takes over the moment the app leaves the screen.
 *
 * All passes share [AutoClassificationWorker] `pipelineMutex`, so this coordinator and the
 * periodic WorkManager job can never run simultaneously.
 */
object BackgroundClassificationCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loopJob: Job? = null

    @Synchronized
    fun start(applicationContext: Context) {
        if (loopJob?.isActive == true) return
        if (!eligible(applicationContext)) return

        loopJob = scope.launch {
            val oldPriority = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                drainLoop(applicationContext)
            } finally {
                Process.setThreadPriority(oldPriority)
            }
        }
    }

    @Synchronized
    fun stop() {
        val job = loopJob ?: return
        loopJob = null
        scope.launch { job.cancelAndJoin() }
    }

    private fun eligible(context: Context): Boolean {
        val smsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return smsGranted && AutoDetect.isEnabled(context)
    }

    private suspend fun drainLoop(appContext: Context) {
        val app = appContext as? AuraSpendApp ?: return
        val dao = app.database.smsMessageDao()

        // Cancellation (stop()) surfaces through delay()/withLock suspension points.
        while (true) {
            var attempted = 0

            AutoClassificationWorker.pipelineMutex.withLock {
                if (!eligible(appContext)) return

                withContext(Dispatchers.IO) {
                    // 1. Snapshot new device messages into the queue (idempotent).
                    runCatching { SmsIngestor.ingest(context = appContext, dao = dao) }

                    // 2. Classify one-by-one with AI enrichment + learned-memory fast path.
                    //    Constructed by the shared factory: this path used to omit
                    //    `recordUnrecognized`, so unreadable-but-real money
                    //    movements vanished here while the worker recorded them.
                    val processor = buildSmsPipelineProcessor(
                        context = appContext,
                        app = app,
                        dao = dao,
                        interMessageDelayMs = INTER_MESSAGE_DELAY_MS
                    )
                    val result = processor.processPending(maxMessages = MESSAGES_PER_PASS)
                    attempted = result.attempted

                    // 3. Same reporting the worker does, so the notification (and its
                    //    foreground gate) behaves identically whichever driver ran the pass.
                    app.smsProgressNotifier.onPassFinished(
                        result = result,
                        pendingNew = dao.countByStatus(SmsMessageStatus.NEW.name),
                        queueTotal = dao.countAll(),
                        moreHistoryMayCome = SmsIngestor.hasMoreHistory(appContext)
                    )
                }
            }

            // Queue drained: idle-poll slowly for newly arrived messages. Otherwise hand
            // control back to the system between batches.
            delay(if (attempted > 0) BATCH_GAP_MS else IDLE_POLL_MS)
        }
    }

    /** Messages per pass — small so each pass yields to the system quickly. */
    private const val MESSAGES_PER_PASS = 8

    /** Pause between messages inside a pass (LLM generations are the heavy part). */
    private const val INTER_MESSAGE_DELAY_MS = 750L

    /** Breathing room between consecutive non-empty passes. */
    private const val BATCH_GAP_MS = 2_000L

    /** Poll cadence once the queue is empty. */
    private const val IDLE_POLL_MS = 60_000L
}
