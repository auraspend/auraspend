package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two families that looked like money and were not, both measured on a real
 * export (2026-10-22).
 *
 * **The counterparty's receipt.** A utility, an insurer, a lender or a courier
 * writes "your payment has been received" / "we are happy to inform you" /
 * "payment update". Every one carries a credit word, so it was booked as
 * income — while the bank had already recorded the same payment as an expense.
 * The money was counted twice, in both directions.
 *
 * **Both legs of an own-account move.** One SMS can describe a debit from one
 * of the user's accounts and the credit into another ("… debited for Rs X and
 * ‹account› credited"). Booked as an ordinary expense, it inflated spending
 * with the user's own money; it belongs in Transfer, which the totals exclude.
 *
 * The counter-cases matter as much as the matches: a real bank credit, and a
 * payment confirmation that is the user's only record of a real expense.
 */
class TransferAndReceiptFamiliesTest {

    // ─── Counterparty receipts ───────────────────────────────────────────────

    @Test
    fun `a utility's reminder to keep enjoying is not income`() {
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Payment of Rs. 470.82 for your ExampleFiber connection has been received. " +
                    "Continue enjoying ExampleFiber services."
            )
        )
    }

    @Test
    fun `a service acknowledging a credit on its own account is not income`() {
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "We are happy to inform you that your ExampleFiber account with Service ID " +
                    "12XXXX34 has been credited with Rs. 50.00."
            )
        )
    }

    @Test
    fun `a lender's payment update is not income`() {
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Payment Update! We have received an Overdue amt of Rs. 502.00 for " +
                    "LAN 123456789 on 05-Apr-2026 Example Finance Ltd"
            )
        )
    }

    @Test
    fun `an order receipt is not income`() {
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Dear Customer, payment of 960.50 has been successfully received against " +
                    "your order 2-000123456789."
            )
        )
    }

    @Test
    fun `a real bank credit is still income`() {
        assertFalse(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Rs.500.00 credited to your A/c XX9780 on 22OCT2026 09:15:00. " +
                    "BAL-Rs.6200.00-Example Bank"
            )
        )
    }

    @Test
    fun `a receipt that is the user's only record of a payment stays an expense`() {
        // "Your payment … has been received. Receipt No…" is how a charity or a
        // small merchant confirms a payment the user actually made. Vetoing the
        // shape wholesale would delete those expenses.
        assertFalse(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Dear Customer, your payment of Rs. 50.00 for Example Trust has been " +
                    "received. Receipt No: ABC0100041-179035137."
            )
        )
    }

    // ─── Both legs of an own-account move ────────────────────────────────────

    @Test
    fun `a message naming both identified accounts is a transfer`() {
        val message = "A/c. XX9780 debited for Rs.3450.00 on 04-07-2026 and XX179 credited " +
            "(IMPS Ref no. 61XXXX06). Not you? Call 1800000000"
        assertEquals(
            "cat_transfer",
            BankMessageParser.categoryIdFor(message, null, TransactionType.EXPENSE)
        )
    }

    @Test
    fun `the longer account wording is a transfer too`() {
        val message = "Your a/c no. XXXXXXXXXX9780 is debited for Rs.5000.00 on 14-10-2025 " +
            "and a/c XXXXXXXXXX192 credited (IMPS Ref no. 52XXXX12)."
        assertEquals(
            "cat_transfer",
            BankMessageParser.categoryIdFor(message, null, TransactionType.EXPENSE)
        )
    }

    @Test
    fun `a debit plus a refund is not a self-transfer`() {
        // Two different movements, the second stated as an amount — not an
        // account. Filing it as Transfer would hide both from the totals.
        val message = "Rs.500.00 debited at EXAMPLE STORE and Rs.200.00 credited as refund."
        val category = BankMessageParser.categoryIdFor(message, null, TransactionType.EXPENSE)
        assertEquals(false, category == "cat_transfer")
    }
}
