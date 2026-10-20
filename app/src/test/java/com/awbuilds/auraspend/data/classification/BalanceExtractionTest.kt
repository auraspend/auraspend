package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The balance an SMS states, and the instrument it belongs to.
 *
 * A message carries exactly one number that is a **balance** rather than a
 * movement — a bank's "Avl Bal" or a card's "Avl Limit" — and the two are not
 * the same thing. Fixtures are synthetic.
 */
class BalanceExtractionTest {

    @Test
    fun `a bank debit carries the account's balance`() {
        val parsed = BankMessageParser.parse(
            "Dear Customer, Acct XXXX0007 Dr. INR 1,299.00 on 03/08/26 to Some Store; " +
                "UPI: 000000000000; Bal INR 44,821.73."
        )
        assertEquals(1299.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(44821.73, parsed.balanceAfter ?: 0.0, 0.01)
        assertEquals("0007", parsed.instrumentLast4)
        assertFalse(parsed.instrumentIsCard)
    }

    @Test
    fun `a card spend carries the remaining credit, not a bank balance`() {
        val parsed = BankMessageParser.parse(
            "Spent INR 20\nAxis Bank Card no. XX9054\n04-03-26 21:20:58 IST\nSOME SHOP\n" +
                "Avl Limit: INR 57289.61\nNot you? SMS BLOCK 9054 to 000000000000"
        )
        assertEquals(20.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(57289.61, parsed.balanceAfter ?: 0.0, 0.01)
        assertEquals("9054", parsed.instrumentLast4)
        assertTrue(parsed.instrumentIsCard)
    }

    @Test
    fun `the first instrument reference wins, so a linked card is not the account`() {
        // "A/C XXXX0007 linked to card XXXX1751 debited …": the movement is on
        // the account, so the balance is the account's and the card mentioned
        // in passing must not turn this into a card row.
        val parsed = BankMessageParser.parse(
            "A/C XXXX0007 linked to card XXXX1751 debited Rs INR 1,497.00 on 03/05/2026 " +
                "POS txn.Avl Bal is Rs INR 57,270.00."
        )
        assertEquals("0007", parsed.instrumentLast4)
        assertFalse(parsed.instrumentIsCard)
        assertEquals(57270.00, parsed.balanceAfter ?: 0.0, 0.01)
    }
}
