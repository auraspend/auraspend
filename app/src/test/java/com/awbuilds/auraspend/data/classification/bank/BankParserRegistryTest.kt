package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.ai.ClassificationEval
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BankParserRegistryTest {

    // ── sender resolution ──────────────────────────────────────────────────────

    @Test
    fun `resolves the DLT sender id to the right bank`() {
        assertEquals("Axis Bank", BankParserRegistry.parserFor("AXISBK")?.bankName)
        assertEquals("Axis Bank", BankParserRegistry.parserFor("AXISBANK")?.bankName)
        assertEquals("Axis Bank", BankParserRegistry.parserFor("VI-AXISBK-S")?.bankName)
        assertEquals("Canara Bank", BankParserRegistry.parserFor("CANARA")?.bankName)
        assertEquals("State Bank of India", BankParserRegistry.parserFor("SBIN")?.bankName)
    }

    @Test
    fun `an unknown or missing sender resolves to no parser`() {
        // A real state, not an edge case: most users bank somewhere we have no
        // parser for. Nothing may be guessed here.
        assertNull(BankParserRegistry.parserFor("UNKNOWN-BANK"))
        assertNull(BankParserRegistry.parserFor(""))
        assertNull(BankParserRegistry.parserFor("   "))
    }

    // ── per-bank extraction ────────────────────────────────────────────────────

    @Test
    fun `axis parser takes the spend amount and not the available-limit decoy`() {
        val body = """
            Spent INR 135
            Axis Bank Card
            no. XX4821
            05-07-26 10:15:22 IST
            Tulip
            Avl Limit: INR 42731.09
        """.trimIndent()
        val p = AxisBankParser().extract(body)
        assertEquals(135.0, p.amount!!, 0.001)
        assertEquals("Tulip", p.merchant)
        assertEquals("Axis Bank", p.bankName)
    }

    @Test
    fun `canara parser keeps a genuine debit that carries a cyber-fraud footer`() {
        // "Dial 1930 to report cyber fraud" decorates every real Canara debit.
        // A parser that pattern-matches on the word "fraud" would drop real money.
        val body = "An amount of INR 135.00 has been DEBITED to your account XXXX6021 " +
            "on 12-08-2026. UPI Ref No 123456789012. Dial 1930 to report cyber fraud."
        val p = CanaraBankParser().extract(body)
        assertEquals(135.0, p.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, p.type)
    }

    @Test
    fun `canara parser reads a credit as income`() {
        val body = "An amount of INR 46800.00 has been credited to XXXX6021 on 04-06-2026 " +
            "from ACME SOLUTIONS LTD NEFT."
        val p = CanaraBankParser().extract(body)
        assertEquals(46800.0, p.amount!!, 0.001)
        assertEquals(TransactionType.INCOME, p.type)
    }

    // ── the measurement that justifies the whole module ────────────────────────

    @Test
    fun `per-bank parsers beat the global regex on their own banks' cases`() {
        // A parser that does not beat the global regex on that bank's own
        // messages is not worth existing, so this is a hard gate rather than a
        // nice-to-have. The global regex scores type 95.4% and category 36.9%
        // over all 65 cases (RegexBaselineEvalTest).
        val routed = ClassificationEval.loadCorpus().filter { it.sender != null }
        assertTrue(
            "the corpus must exercise sender routing, otherwise this test proves nothing",
            routed.isNotEmpty()
        )

        var typeCorrect = 0
        var amountCorrect = 0
        val checked = routed.filter { it.isTransaction && it.type != null }
        checked.forEach { case ->
            val parser = BankParserRegistry.parserFor(case.sender!!) ?: return@forEach
            val p = parser.extract(case.body)
            if (p.type?.name == case.type!!.name) typeCorrect++
            if (p.amount != null && case.amountForTest() != null &&
                kotlin.math.abs(p.amount - case.amountForTest()!!) < 0.01
            ) amountCorrect++
        }

        assertTrue(
            "per-bank parsers must read every type their own bank sends " +
                "(got $typeCorrect/${checked.size})",
            typeCorrect == checked.size
        )
        assertTrue(
            "per-bank parsers must read every amount their own bank sends " +
                "(got $amountCorrect/${checked.size})",
            amountCorrect == checked.size
        )
    }

    private fun assertTrue(message: String, condition: Boolean) =
        org.junit.Assert.assertTrue(message, condition)

    /** The corpus amount is ground truth; the body fallback is for legacy cases. */
    private fun ClassificationEval.Case.amountForTest(): Double? =
        amount ?: Regex("""(?:INR|Rs\.?)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
}
