package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.ui.classification.SmsInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The triage inbox rule, and the undo round-trip that depends on it.
 *
 * Handled a message by swiping it — saved or dismissed — and it should leave the
 * list; take the undo and it should come back. "Coming back" is not separate logic,
 * it is the same predicate applied to a row whose status the undo reverted, so both
 * halves are pinned here.
 */
class TriageInboxTest {

    @Test
    fun `saved and dismissed messages are not in the inbox`() {
        val all = listOf(
            msg("a", saved = true, status = SmsStatus.SAVED),
            msg("b", dismissed = true, status = SmsStatus.SKIPPED),
            msg("c")
        )
        assertEquals(listOf("c"), pendingInbox(all).map { it.sms.id })
    }

    @Test
    fun `a newly classified message is still in the inbox`() {
        // The regression this guards: SKIPPED used to map onto SmsStatus.CLASSIFIED,
        // the same status a freshly-classified message has, so a dismissed message
        // was indistinguishable from a ready-to-save one.
        val classified = msg("a", status = SmsStatus.CLASSIFIED)
        val skipped = msg("b", dismissed = true, status = SmsStatus.CLASSIFIED)
        assertTrue("classified must stay in the inbox", classified in pendingInbox(listOf(classified)))
        assertTrue("dismissed must leave it", skipped !in pendingInbox(listOf(skipped)))
    }

    @Test
    fun `undoing a dismiss brings the message back`() {
        val dismissed = msg("a", dismissed = true, status = SmsStatus.SKIPPED)
        assertTrue("dismissed must be gone", dismissed !in pendingInbox(listOf(dismissed)))

        // The undo handler reverts the stored status; the observer re-emits the row,
        // and the same predicate now admits it.
        val afterUndo = dismissed.copy(isDismissed = false, status = SmsStatus.CLASSIFYING)
        assertTrue("undone dismiss must return", afterUndo in pendingInbox(listOf(afterUndo)))
    }

    @Test
    fun `undoing a save brings the message back`() {
        val saved = msg("a", saved = true, status = SmsStatus.SAVED)
        assertTrue("saved must be gone", saved !in pendingInbox(listOf(saved)))

        val afterUndo = saved.copy(isSaved = false, status = SmsStatus.CLASSIFYING)
        assertTrue("undone save must return", afterUndo in pendingInbox(listOf(afterUndo)))
    }

    @Test
    fun `a failed message stays in the inbox so it can be retried`() {
        val failed = msg("a", status = SmsStatus.FAILED)
        assertTrue(failed in pendingInbox(listOf(failed)))
    }

    @Test
    fun `an empty queue yields an empty inbox`() {
        assertTrue(pendingInbox(emptyList()).isEmpty())
    }

    @Test
    fun `the skipped inbox lists exactly the dismissed messages`() {
        // The audit surface: skipped rows are absent from the triage inbox and
        // the pick-list, and this is the only list that shows them.
        val saved = msg("s", saved = true)
        val pending = msg("p")
        val dismissed = msg("d", dismissed = true, status = SmsStatus.SKIPPED)

        assertEquals(listOf("d"), skippedInbox(listOf(saved, pending, dismissed)).map { it.sms.id })
        assertTrue(skippedInbox(listOf(saved, pending)).isEmpty())
    }

    private fun msg(
        id: String,
        saved: Boolean = false,
        dismissed: Boolean = false,
        status: SmsStatus = SmsStatus.PENDING
    ) = ClassifiedSms(
        sms = SmsInfo(id = id, address = "BANK", body = "Rs 1.00 spent", timestamp = 0L),
        parsed = ParsedBankMessage(),
        isSaved = saved,
        isDismissed = dismissed,
        status = status
    )
}
