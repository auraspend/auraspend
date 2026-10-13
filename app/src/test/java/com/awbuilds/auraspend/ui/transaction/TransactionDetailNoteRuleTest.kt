package com.awbuilds.auraspend.ui.transaction

import com.awbuilds.auraspend.data.classification.BankMessageParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Note row on the transaction detail page is hidden when it is only
 * pipeline filler — the merchant repeated, or the message truncated the way
 * `BankMessageParser` truncates it — because the Original-message card shows
 * the whole text right below it.
 *
 * The direction of error matters: an edited note must never be hidden, so the
 * comparison is exact and the tests pin both sides. Bodies are synthetic.
 */
class TransactionDetailNoteRuleTest {

    private val longBody =
        "Rs.315.00 debited from your RWallet Account on Jul 22 2026 6:19AM. " +
            "Available Balance Rs.412.05. Indian Railway. " +
            "Please retain this message for your records and contact the issuer for any queries."

    @Test
    fun `the merchant repeated by the pipeline is hidden`() {
        assertTrue(isPipelineNote("Swiggy", "Swiggy", longBody))
    }

    @Test
    fun `the message truncated by the pipeline is hidden`() {
        val note = BankMessageParser.truncateMessage(longBody)
        assertTrue(isPipelineNote(note, null, longBody))
    }

    @Test
    fun `a short message note is hidden too, even without an ellipsis`() {
        val short = "Rs 40 debited at COFFEE DAY on 15/08/2026"
        assertTrue(isPipelineNote(BankMessageParser.truncateMessage(short), null, short))
    }

    @Test
    fun `multi-line messages compare after whitespace collapsing`() {
        val multiline = "Rs 40 debited\n at  COFFEE   DAY\n on 15/08/2026"
        val note = BankMessageParser.truncateMessage(multiline)
        assertTrue(isPipelineNote(note, null, multiline))
    }

    @Test
    fun `an edited note stays visible`() {
        assertFalse(isPipelineNote("Dinner with friends", "Swiggy", longBody))
    }

    @Test
    fun `a stale merchant note stays visible when the merchant changed`() {
        // The note says "Old Merchant" while the merchant is now something else:
        // either was edited, so show both rather than gamble on which.
        assertFalse(isPipelineNote("Old Merchant", "New Merchant", longBody))
    }

    @Test
    fun `without a message body only the merchant match hides`() {
        assertFalse(isPipelineNote("Some note", null, null))
        assertFalse(isPipelineNote("Some note", "Swiggy", null))
        assertTrue(isPipelineNote("Swiggy", "Swiggy", null))
    }

    @Test
    fun `a blank note is not treated as filler`() {
        assertFalse(isPipelineNote("", null, longBody))
    }
}
