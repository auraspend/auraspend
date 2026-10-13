package com.awbuilds.auraspend.data.classification

/**
 * What the queue says about the drain right now.
 *
 * Derived from [ClassifiedSms.status] — i.e. from the same rows the UI already observes — so
 * there is exactly one source of truth for "how far along are we". The notification reads the
 * same numbers from the database.
 */
data class QueueProgress(
    /** Every row in the queue: the denominator of "X of Y". */
    val total: Int,
    /** Rows that reached a terminal state (saved / skipped / failed). */
    val processed: Int,
    /** Rows still waiting to be classified. */
    val pending: Int,
    /** Rows being classified by a pass right now. */
    val inFlight: Int,
    val saved: Int,
    val skipped: Int
)

/**
 * Summarises a queue snapshot. Pure so the arithmetic can be pinned without a database.
 *
 * [total] grows as the history walk adds pages, so [processed] can appear to jump when new
 * rows arrive — that is honest (only terminal rows are counted as processed), which is why
 * the UI reports the pair rather than a percentage.
 */
fun queueProgress(rows: List<ClassifiedSms>): QueueProgress {
    var saved = 0
    var skipped = 0
    var pending = 0
    var inFlight = 0
    var processed = 0
    rows.forEach { row ->
        when (row.status) {
            SmsStatus.SAVED -> { saved++; processed++ }
            SmsStatus.SKIPPED -> { skipped++; processed++ }
            SmsStatus.FAILED -> processed++
            // Awaiting the user's decision: not terminal, so it counts as still to do.
            // No row mapper can produce this status today, but the type allows it and the
            // honest reading of "classified but unsaved" is "not processed yet".
            SmsStatus.CLASSIFIED -> pending++
            SmsStatus.PENDING -> pending++
            SmsStatus.CLASSIFYING -> inFlight++
        }
    }
    return QueueProgress(
        total = rows.size,
        processed = processed,
        pending = pending,
        inFlight = inFlight,
        saved = saved,
        skipped = skipped
    )
}
