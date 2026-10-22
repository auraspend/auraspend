package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.ui.classification.SmsInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The two figures an SMS can carry, and the one clock a transaction may not use.
 *
 * Measured on a real export (2026-10-22), both families were silent and large:
 *
 * - One bank writes the **movement bare** and the **balance rupee-marked** —
 *   "4500 credited … BAL-Rs.6221.41". The balance was the only rupee figure in
 *   the body, so the picker chose it: ~30 rows booked the account balance as
 *   income, usually on top of the same deposit recorded correctly from another
 *   message.
 * - A statement summary listing numbered legs was booked as one figure — the
 *   closing balance — producing the largest phantoms in the file.
 *
 * A third, smaller one: a date read from a "valid until" clause put a row in a
 * month that had not happened yet.
 *
 * Every message here is synthetic, written to the same shape.
 */
class AmountAndDateIntegrityTest {

    private fun classify(body: String, receivedAt: Long = ARRIVAL) = ClassifiedSms(
        sms = SmsInfo(id = "synthetic", address = "VM-EXAMPLE", body = body, timestamp = receivedAt),
        parsed = TransactionClassifier.classify(body, "VM-EXAMPLE")
    )

    // ─── The balance is not the amount ───────────────────────────────────────

    @Test
    fun `a bare credited figure beats the balance that follows it`() {
        // The shape that produced ~30 phantom incomes.
        val parsed = classify(
            "Dear Customer, 4500 credited to your A/c XX9780 on 06OCT2026 11:04:40." +
                "BAL-Rs.6221.41.Use FedMobile App to view your A/c statement."
        ).parsed
        assertEquals(4500.0, parsed.amount!!, 0.001)
    }

    @Test
    fun `a rupee-marked credit is not displaced by the balance`() {
        val parsed = classify(
            "Dear Customer, Rs.12000 credited to your A/c XX9780 on 20OCT2026 20:48:27. " +
                "BAL-Rs.22930.45-Federal Bank"
        ).parsed
        assertEquals(12000.0, parsed.amount!!, 0.001)
    }

    @Test
    fun `a debit keeps its amount when a balance follows`() {
        val parsed = classify(
            "Debited Rs 960.50 from a/c XX9780 on 20OCT2026 08:16:27.Bal Rs 41.45." +
                "Not you?Call 1800000000 -Example Bank"
        ).parsed
        assertEquals(960.50, parsed.amount!!, 0.001)
    }

    @Test
    fun `the abbreviated balance form is masked too`() {
        val parsed = classify(
            "Rs 5000.00 debited from A/c XX1234 on 12-10-2026 at EXAMPLE STORE. " +
                "Aval Bal Rs.27000.00 CR."
        ).parsed
        assertEquals(5000.0, parsed.amount!!, 0.001)
    }

    @Test
    fun `a balance-only notice has no amount at all`() {
        val parsed = classify(
            "Dear Customer, your available balance is Rs.5,000.00 as on 12-10-2026."
        ).parsed
        assertNull(parsed.amount)
    }

    // ─── A statement summary is not a transaction ────────────────────────────

    @Test
    fun `a numbered statement summary is not a transaction`() {
        // The shape that closed with the balance and booked it as income.
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Tran for A/C XX0116 1) 20XXXX27 2200.00 Cr 2) 20XXXX27 1260.00 Cr " +
                    "3) 20XXXX27 70.00 Cr 4) 20XXXX27 250.00 Cr 5) 20XXXX27 120.00 Cr"
            )
        )
    }

    @Test
    fun `a single transfer is not mistaken for a summary`() {
        assertEquals(
            false,
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Rs.5000.00 debited from A/c XX1234 on 12-10-2026 to EXAMPLE PAYEE."
            )
        )
    }

    // ─── A validity date is not the transaction date ─────────────────────────

    @Test
    fun `a date in the future falls back to the arrival time`() {
        val classified = classify(
            "Your certificate validity is 24/07/2027 and the payment received for the " +
                "certificate is Rs. 100 (excluding GST)."
        )
        // The parser reads the only date it can see — in the future.
        assertEquals(LocalDate.of(2027, 7, 24), classified.parsed.date?.toLocalDate())

        val transaction = SmsAutoClassifier.toTransaction(
            classified,
            categoryId = "cat_other",
            receivedAt = ARRIVAL
        )
        assertEquals(LocalDate.of(2026, 10, 22), transaction.date.toLocalDate())
    }

    @Test
    fun `a date on the day of arrival is kept`() {
        val classified = classify("Rs 100 debited at EXAMPLE STORE on 22/10/2026.")
        val transaction = SmsAutoClassifier.toTransaction(
            classified,
            categoryId = "cat_other",
            receivedAt = ARRIVAL
        )
        assertEquals(LocalDate.of(2026, 10, 22), transaction.date.toLocalDate())
    }

    private companion object {
        /** 22 Oct 2026, 09:15 local — the arrival of every fixture here. */
        val ARRIVAL: Long = LocalDateTime.of(2026, 10, 22, 9, 15)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
