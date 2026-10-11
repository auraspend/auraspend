package com.awbuilds.auraspend.data.classification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.ContextCompat
import com.awbuilds.auraspend.AuraSpendApp
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
 * Keeps draining the persistent SMS queue whenever the app sits in the BACKGROUND but its
 * process is still alive (not swiped from recents).
 *
 * Wired to ProcessLifecycleOwner by [AuraSpendApp]:
 *  - `onStop`  (app went to background) -> [start]: loop of ingest -> classify-one-by-one
 *    until the queue is empty, then a slow idle poll picks up messages that arrive later.
 *  - `onStart` (app came back)          -> [stop]: the user's device belongs to them again;
 *    zero LLM/CPU work while any activity is visible.
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
                }
            }

            // Queue drained: idle-poll slowly for newly arrived messages while still in the
            // background. Otherwise hand control back to the system between batches.
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
