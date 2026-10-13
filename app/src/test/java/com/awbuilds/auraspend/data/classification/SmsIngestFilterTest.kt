package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.ui.classification.SmsInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ingest-time noise filter.
 *
 * OTP / alert / promo messages were already skipped by the *processor*, but they were still
 * queued first: they inflated the progress denominator, occupied the SMS tab's message list, and
 * burned a pipeline slot each. Filtering them at ingest means the queue holds only candidates.
 *
 * The predicate is the processor's own `SmsAutoClassifier.isOtpOrAlertMessage`, negated — one
 * source of truth, including its amount+movement-verb rescue. These tests exist so the two can
 * never drift apart again.
 */
class SmsIngestFilterTest {

    private fun sms(body: String) = SmsInfo(id = "1", address = "BANK", body = body, timestamp = 0L)

    @Test
    fun `an OTP is not ingestable`() {
        assertFalse(isIngestable(sms("Your OTP is 482913")))
        assertFalse(isIngestable(sms("One time password 445122 to login")))
    }

    @Test
    fun `a balance alert is not ingestable`() {
        assertFalse(isIngestable(sms("Your available balance is Rs. 5000 as on 15/08/2026")))
    }

    @Test
    fun `a promo is not ingestable`() {
        assertFalse(isIngestable(sms("Flat 10% off on Swiggy orders this weekend. Use coupon FEST10.")))
    }

    @Test
    fun `a phishing lure is not ingestable`() {
        // Parses identically to a genuine debit, so the hard veto is the only thing separating
        // it from one — and there is deliberately no rescue for it.
        assertFalse(
            isIngestable(
                sms("Rs.10,000 has been debited? If not done by you, dial 1930 to report cyber fraud")
            )
        )
    }

    @Test
    fun `a debit that ends in an OTP is still ingestable`() {
        // The rescue the AGENTS.md OTP invariant demands: dropping this loses real money.
        assertTrue(
            isIngestable(
                sms("INR 149.00 debited at SWIGGY. Your OTP for this transaction is 445122.")
            )
        )
    }

    @Test
    fun `an ordinary debit is ingestable`() {
        assertTrue(
            isIngestable(
                sms("INR 250.00 debited from A/c **1234 at swiggy on 15/08/2026. Not you? Call 1800.")
            )
        )
    }

    @Test
    fun `a credit is ingestable`() {
        assertTrue(
            isIngestable(sms("INR 12500.00 credited to A/c **1234 by SALARY on 01/09/2026"))
        )
    }

    @Test
    fun `a genuine debit with the bank's alarm tail is still ingestable`() {
        // ICICI appends "If not you, call ... / SMS BLOCK ..." to every genuine
        // card alert. The hard veto deleted three real spends on the owner's
        // device (2026-10-13) — they appeared nowhere, not even in the
        // unrecognised list.
        assertTrue(
            isIngestable(
                sms("INR 994.09 spent using ICICI Bank Card XX8001 on 12-Sep-26 on SWIGGY. Avl Limit: INR 40,140.08. If not you, call 1800 2662/SMS BLOCK 5003 to 9000000042.")
            )
        )
    }

    @Test
    fun `the alarm-tail exception does not rescue phishing framing`() {
        // The same alarm shape, but the framing is the phishing one — still vetoed.
        assertFalse(
            isIngestable(
                sms("INR 4,999.00 spent using your card. If not done by you, call 1800 2662/SMS BLOCK 5003 to 9000000042.")
            )
        )
    }

    @Test
    fun `an unsuccessful payment attempt is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Your last payment attempt of Rs. 49.00 on Jul 20, 2026 03:50:47 PM for Jio number 9000000042 was unsuccessful. Please try again.")
            )
        )
    }

    @Test
    fun `a refund that is only initiated is not ingestable`() {
        assertFalse(
            isIngestable(sms("Your refund for PNR- 4123456789 of Rs. 560.00 has been initiated by Confirmtkt."))
        )
    }
}
