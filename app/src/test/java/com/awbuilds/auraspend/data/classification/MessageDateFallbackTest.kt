package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.ui.classification.SmsInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Which clock a transaction's date comes from.
 *
 * Three sources, in this order: a date the message states, the message's own
 * arrival time, and only then the save clock. The middle one is the honest
 * fallback — a message that names no date still arrived at a known moment — and
 * it was already implemented in `SmsAutoClassifier.toTransaction`.
 *
 * What broke it was upstream: `BankMessageParser.detectDateTime` treated the
 * **word** "today" anywhere in the body as a date and answered
 * `LocalDateTime.now()`. That made the fallback unreachable, and it dated
 * advertising copy — "the match Today!" — with the day the inbox was scanned.
 * Measured on the owner's device (2026-10-21): a message from 2021 was booked
 * on the day of a re-scan, years later.
 *
 * The same branch made parsing depend on the wall clock, so the same text could
 * parse to a different date on a different day.
 */
class MessageDateFallbackTest {

    private val receivedAt: Long =
        LocalDateTime.of(2021, 10, 24, 19, 22)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun classified(id: String, body: String) = ClassifiedSms(
        sms = SmsInfo(id = id, address = "VM-EXAMPLE", body = body, timestamp = receivedAt),
        parsed = TransactionClassifier.classify(body, "VM-EXAMPLE")
    )

    // ─── The parser must not invent a date from prose ─────────────────────────

    @Test
    fun `the word today is not a date`() {
        assertNull(BankMessageParser.detectDateTime("Your bill is due today. Pay now."))
        assertNull(BankMessageParser.detectDateTime("Offer ends today, recharge now."))
        assertNull(BankMessageParser.detectDateTime("Your statement for today is ready."))
    }

    @Test
    fun `a stated date is still read`() {
        assertEquals(
            LocalDateTime.of(2026, 3, 15, 12, 0),
            BankMessageParser.detectDateTime("INR 200 credited on 15/03/2026 towards refund")
        )
    }

    // ─── The fallback the pipeline actually relies on ────────────────────────

    @Test
    fun `an undated message is dated by its arrival, not by the scan`() {
        // The message that exposed this: advertising copy that happens to say
        // "Today", and names no date of its own.
        val body = "DONT MISS tonight's match! Watch it LIVE on ExamplePlay+ with your " +
            "ExampleTelco prepaid! Recharge Rs.501 = 1 year ExamplePlay+ mobile " +
            "subscription + 3GB/day + UL calls, 28 days. Click bit.ly/ExamplePlan"
        val parsed = classified("example-undated", body)
        assertNull("this body states no date", parsed.parsed.date)

        val transaction = SmsAutoClassifier.toTransaction(
            parsed,
            categoryId = "cat_other",
            receivedAt = receivedAt
        )
        assertEquals(LocalDateTime.of(2021, 10, 24, 19, 22), transaction.date)
    }

    @Test
    fun `a stated date still wins over the arrival time`() {
        // A date the message states, and a plausible relationship to its
        // arrival: the message arrived months later than the day it names.
        // (A stated date *after* the arrival is clamped — see
        // `AmountAndDateIntegrityTest`.)
        val transaction = SmsAutoClassifier.toTransaction(
            classified("example-dated", "Rs 749.50 debited at EXAMPLE STORE on 15/03/2021"),
            categoryId = "cat_other",
            receivedAt = receivedAt
        )
        assertEquals(LocalDateTime.of(2021, 3, 15, 12, 0), transaction.date)
    }

    @Test
    fun `a missing arrival time still falls back to the save clock`() {
        // The last resort, for a row whose queue entry never carried a
        // timestamp. It is deliberately still there; the bug was that prose
        // could reach it, not that it exists.
        val transaction = SmsAutoClassifier.toTransaction(
            classified("example-no-arrival", "Rs 100 debited at EXAMPLE STORE"),
            categoryId = "cat_other",
            receivedAt = 0L
        )
        assertEquals(LocalDateTime.now().toLocalDate(), transaction.date.toLocalDate())
    }
}
