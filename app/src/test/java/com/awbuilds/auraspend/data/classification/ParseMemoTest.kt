package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The memo behind the triage list's re-derivations.
 *
 * The screen re-derives the whole list on every queue write, and this is what
 * keeps that from re-running hundreds of regex parses each time (the Smart Add
 * jank). These tests pin both sides of the contract: hits are exact, and a
 * reused id with a different body re-classifies instead of serving a stale
 * parse. Bodies are synthetic.
 */
class ParseMemoTest {

    private val body = "INR 315.00 debited from your RWallet Account on Jul 22 2026 6:19AM"

    @Test
    fun `a repeated id is classified once`() {
        val memo = ParseMemo()

        val first = memo.parse("sms-1", body, "VM-IRSMSa-G")
        val second = memo.parse("sms-1", body, "VM-IRSMSa-G")

        assertSame("a hit must return the cached instance, not a re-parse", first, second)
    }

    @Test
    fun `a reused id with a different body re-classifies`() {
        // The production invariant is that a row's body never changes, but the
        // memo must not *rely* on that to be correct.
        val memo = ParseMemo()

        val first = memo.parse("sms-1", body, "VM-IRSMSa-G")
        val other = memo.parse("sms-1", "Happy birthday! See you soon.", "VM-IRSMSa-G")

        assertNotSame(first, other)
        assertNull(other.amount)
    }

    @Test
    fun `a reused id with a different sender re-classifies`() {
        // Sender routing changes the parse (per-bank parsers are chosen by
        // sender), so the sender is part of the key.
        val memo = ParseMemo()

        val first = memo.parse("sms-1", body, "VM-IRSMSa-G")
        val other = memo.parse("sms-1", body, "SOMEBANK")

        assertNotSame(first, other)
    }

    @Test
    fun `eviction past the bound re-derives`() {
        val memo = ParseMemo(maxEntries = 2)

        val first = memo.parse("a", body, null)
        memo.parse("b", body, null)
        memo.parse("c", body, null)

        assertNotSame("the eldest entry must have been evicted", first, memo.parse("a", body, null))
    }

    @Test
    fun `a hit carries the classifier's values`() {
        val memo = ParseMemo()

        val parsed = memo.parse("sms-9", body, "VM-IRSMSa-G")

        assertEquals(315.0, parsed.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }
}
