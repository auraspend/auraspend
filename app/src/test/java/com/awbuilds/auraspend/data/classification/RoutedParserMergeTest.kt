package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

/**
 * Sender routing must **specialize** the global parse, not replace it.
 *
 * The per-bank parsers exist to correct fields the global engine reads wrongly
 * (an Axis limit line, a Canara fraud footer). They must not blank a field the
 * global engine already resolved. These cases were measured regressions: the
 * routed Axis parser looks for the merchant on the line after the timestamp, so
 * a one-line Axis SMS lost a merchant the global parser had; the routed Canara
 * parser wants `from <name> NEFT` and lost the `by Sender <name>` payee.
 */
class RoutedParserMergeTest {

    @Test
    fun `single-line Axis keeps the global merchant and timestamp`() {
        val parsed = TransactionClassifier.classify(
            "Spent INR 2745 Axis Bank Card no. XX4821 03-07-26 20:11:45 IST SUNRISE DINER",
            "VI-AXISBK-S"
        )
        assertEquals("Axis Bank", parsed.bankName)
        assertEquals(2745.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("SUNRISE DINER", parsed.merchant)
        assertEquals(LocalTime.of(20, 11, 45), parsed.date?.toLocalTime())
    }

    @Test
    fun `Canara NEFT keeps the global payee and date`() {
        val parsed = TransactionClassifier.classify(
            "An amount of INR 46,800.00 has been credited to XXXX6021 on 28/06/2026 " +
                "towards NEFT by Sender ACME SOLUTIONS LIMITED, IFSC HDFC0000240, " +
                "Sender A/c XXXX1134, HDFC BANK, MUMBAI",
            "CANARA"
        )
        assertEquals("Canara Bank", parsed.bankName)
        assertEquals(46800.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("ACME SOLUTIONS LIMITED", parsed.merchant)
        assertEquals("2026-06-28", parsed.date?.toLocalDate()?.toString())
    }
}
