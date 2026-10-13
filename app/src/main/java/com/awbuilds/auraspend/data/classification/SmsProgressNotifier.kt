package com.awbuilds.auraspend.data.classification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.AuraLog

private const val TAG = "SmsProgressNotifier"

/**
 * Same id the per-run "N new transactions saved" notification used. Reusing it means an
 * upgrade replaces the old notification instead of leaving a stale one behind, and lets the
 * progress notification turn into its own summary in place.
 */
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "auto_classification"

/**
 * What the categorisation notification should say.
 *
 * A sealed hierarchy rather than four booleans because the four states are genuinely different
 * shapes: a determinate bar, an indeterminate bar, a one-line summary, and a one-line result.
 * Which one applies is decided by [copyForSnapshot], which is pure so the rules can be pinned in
 * a unit test without a NotificationManager.
 *
 * Public (rather than internal) because [SmsProgressNotifier] is a public seam on
 * [AuraSpendApp] and its callers' lambdas are typed with it.
 */
sealed interface SmsNotificationCopy {
    /** A progress notification cannot be swiped away; a result one can. */
    val ongoing: Boolean

    val autoCancel: Boolean

    /** "Categorizing messages… 34 of 210". */
    data class Progress(val processed: Int, val queueTotal: Int) : SmsNotificationCopy {
        override val ongoing: Boolean = true
        override val autoCancel: Boolean = false
    }

    /**
     * Nothing left to classify right now, but the inbox history walk may still add more
     * messages. Shows the bar without numbers rather than a completed "210 of 210".
     */
    data object Scanning : SmsNotificationCopy {
        override val ongoing: Boolean = true
        override val autoCancel: Boolean = false
    }

    /** The single end-of-drain result: "210 messages reviewed · 148 saved · 62 skipped". */
    data class Summary(val reviewed: Int, val saved: Int, val skipped: Int) : SmsNotificationCopy {
        override val ongoing: Boolean = false
        override val autoCancel: Boolean = true
    }

    /** A batch too small to be worth watching: "2 new transactions saved". */
    data class Saved(val saved: Int) : SmsNotificationCopy {
        override val ongoing: Boolean = false
        override val autoCancel: Boolean = true
    }
}

/** Copy builders. Pure: resource ids and arguments, no Android types. */
internal object SmsProgressCopy {

    /**
     * A determinate bar below this size is noise: a notification that appears, ticks once and
     * disappears tells the user less than the single result notification that follows it.
     */
    const val MIN_BATCH_FOR_PROGRESS = 4

    /** The "X of Y" progress notification, or null when the batch is too small to warrant one. */
    fun progress(processed: Int, queueTotal: Int): SmsNotificationCopy? =
        if (queueTotal < MIN_BATCH_FOR_PROGRESS) {
            null
        } else {
            SmsNotificationCopy.Progress(
                processed = processed.coerceIn(0, queueTotal),
                queueTotal = queueTotal
            )
        }

    fun scanning(): SmsNotificationCopy = SmsNotificationCopy.Scanning

    /** The single end-of-drain result. */
    fun summary(reviewed: Int, saved: Int, skipped: Int): SmsNotificationCopy =
        SmsNotificationCopy.Summary(reviewed = reviewed, saved = saved, skipped = skipped)

    /** A batch too small to be worth a bar: "2 new transactions saved". */
    fun saved(count: Int): SmsNotificationCopy = SmsNotificationCopy.Saved(count)
}

/** Running totals for the drain the current progress notification is describing. */
internal data class DrainTally(val saved: Int = 0, val skipped: Int = 0, val failed: Int = 0) {
    val reviewed: Int get() = saved + skipped + failed

    operator fun plus(result: SmsProcessResult): DrainTally =
        DrainTally(
            saved = saved + result.saved,
            skipped = skipped + result.skipped,
            failed = failed + result.failed
        )
}

/**
 * Everything the notification decision needs to know about the pass that just finished.
 *
 * [pendingNew] and [queueTotal] come from the queue itself, so the numbers in the notification
 * and the numbers on the Classification screen are the same numbers.
 */
internal data class SmsDrainSnapshot(
    /** Rows still waiting to be classified. */
    val pendingNew: Int,
    /** Every row in the queue — the denominator, which can grow as history pages arrive. */
    val queueTotal: Int,
    /** The inbox history walk still has pages to pull. */
    val moreHistoryMayCome: Boolean,
    /** Transactions this single pass saved. */
    val passSaved: Int,
    /** Whether a progress notification is on screen right now. */
    val progressWasShowing: Boolean,
    /** Totals for the drain the progress notification has been describing. */
    val drain: DrainTally
)

/**
 * Which notification (if any) should follow a finished pass. Pure, so every branch is testable.
 *
 * The rules, in order:
 *  1. Still draining → keep the determinate bar going (it is the status surface).
 *  2. Nothing pending but history may still arrive → indeterminate bar.
 *  3. A progress notification was showing and the drain is over → replace it with ONE summary.
 *  4. A small batch with nothing else to say → the single "N saved" result notification.
 */
internal fun copyForSnapshot(snapshot: SmsDrainSnapshot): SmsNotificationCopy? = when {
    snapshot.pendingNew > 0 ->
        SmsProgressCopy.progress(
            processed = snapshot.queueTotal - snapshot.pendingNew,
            queueTotal = snapshot.queueTotal
        )

    snapshot.moreHistoryMayCome -> SmsProgressCopy.scanning()

    snapshot.progressWasShowing -> SmsProgressCopy.summary(
        reviewed = snapshot.drain.reviewed,
        saved = snapshot.drain.saved,
        skipped = snapshot.drain.skipped
    )

    snapshot.passSaved > 0 -> SmsProgressCopy.saved(snapshot.passSaved)

    else -> null
}

/**
 * The one place the categorisation feature talks to the notification shade.
 *
 * Both background entry points — the WorkManager pass and the in-process coordinator — call
 * [onPassFinished] once per pass. The old behaviour posted "N new transactions saved" on every
 * pass, so a backfill of two months of history produced a notification every six hours (and on
 * every screen entry) for as long as the backlog lasted. Now:
 *
 *  - while a backlog drains there is ONE ongoing, silent, determinate notification;
 *  - when it finishes that notification is replaced by ONE summary and then goes away;
 *  - batches too small to watch get a single result notification;
 *  - nothing is posted while the user is looking at the app — the queue itself is the status
 *    surface there, and [ClassificationSmsTab] renders it.
 *
 * Every post is best-effort: a notification failure must never fail a classification pass
 * (AGENTS.md hard requirement 1), so failures are logged and dropped.
 */
class SmsProgressNotifier(
    private val context: Context,
    /** Read before every post: while an activity is visible the app is the status surface. */
    private val isForeground: () -> Boolean = ::appInForeground,
    /** Seam so the decision logic can be tested without a NotificationManager. */
    private val onCopy: (SmsNotificationCopy) -> Unit = { copy -> publish(context, copy) }
) {

    private var progressShowing = false
    private var drain = DrainTally()

    /**
     * Reports a finished pass and updates the notification to match.
     *
     * @param pendingNew  rows still [SmsMessageStatus.NEW] after the pass
     * @param queueTotal  total rows in the queue
     * @param moreHistoryMayCome  the inbox history walk still has pages left
     */
    @Synchronized
    fun onPassFinished(
        result: SmsProcessResult,
        pendingNew: Int,
        queueTotal: Int,
        moreHistoryMayCome: Boolean
    ) {
        if (isForeground()) {
            // The user is looking at the app: cancel the shade copy, drop the accumulation.
            // The in-app progress continues to describe the same queue.
            cancel()
            return
        }

        drain += result
        val copy = copyForSnapshot(
            SmsDrainSnapshot(
                pendingNew = pendingNew,
                queueTotal = queueTotal,
                moreHistoryMayCome = moreHistoryMayCome,
                passSaved = result.saved,
                progressWasShowing = progressShowing,
                drain = drain
            )
        )

        if (copy != null) {
            onCopy(copy)
            progressShowing = copy is SmsNotificationCopy.Progress || copy is SmsNotificationCopy.Scanning
        }

        if (!progressShowing) {
            // The drain closed (or never needed a bar): the next drain starts from zero.
            drain = DrainTally()
        }
    }

    /** Removes any progress notification and forgets the running drain totals. */
    @Synchronized
    fun cancel() {
        progressShowing = false
        drain = DrainTally()
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }.onFailure {
            AuraLog.w(TAG, "Could not cancel the categorisation notification", it)
        }
    }
}

/** True while any activity of this process is visible. */
private fun appInForeground(): Boolean = runCatching {
    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}.getOrDefault(false)

/**
 * Posts one notification for [copy].
 *
 * Same shape as the model-download notifications (channel creation + `runCatching`), so this
 * file adds no new infrastructure. The channel is IMPORTANCE_LOW: a progress notification has
 * no reason to buzz or peek.
 */
private fun publish(context: Context, copy: SmsNotificationCopy) {
    // Inline (rather than in a helper) so lint's MissingPermission detector can see the
    // guard: the model-download notifications needed three baseline entries for the exact
    // same call, and this feature is small enough not to deserve a fourth.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        // The user denied notifications. Everything still works; the Classification screen
        // is the status surface instead.
        return
    }
    runCatching {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(copy.ongoing)
            .setAutoCancel(copy.autoCancel)
            // An ongoing notification that beeped on every tick would be worse than the
            // per-pass notifications this replaces.
            .setOnlyAlertOnce(true)
            .setSilent(true)

        when (copy) {
            is SmsNotificationCopy.Progress -> {
                builder
                    .setContentTitle(context.getString(R.string.sms_categorizing_title))
                    .setContentText(
                        context.getString(
                            R.string.sms_categorizing_count,
                            copy.processed,
                            copy.queueTotal
                        )
                    )
                    .setProgress(copy.queueTotal, copy.processed, false)
            }

            SmsNotificationCopy.Scanning -> {
                builder
                    .setContentTitle(context.getString(R.string.sms_categorizing_title))
                    .setProgress(0, 0, true)
            }

            is SmsNotificationCopy.Summary -> {
                builder
                    .setContentTitle(
                        context.resources.getQuantityString(
                            R.plurals.sms_categorizing_reviewed,
                            copy.reviewed,
                            copy.reviewed
                        )
                    )
                    .setContentText(
                        context.getString(
                            R.string.sms_categorizing_summary,
                            copy.saved,
                            copy.skipped
                        )
                    )
                    .setProgress(0, 0, false)
            }

            is SmsNotificationCopy.Saved -> {
                builder
                    .setContentTitle(
                        context.resources.getQuantityString(
                            R.plurals.sms_categorizing_saved,
                            copy.saved,
                            copy.saved
                        )
                    )
                    .setProgress(0, 0, false)
            }
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }.onFailure {
        AuraLog.w(TAG, "Could not post the categorisation notification", it)
    }
}

private fun ensureChannel(context: Context) {
    // No SDK_INT guard: minSdk is 30, so NotificationChannel always exists. The model-download
    // notifications guard this call and paid three lint baseline entries for the privilege.
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        CHANNEL_ID,
        // Localized: the channel name is user-visible in system settings, so it is a string
        // like every other user-facing string, not a hardcoded English literal.
        context.getString(R.string.sms_channel_name),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = context.getString(R.string.sms_channel_description)
    }
    manager.createNotificationChannel(channel)
}
