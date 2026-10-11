package com.awbuilds.auraspend.data.classification
import com.awbuilds.auraspend.ui.classification.SmsInfo

/**
 * Where the inbox scan has got to.
 *
 * Three values, because two different questions have to be answered at once and
 * one number cannot answer both:
 *
 *  - [newestIngested] is a **high-water mark**: everything above it has been
 *    queued. It answers "what is new?" without re-reading the whole inbox, and it
 *    is the only reason a long-time user's scan stays cheap.
 *  - [historyBefore] is the **cursor of the downwards walk** through older
 *    messages: the next history page is everything below it. 0 means "not
 *    walking".
 *  - [historyWalkDone] records that the walk reached the bottom of the inbox, so
 *    it does not restart on every app start.
 *
 * The scan used to be a single high-water mark advanced to
 * `newest - 1` on every pass. With `ORDER BY DATE DESC LIMIT 500` that made the
 * first pass permanently authoritative: on a fresh install (or any gap of more
 * than 500 bank-like messages) only the newest 500 were ever queued, and every
 * older message sat below a cursor that claimed to have covered it — never
 * ingested, never classified, never surfaced. Advancing only when the batch was
 * short does not fix it either, because the next scan then re-reads the same
 * newest 500 rows for ever; a *separate* walk cursor is what makes progress
 * possible with a provider query that can only return the newest page.
 *
 * `SmsIngestPlannerTest` drives this against a simulated 1,200-message inbox and
 * asserts that every message is queued exactly once.
 */
internal data class SmsIngestCursor(
    val newestIngested: Long = 0L,
    val historyBefore: Long = 0L,
    val historyWalkDone: Boolean = false
)

/** Which of the three scans a page request is. */
internal enum class InboxScanPhase { FIRST_PASS, NEW_ARRIVALS, HISTORY_WALK }

/**
 * One page request against the provider. Timestamp bounds are exclusive, which is
 * what makes a boundary message (same millisecond as the cursor) re-readable
 * rather than permanently skippable — re-reads are ignored by the queue's
 * `OR IGNORE` insert.
 */
internal data class InboxScan(
    val sinceTimestamp: Long,
    val beforeTimestamp: Long,
    val oldestFirst: Boolean,
    val phase: InboxScanPhase
)

internal object SmsIngestPlanner {

    /** No upper bound. */
    const val NO_BOUND: Long = Long.MAX_VALUE

    /**
     * The live scan: new messages, or the newest page on a cold start.
     *
     * The cold start is newest-first, because the newest messages are the ones
     * the user is looking at today. Every later live scan is **oldest-first above
     * the high-water mark**: if more than one page arrived since the last pass,
     * that walks forward through them, where newest-first would return the same
     * page for ever and strand the rest.
     */
    fun liveScan(cursor: SmsIngestCursor): InboxScan =
        if (cursor.newestIngested <= 0L) {
            InboxScan(0L, NO_BOUND, oldestFirst = false, phase = InboxScanPhase.FIRST_PASS)
        } else {
            InboxScan(
                sinceTimestamp = cursor.newestIngested,
                beforeTimestamp = NO_BOUND,
                oldestFirst = true,
                phase = InboxScanPhase.NEW_ARRIVALS
            )
        }

    /**
     * The next history page, or null when there is nothing older to walk.
     *
     * A cold start walks from the oldest message of the first page; an upgrade
     * walks from its own high-water mark, which is exactly the boundary below
     * which messages were stranded by the old cursor.
     */
    fun historyScan(cursor: SmsIngestCursor): InboxScan? = when {
        cursor.historyWalkDone -> null
        cursor.historyBefore > 0L -> InboxScan(
            sinceTimestamp = 0L,
            beforeTimestamp = cursor.historyBefore,
            oldestFirst = false,
            phase = InboxScanPhase.HISTORY_WALK
        )
        cursor.newestIngested > 0L -> InboxScan(
            sinceTimestamp = 0L,
            beforeTimestamp = cursor.newestIngested + 1,
            oldestFirst = false,
            phase = InboxScanPhase.HISTORY_WALK
        )
        // Nothing scanned yet: the first pass seeds the walk from its oldest row.
        else -> null
    }

    /**
     * [historyScan], held back while the live queue is not empty.
     *
     * The queue gate is the difference between a backfill and a flood: history is
     * pulled one page at a time, and only once everything that arrived live has
     * been classified. Without it a fresh install would pull its whole history in
     * a few passes and grow the queue far faster than the classifier drains it.
     */
    fun historyScanWhenIdle(cursor: SmsIngestCursor, pendingMessages: Int): InboxScan? =
        if (pendingMessages == 0) historyScan(cursor) else null

    /**
     * Advances the cursor after a live scan.
     *
     * A full page on a cold start means the inbox holds more than one page, so the
     * walk starts below the oldest row of this page. Any other outcome means the
     * live scan has caught up and there is no history left to walk.
     */
    fun afterLiveScan(
        cursor: SmsIngestCursor,
        scan: InboxScan,
        batch: List<SmsInfo>,
        cap: Int
    ): SmsIngestCursor {
        if (batch.isEmpty()) {
            return if (scan.phase == InboxScanPhase.FIRST_PASS) {
                cursor.copy(historyWalkDone = true)
            } else {
                cursor
            }
        }
        val advanced = cursor.copy(newestIngested = batch.maxOf { it.timestamp } - 1)
        return when {
            scan.phase != InboxScanPhase.FIRST_PASS -> advanced
            batch.size < cap -> advanced.copy(historyWalkDone = true)
            else -> advanced.copy(historyBefore = batch.minOf { it.timestamp })
        }
    }

    /**
     * Advances the cursor after a history page.
     *
     * A short page means this window is exhausted, and because each window was
     * requested strictly below the previous one, reaching the bottom of one means
     * the bottom of the inbox: the walk is done.
     */
    fun afterHistoryScan(
        cursor: SmsIngestCursor,
        batch: List<SmsInfo>,
        cap: Int
    ): SmsIngestCursor =
        if (batch.size < cap) {
            cursor.copy(historyBefore = 0L, historyWalkDone = true)
        } else {
            cursor.copy(historyBefore = batch.minOf { it.timestamp })
        }
}
