package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payee extraction, checked against a second implementation.
 *
 * The shapes here came from comparing this app's parse of a real message
 * corpus against another SMS parser's, message by message. Two findings became
 * rules:
 *
 * 1. **A semicolon ends a payee name.** "… Dr. INR 1,299.00 on 03/08/26 to
 *    SOMEONE; UPI: 000000000000" never matched, because the "to X" pattern
 *    terminated on a word or on `[/.,]` and not on `;`. The name was there; the
 *    pattern just could not see where it stopped.
 * 2. **A counterparty clause is not a merchant.** "towards reversal/cashback
 *    from THE MERCHANT" extracted "reversal", and "to register your UPI PIN"
 *    extracted "register" — both hid the name the message actually carried.
 *
 * The fixtures are synthetic: the shapes are real, the names and numbers are
 * not.
 */
class PayeeExtractionTest {

    private fun merchant(message: String): String? = BankMessageParser.detectMerchant(message)

    @Test
    fun `a semicolon ends the payee name`() {
        assertEquals(
            "Some Payee",
            merchant(
                "Dear Customer, Acct XXXXXXXX Dr. INR 1,299.00 on 03/08/26 to Some Payee; " +
                    "UPI: 000000000000; Bal INR 44,821.73."
            )
        )
    }

    @Test
    fun `a semicolon ends the payee name in the generic debit shape`() {
        assertEquals(
            "Some Store",
            merchant(
                "An amount of INR 7,250.00 has been DEBITED to your account XXXX0007 on " +
                    "10/02/2026 to Some Store; Total Avail.bal INR 21,691.95."
            )
        )
    }

    @Test
    fun `a cashback clause is not a merchant`() {
        val found = merchant(
            "Rs. 2 has been credited to your card xxxx1234, towards reversal/cashback from " +
                "Some Company for trxn. dated 10/04/2026"
        )
        assertFalse("a generic clause became the merchant: $found", found?.contains("reversal") == true)
        assertTrue("the counterparty was lost: $found", found?.contains("Some Company") == true)
    }

    @Test
    fun `an onboarding instruction is not a merchant`() {
        val found = merchant(
            "Dear Customer, Payment of Rs. 706.82 for your Home connection through UPI " +
                "Payments has been received on 07-Oct-26. Visit the app to register your UPI PIN."
        )
        assertFalse("an instruction became the merchant: $found", found?.contains("register") == true)
    }

    @Test
    fun `a written date is not a merchant`() {
        val found = merchant(
            "Dear Investor, the Base Expense Ratio of Some Gold ETF FOF will be revised from " +
                "October 22, 2026 onwards."
        )
        assertFalse("a date became the merchant: $found", found?.contains("October") == true)
    }

    @Test
    fun `an account clause is still not a merchant`() {
        assertNull(
            merchant(
                "An amount of INR 7,250.00 has been DEBITED to your account XXXX0007 on " +
                    "10/02/2026. Total Avail.bal INR 21,691.95."
            )
        )
    }
}
