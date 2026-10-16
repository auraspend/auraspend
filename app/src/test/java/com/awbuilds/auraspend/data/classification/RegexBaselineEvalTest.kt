package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.ai.ClassificationEval
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Establishes the accuracy floor: what the app does with the model switched off.
 *
 * The thresholds are anchored to a measurement, never to a guess, and the number
 * moved on **2026-10-16** for two reasons that are worth separating:
 *
 * - **Rails stopped being categories.** `upi`/`neft`/`imps`/`rtgs`/`transfer` no
 *   longer map to Transfer (a person-to-person counterparty does), and the four
 *   NEFT/UPI cases that expect "Other" now get it.
 * - **The category field was being scored wrong.** A missing category is not a
 *   different answer from `cat_other`: the pipeline saves Other when nothing
 *   matches, so the eval now compares the same thing the database stores. That
 *   alone moved thirteen rows from "miss" to "match" — it is a fidelity fix, not
 *   a classification win, and the jump in the headline number should be read that
 *   way.
 *
 * The corpus also grew 72 → 82 with the families that shipped as phantoms or
 * went uncategorised (reward points, fund NAV notices, card offers, SIP debits,
 * person-to-person transfers). Measured after both changes: **26/82 (31.7 %)
 * exact, type 95.1 %, category 53.7 %, zero false vetoes**.
 *
 * Exact match is a deliberately harsh metric here: two of the five scored fields
 * (amount, date) are not even in the model's schema, because the regex layer owns
 * them. The per-field breakdown is the readable signal; see
 * [ClassificationEval.Report.format].
 */
class RegexBaselineEvalTest {

    private val report = RegexBaselineEval.run()

    @Test
    fun `baseline is measured and reported`() {
        println("\n===== REGEX-ONLY BASELINE (no on-device AI) =====\n${report.format()}\n")
        assertTrue("corpus should not be empty", report.total > 0)
    }

    @Test
    fun `regex layer does not regress below its measured floor`() {
        assertTrue(
            "regex-only exact match regressed: ${report.exactMatchPercent}% (floor 30.0%)",
            report.exactMatchPercent >= 30.0
        )
        // The fields the regex layer is genuinely responsible for must hold up.
        assertTrue(
            "type accuracy regressed: ${report.fieldRate("type")}% (floor 92%)",
            report.fieldRate("type")!! >= 92.0
        )
    }

    @Test
    fun `a noise SMS is never reported as a transaction`() {
        val falsePositives = report.results.filter {
            !it.case.isTransaction && it.predicted?.isTransaction == true
        }
        assertTrue(
            "noise classified as a transaction: ${falsePositives.map { it.case.id }}",
            falsePositives.isEmpty()
        )
    }

    @Test
    fun `the OTP branch does not drop a real transaction that merely mentions a code`() {
        // Found by this harness: a genuine debit followed by "Your OTP for this
        // transaction is ..." was being discarded, because the OTP branch of
        // isOtpOrAlertMessage returned true unconditionally. The user silently
        // lost a real transaction.
        val dropped = report.results.filter { result ->
            result.case.isTransaction &&
                result.predicted == null &&
                RegexBaselineEval.classify(result.case.body).skip ==
                RegexBaselineEval.SkipReason.ALERT_FILTER &&
                !RegexBaselineEval.isFraudLineVeto(result.case.body)
        }
        if (dropped.isNotEmpty()) {
            println("Real transactions dropped by the OTP branch (${dropped.size}):")
            dropped.forEach { println("  - [${it.case.id}] ${it.case.body.take(70)}") }
        }
        assertTrue(
            "the OTP branch dropped ${dropped.size} real transactions: " +
                dropped.map { it.case.id },
            dropped.isEmpty()
        )
    }

    @Test
    fun `a genuine debit carrying a fraud line is a known accepted loss`() {
        // The deliberate trade-off, recorded so it is a decision rather than a
        // surprise. A real debit that ends in a fraud-alert footer parses
        // identically to a phishing lure, so the hard veto cannot tell them apart
        // and blocks both. Losing one real entry (visible in the SMS thread) is
        // much cheaper than inventing a phantom one the user never spent.
        val accepted = listOf("canara_debit_10000")
        accepted.forEach { id ->
            val result = report.results.firstOrNull { it.case.id == id }
                ?: error("corpus case $id disappeared")
            assertTrue(
                "$id should be blocked by the hard veto",
                RegexBaselineEval.classify(result.case.body).skip ==
                    RegexBaselineEval.SkipReason.ALERT_FILTER
            )
        }
    }

    @Test
    fun `a pure OTP is always skipped`() {
        // The counterpart guard: loosening the filter must not let bare OTPs
        // through and create phantom transactions.
        val otps = listOf(
            "Your OTP is 482913 do not share with anyone",
            "123456 is your OTP for login. Do not share.",
            "OTP 998877 valid for 5 minutes"
        )
        otps.forEach { body ->
            assertTrue(
                "pure OTP was not skipped: $body",
                RegexBaselineEval.classify(body).skip == RegexBaselineEval.SkipReason.ALERT_FILTER
            )
        }
    }

    @Test
    fun `a phishing lure carrying a debit verb is still skipped`() {
        // Deliberate safety trade-off, and the reason the hard-veto branch has no
        // rescue: a phishing SMS quoting a real amount and a debit verb is
        // indistinguishable from a genuine one by regex alone. It is kept blocked.
        val lure = "Dear Customer, Rs.10,000 has been debited? report cyber fraud- " +
            "Canara Bank if not done by you. Phishing alert."
        assertTrue(
            "phishing lure was not blocked",
            RegexBaselineEval.classify(lure).skip == RegexBaselineEval.SkipReason.ALERT_FILTER
        )
    }

    @Test
    fun `skip reasons are attributed to the right gate`() {
        // Guards the reporting itself: conflating "the alert filter dropped it"
        // with "the parser could not read a type" sends you hunting in the wrong file.
        val otp = RegexBaselineEval.classify("Your OTP is 482913")
        assertTrue(RegexBaselineEval.SkipReason.ALERT_FILTER == otp.skip)

        val noType = RegexBaselineEval.classify("INR 150 debited at SWIGGY and INR 200 credited")
        assertTrue(
            "a two-movement SMS should fail the type gate, not the alert filter",
            RegexBaselineEval.SkipReason.UNRESOLVED_FIELDS == noType.skip
        )

        val ok = RegexBaselineEval.classify("INR 250 debited A/c XX1234 at SWIGGY on 01-07")
        assertTrue(RegexBaselineEval.SkipReason.NONE == ok.skip)
        assertTrue(ok.extraction != null)
    }
}
