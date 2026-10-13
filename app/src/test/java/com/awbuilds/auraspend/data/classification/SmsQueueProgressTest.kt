package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.ui.classification.SmsInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind "Categorizing 34 of 210".
 *
 * Pure on purpose: the numbers the Classification screen shows and the numbers the background
 * notification shows come from the same rows, so a rule that changes here has to be pinned
 * somewhere without a database.
 */
class SmsQueueProgressTest {

    private fun msg(id: String, status: SmsStatus) = ClassifiedSms(
        sms = SmsInfo(id = id, address = "BANK", body = "Rs 1.00 spent", timestamp = 0L),
        parsed = ParsedBankMessage(),
        status = status
    )

    @Test
    fun `an empty queue has nothing to report`() {
        val progress = queueProgress(emptyList())

        assertEquals(0, progress.total)
        assertEquals(0, progress.processed)
        assertEquals(0, progress.pending)
        assertEquals(0, progress.inFlight)
        assertEquals(0, progress.saved)
        assertEquals(0, progress.skipped)
    }

    @Test
    fun `terminal rows are processed and pending rows are not`() {
        val progress = queueProgress(
            listOf(
                msg("a", SmsStatus.SAVED),
                msg("b", SmsStatus.SKIPPED),
                msg("c", SmsStatus.FAILED),
                msg("d", SmsStatus.PENDING),
                msg("e", SmsStatus.CLASSIFYING)
            )
        )

        assertEquals(5, progress.total)
        assertEquals(3, progress.processed)
        assertEquals(1, progress.pending)
        assertEquals(1, progress.inFlight)
        assertEquals(1, progress.saved)
        assertEquals(1, progress.skipped)
    }

    @Test
    fun `an unsaved but classified message counts as still to do`() {
        // Classified and awaiting the user's decision is not a finished row.
        val progress = queueProgress(listOf(msg("a", SmsStatus.CLASSIFIED)))

        assertEquals(1, progress.pending)
        assertEquals(0, progress.processed)
    }

    @Test
    fun `a history page arriving mid-drain grows the total without moving processed`() {
        // The denominator is allowed to grow: `queueTotal` is every row in the queue, and new
        // NEW rows raise it without claiming to be processed. That is why the UI reports the
        // pair rather than a percentage that would jump backwards.
        val midDrain = queueProgress(
            List(10) { msg("p$it", SmsStatus.SAVED) } +
                List(20) { msg("n$it", SmsStatus.PENDING) }
        )
        assertEquals(30, midDrain.total)
        assertEquals(10, midDrain.processed)

        val afterPage = queueProgress(
            List(10) { msg("p$it", SmsStatus.SAVED) } +
                List(120) { msg("n$it", SmsStatus.PENDING) }
        )
        assertEquals(130, afterPage.total)
        assertEquals("processed did not move for rows nobody worked on", 10, afterPage.processed)
    }
}
