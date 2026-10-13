package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.TransactionClassifier
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wave-2 routing: PNB, Bank of Baroda, IDFC First and IndusInd get their
 * labels and direction fixes; the remaining national banks get sender→bank
 * attribution over the global engine.
 */
class WaveTwoBankParsersTest {

    // ── routing ────────────────────────────────────────────────────────────────

    @Test
    fun `wave-2 sender ids route to their bank`() {
        assertEquals("PNB", BankParserRegistry.parserFor("VM-PNBSMS-S")?.bankName)
        assertEquals("PNB", BankParserRegistry.parserFor("VA-PNBCCD-S")?.bankName)
        assertEquals("Bank of Baroda", BankParserRegistry.parserFor("VM-BOBTXN-S")?.bankName)
        assertEquals("Bank of Baroda", BankParserRegistry.parserFor("VM-BOBCRD-S")?.bankName)
        assertEquals("IDFC First", BankParserRegistry.parserFor("BM-IDFCBK-S")?.bankName)
        assertEquals("IndusInd Bank", BankParserRegistry.parserFor("AD-INDUSB-S")?.bankName)
        assertEquals("IndusInd Bank", BankParserRegistry.parserFor("VM-INDUSIND-S")?.bankName)
        assertEquals("Federal Bank", BankParserRegistry.parserFor("VM-FEDBNK-S")?.bankName)
        assertEquals("Yes Bank", BankParserRegistry.parserFor("VM-YESBNK-S")?.bankName)
        assertEquals("AU Small Finance Bank", BankParserRegistry.parserFor("AD-AUBANK-S")?.bankName)
        assertEquals("Bandhan Bank", BankParserRegistry.parserFor("AD-BDNSMS-S")?.bankName)
        assertEquals("Union Bank", BankParserRegistry.parserFor("VM-UNIONB-S")?.bankName)
        assertEquals("Central Bank of India", BankParserRegistry.parserFor("AD-CENTBK-S")?.bankName)
        assertEquals("South Indian Bank", BankParserRegistry.parserFor("AD-SIBSMS-S")?.bankName)
        assertEquals("Bank of India", BankParserRegistry.parserFor("AD-BOIIND-S")?.bankName)
    }

    @Test
    fun `near-miss senders are not claimed by PNB`() {
        assertNull(BankParserRegistry.parserFor("VA-PNBCCDX-S"))
        assertNull(BankParserRegistry.parserFor("VA-XPNBCCD-S"))
    }

    // ── PNB ────────────────────────────────────────────────────────────────────

    @Test
    fun `pnb debit reads the amount and not the Aval Bal tail`() {
        val parsed = TransactionClassifier.classify(
            "Ac XX1234 Debited with Rs.5000.00, 20-02-2026 07:47:16. Aval Bal Rs.27000.00 CR. Helpline 18001800/18002021-PNB",
            "VM-PNBSMS-S"
        )
        assertEquals(5000.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("PNB", parsed.bankName)
    }

    @Test
    fun `pnb card-ride labels the card`() {
        val parsed = TransactionClassifier.classify(
            "A/c XX1234 debited with Rs.5000.00,21-11-2025 13:23:22 thru card XX9239  . Out of 5 free txn on PNB ATM, you utilized 1 txn. Bal 27000.00 CR.",
            "VM-PNBSMS-S"
        )
        assertEquals("Card XX9239", parsed.merchant)
    }

    @Test
    fun `pnb upi credit without a counterparty reads as a UPI move`() {
        val parsed = TransactionClassifier.classify(
            "Ac XX7582 Credited with Rs.4750.00 11-07-2025 08:53:21 thru UPI . Aval Bal Rs.82431.67 CR. (UPI Ref ID:714628305917) Helpline 18001800/18002021-PNB",
            "VM-PNBSMS-S"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("UPI Transaction", parsed.merchant)
    }

    @Test
    fun `a machine-generated upi handle stays raw`() {
        val parsed = TransactionClassifier.classify(
            "PNB Credit Card 1234 debited with Rs.270 [CODE:VG9918] at ombk.aaeh000000abcd@mbk on 04-10-2026 20:44 through UPI: 627768901810 Avl limit Rs. 48882.5. -PNB",
            "VA-PNBCCD-S"
        )
        assertEquals(270.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals("ombk.aaeh000000abcd", parsed.merchant)
    }

    @Test
    fun `pnb payee before thru is the merchant`() {
        val parsed = TransactionClassifier.classify(
            "A/c X0000 debited INR 125.00 Dt 01-01-26 12:00:00 to Swiggy thru UPI:000000000001..Bal INR 9000.00-PNB",
            "VM-PNBSMS-S"
        )
        assertEquals("Swiggy", parsed.merchant)
        assertEquals(125.0, parsed.amount ?: Double.NaN, 0.001)
    }

    // ── Bank of Baroda ─────────────────────────────────────────────────────────

    @Test
    fun `bob transfer via the colon form is an expense with a payee`() {
        val parsed = TransactionClassifier.classify(
            "Rs.1500.00 transferred from A/c ...1234 to:John Smith. Total Bal:Rs.15000.00CR. Avlbl Amt:Rs.15000.00(10-11-2025 10:30:00) - Bank of Baroda",
            "VM-BOBTXN-S"
        )
        assertEquals(1500.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("John Smith", parsed.merchant)
        assertEquals("Bank of Baroda", parsed.bankName)
    }

    @Test
    fun `bob imps credit names the counterparty after by`() {
        val parsed = TransactionClassifier.classify(
            "Rs.2500.00 credited to A/c XX456789 via IMPS/518233445566 by JOHN DOE. AvlBal:Rs25000.00 -Bank of Baroda",
            "VM-BOBTXN-S"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("JOHN DOE", parsed.merchant)
        assertEquals(2500.0, parsed.amount ?: Double.NaN, 0.001)
    }

    // ── IDFC First ─────────────────────────────────────────────────────────────

    @Test
    fun `idfc interest credit is labelled`() {
        val parsed = TransactionClassifier.classify(
            "Your A/C XXXXXXX1234 is credited by INR 125.50 on 01/01/25 for monthly interest. New Bal :INR 15125.50",
            "BM-IDFCBK-S"
        )
        assertEquals("Interest Credit", parsed.merchant)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("IDFC First", parsed.bankName)
    }

    @Test
    fun `idfc counterparty credit names the payee`() {
        val parsed = TransactionClassifier.classify(
            "Your A/c XX4614 debited by Rs. 1,172.06 on 15/01/26; REDBUS credited. RRN 060649915527. Available balance Rs. 9,134.15. Team IDFC FIRST Bank",
            "JM-IDFCFB-S"
        )
        assertEquals("REDBUS", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(1172.06, parsed.amount ?: Double.NaN, 0.001)
    }

    // ── IndusInd ───────────────────────────────────────────────────────────────

    @Test
    fun `indusind debit names the merchant at`() {
        val parsed = TransactionClassifier.classify(
            "Rs. 1,234.00 debited from A/c XX1234 at ZOMATO Ref 998877. Avl Bal: Rs 10,000.00",
            "VM-INDUSB-S"
        )
        assertEquals("ZOMATO", parsed.merchant)
        assertEquals(1234.0, parsed.amount ?: Double.NaN, 0.001)
    }

    @Test
    fun `indusind imps reference reads as the label`() {
        val parsed = TransactionClassifier.classify(
            "Your IndusInd Account 20XXXXX1234 has been debited for INR 6440 towards IMPS/12345678901. Call 18602677777 to report issue-IndusInd Bank.",
            "AD-INDUSIND-S"
        )
        assertEquals("IMPS Transfer", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }
}
