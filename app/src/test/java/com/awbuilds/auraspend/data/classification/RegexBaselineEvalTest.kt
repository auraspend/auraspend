package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.ai.ClassificationEval
import org.junit.Assert.assertEquals
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
 * person-to-person transfers).
 *
 * **Re-measured 2026-10-17 after the classification audit.** Exact match now
 * covers **seven** fields, including amount and date; a correct rejection is a
 * scored pass, not a null miss; merchants compare case-insensitively; and the
 * production category resolver runs. Result: **80/82 (97.6 %) exact, type 98.8 %,
 * category 100 %, merchant 98.8 %, amount 98.8 %, date 100 %, one accepted false
 * veto** (`canara_debit_10000`, the documented phishing trade-off). The two
 * non-exact cases are that trade-off and a flight-booking merchant that is a
 * destination, not a payee.
 *
 * The floors below are deliberately close to the measurement. A wrong amount is
 * the most expensive parse error in the app, so it cannot sit outside the score.
 * See [ClassificationEval.Report.format].
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
            "regex-only exact match regressed: ${report.exactMatchPercent}% (floor 97.0%)",
            report.exactMatchPercent >= 97.0
        )
        // The fields the regex layer is genuinely responsible for must hold up.
        assertTrue(
            "type accuracy regressed: ${report.fieldRate("type")}% (floor 98%)",
            report.fieldRate("type")!! >= 98.0
        )
        assertTrue(
            "category accuracy regressed: ${report.fieldRate("category")}% (floor 99%)",
            report.fieldRate("category")!! >= 99.0
        )
        assertTrue(
            "merchant accuracy regressed: ${report.fieldRate("merchant")}% (floor 98%)",
            report.fieldRate("merchant")!! >= 98.0
        )
        assertTrue(
            "amount accuracy regressed: ${report.fieldRate("amount")}% (floor 98%)",
            report.fieldRate("amount")!! >= 98.0
        )
        assertTrue(
            "date accuracy regressed: ${report.fieldRate("date")}% (floor 99%)",
            report.fieldRate("date")!! >= 99.0
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
    fun `a correct rejection is scored as a pass, not a null miss`() {
        val negatives = report.results.filter { !it.case.isTransaction }
        val wrong = negatives.filter { !it.exactMatch }
        assertTrue(
            "correctly rejected non-transactions were scored as misses: " +
                wrong.map { it.case.id },
            wrong.isEmpty()
        )
    }

    @Test
    fun `the alert filter drops no real transaction except the documented phishing trade-off`() {
        val dropped = report.results.filter {
            it.case.isTransaction &&
                it.rejection == ClassificationEval.Rejection.ALERT_FILTER
        }
        // The Canara phishing lure is deliberately indistinguishable from a
        // genuine debit carrying the same "if not done by you" tail. Every other
        // real transaction must survive the filter — this is the counter the old
        // `falseVetoes()` could not see because a skip returns null.
        assertEquals(
            "real transactions dropped by the alert filter",
            setOf("canara_debit_10000"),
            dropped.map { it.case.id }.toSet()
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
                result.rejection == ClassificationEval.Rejection.ALERT_FILTER
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

        // Two amounts with no movement verb: the parser cannot choose a
        // direction, so this is the unresolved-fields gate, not the alert filter.
        // (A two-movement SMS with debited/credited now books the first movement;
        // see TransferAndInvestmentCategoryTest.)
        val noType = RegexBaselineEval.classify("INR 150 at SWIGGY and INR 200 at ZOMATO")
        assertTrue(
            "an amount-only SMS should fail the type gate, not the alert filter",
            RegexBaselineEval.SkipReason.UNRESOLVED_FIELDS == noType.skip
        )

        val ok = RegexBaselineEval.classify("INR 250 debited A/c XX1234 at SWIGGY on 01-07")
        assertTrue(RegexBaselineEval.SkipReason.NONE == ok.skip)
        assertTrue(ok.extraction != null)
    }
}
