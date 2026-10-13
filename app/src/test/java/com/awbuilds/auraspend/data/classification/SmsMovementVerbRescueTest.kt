package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OTP/alert filter's amount **and** movement-verb rescue, pinned.
 *
 * The invariant (AGENTS.md): a message may be skipped as an OTP/alert only when
 * it has no amount *and* no movement verb. A real debit that ends in an OTP code
 * is a real transaction, and dropping it loses the user's money silently.
 *
 * The failure this guards against is already in the project's history: the
 * rescue used a six-word verb list (`debited|credited|spent|paid|transferred|
 * withdrawn`) that omitted the whole credit lexicon, so "Rs 10,000 has been
 * deposited … Available balance …" matched the balance-alert pattern and was
 * dropped *before* the parser that reads "deposited" ever ran. The rescue and
 * the recorder now share `BankMessageParser.hasMovementVerb`, so the two can
 * never disagree again — and these cases fail if that predicate is ever narrowed
 * back.
 */
class SmsMovementVerbRescueTest {

    // ─── The shared predicate ─────────────────────────────────────────────────

    @Test
    fun `hasMovementVerb covers both lexicons`() {
        assertTrue(BankMessageParser.hasMovementVerb("Rs 500 debited"))
        assertTrue(BankMessageParser.hasMovementVerb("Rs 10,000 has been deposited to A/c XX123"))
        assertTrue(BankMessageParser.hasMovementVerb("Refund of Rs 500 received"))
        assertTrue(BankMessageParser.hasMovementVerb("Cashback of Rs 50 credited"))
        assertTrue(BankMessageParser.hasMovementVerb("A reversal of Rs 1,200 is processed"))
    }

    @Test
    fun `hasMovementVerb rejects mere balance and limit wording`() {
        assertFalse(BankMessageParser.hasMovementVerb("Available balance is Rs 45,200"))
        assertFalse(BankMessageParser.hasMovementVerb("Avl Limit: INR 42731.09"))
        assertFalse(BankMessageParser.hasMovementVerb("Dear customer, your KYC is due"))
        assertFalse(BankMessageParser.hasMovementVerb(""))
    }

    // ─── The rescue (review finding #6) ───────────────────────────────────────

    @Test
    fun `a deposit with a balance tail is a transaction, not an alert`() {
        val message = "Rs 10,000 has been deposited to A/c XX123. " +
            "Available balance is Rs 45,200. Not you? Call 1800-000-0000."
        assertFalse(SmsAutoClassifier.isOtpOrAlertMessage(message))
    }

    @Test
    fun `a refund with a balance tail is a transaction, not an alert`() {
        val message = "Refund of Rs 500.00 received for your cancelled order. " +
            "Avl balance: Rs 4,200.10."
        assertFalse(SmsAutoClassifier.isOtpOrAlertMessage(message))
    }

    @Test
    fun `a credit with a balance tail is a transaction, not an alert`() {
        val message = "Rs 2,000 credited to your account XX4821. " +
            "Available balance is Rs 45,200."
        assertFalse(SmsAutoClassifier.isOtpOrAlertMessage(message))
    }

    @Test
    fun `a debit that ends in an OTP code is still a transaction`() {
        val message = "Spent INR 149 at SWIGGY. Your OTP for this transaction is 445122."
        assertFalse(SmsAutoClassifier.isOtpOrAlertMessage(message))
    }

    // ─── The filter still filters ─────────────────────────────────────────────

    @Test
    fun `an OTP with no amount and no movement is skipped`() {
        assertTrue(SmsAutoClassifier.isOtpOrAlertMessage("445122 is your OTP for login. Do not share."))
    }

    @Test
    fun `a pure balance alert is skipped`() {
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Available balance in your A/c XX123 is Rs 45,200 as on 05-07-26."
            )
        )
    }

    @Test
    fun `a conditional fraud framing is skipped even with an amount and a verb`() {
        // Deliberately no rescue: a phishing lure parses identically to a genuine
        // debit, so the only thing separating them is this veto.
        val message = "Rs.10,000 has been debited from A/c XX123. If not done by you, " +
            "report cyber fraud. Phishing alert."
        assertTrue(SmsAutoClassifier.isOtpOrAlertMessage(message))
    }

    @Test
    fun `an advance mandate registration is skipped`() {
        val message = "An e-mandate for Rs 2,999 has been registered with Netflix. " +
            "It will be debited on 01-11-26."
        assertTrue(SmsAutoClassifier.isOtpOrAlertMessage(message))
    }
}
