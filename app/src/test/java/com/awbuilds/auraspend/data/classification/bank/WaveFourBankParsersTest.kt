package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.TransactionClassifier
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave-4 corrections: the wave-3 cohort's fixtures that the global engine
 * could not read — labels, payee clauses and one lexicon gap. Formats are
 * synthetic twins carrying the same structure.
 */
class WaveFourBankParsersTest {

    @Test
    fun `an indian bank atm withdrawal names the channel and location`() {
        val parsed = TransactionClassifier.classify(
            "Rs. 2000 withdrawn from ATM at MAIN STREET BRANCH on 09/10/2025.Indian Bank",
            "INDBNK"
        )
        assertEquals(2000.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("ATM - MAIN STREET BRANCH", parsed.merchant)
        assertEquals("Indian Bank", parsed.bankName)
    }

    @Test
    fun `a saraswat ach credit reads the colon payee`() {
        val parsed = TransactionClassifier.classify(
            "Your A/c no. 1234 is credited with INR 100.50 on 13-10-2025 towards ACH Credit:MERCHANT NAME. Current Bal is INR 950.00 CR  - Saraswat Bank",
            "JD-SARBNK-S"
        )
        assertEquals("MERCHANT NAME", parsed.merchant)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(100.5, parsed.amount ?: Double.NaN, 0.001)
    }

    @Test
    fun `a saraswat standing instruction reads as a label`() {
        val parsed = TransactionClassifier.classify(
            "Dear Customer, Your account no. ending with 5678 is debited with INR 1,000.00 on 25-09-2025  for S.I. Current Bal is INR 8,500.00CR. - Saraswat Bank",
            "JD-SARBNK-S"
        )
        assertEquals("Standing Instruction", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `a saraswat neft debit reads the rail label`() {
        val parsed = TransactionClassifier.classify(
            "Dear Customer, Your account no. ending with 3456 is debited with INR 25,000.00 on 15-10-2025 for NEFT. Current Bal is INR 50,000.00CR. - Saraswat Bank",
            "AD-SARBNK-S"
        )
        assertEquals("NEFT Transfer", parsed.merchant)
    }

    @Test
    fun `a saraswat imps debit reads the rail label`() {
        val parsed = TransactionClassifier.classify(
            "Dear Customer, Your account no. ending with 1357 is debited with INR 2,500.00 on 23-10-2025 for IMPS. Current Bal is INR 12,345.67CR. - Saraswat Bank",
            "BV-SARBNK-S"
        )
        assertEquals("IMPS Transfer", parsed.merchant)
    }

    @Test
    fun `an hsbc card used at a merchant is an expense`() {
        // "used at" — the verb the debit lexicon was missing for this shape.
        val parsed = TransactionClassifier.classify(
            "Your HSBC creditcard xxxxx1234 used at AMAZON for INR 305.00 on 15-04-25.",
            "HSBC"
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("AMAZON", parsed.merchant)
        assertEquals(305.0, parsed.amount ?: Double.NaN, 0.001)
    }

    @Test
    fun `an hsbc debit card purchase names the merchant`() {
        val parsed = TransactionClassifier.classify(
            "Thank you for using HSBC Debit Card XXXXX71xx at IKEA INDIA . for INR 49.00 on 12-04-25.",
            "HSBC"
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("IKEA INDIA", parsed.merchant)
    }

    @Test
    fun `an amazon pay wallet debit gets the direction and label`() {
        val parsed = TransactionClassifier.classify(
            "Your Apay Wallet balance is debited for INR 250.00 Transaction Reference Number is 123456789012 - Powered by Juspay",
            "JUSPAY"
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Amazon Pay Transaction", parsed.merchant)
        assertEquals("Amazon Pay", parsed.bankName)
    }

    @Test
    fun `an amazon pay payment at a merchant is an expense`() {
        val parsed = TransactionClassifier.classify(
            "Payment of Rs 1,250.00 using Apay Balance successful at Amazon. Updated Balance is Rs 2,500.00 - SMS by Juspay",
            "JUSPAY"
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Amazon", parsed.merchant)
        assertEquals(1250.0, parsed.amount ?: Double.NaN, 0.001)
    }

    @Test
    fun `a dhanlaxmi payment-from clause names the app`() {
        val parsed = TransactionClassifier.classify(
            "INR 20.00 is debited from A/c XXXX1234 on 28-NOV-2025 - \"UPI TXN: /675325120952-MR /Payment from PhonePe/Q12345444@ybl/YESB0YBLUPI/4172120251128000100004392\". Aval Bal is INR 26,578.49.",
            "TL-DHANBK-S"
        )
        assertEquals("PhonePe", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(20.0, parsed.amount ?: Double.NaN, 0.001)
    }

    @Test
    fun `a standard chartered neft credit reads the rail label`() {
        val parsed = TransactionClassifier.classify(
            "Dear Customer, there is an NEFT credit of INR 48,796.00 in your account 123xxxx7655 on 1/11/2025.Available Balance:INR 97,885.05 -StanChart",
            "JK-SCBANK-S"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("NEFT Credit", parsed.merchant)
        assertEquals(48796.0, parsed.amount ?: Double.NaN, 0.001)
    }

    @Test
    fun `an equitas upi debit reads the payee through global patterns`() {
        val parsed = TransactionClassifier.classify(
            "INR 1,250.00 debited via UPI from Equitas A/c 5678 -Ref:987654321098 on 15-01-26 to SWIGGY INDIA. Avl Bal is INR 8,500.25.",
            "CP-EQUTAS-S"
        )
        assertEquals("SWIGGY INDIA", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Equitas Small Finance Bank", parsed.bankName)
    }

    @Test
    fun `a pluxee meal-wallet spend reads merchant and balance`() {
        val parsed = TransactionClassifier.classify(
            "Rs. 40.00 spent from Pluxee  Meal wallet, card no.xx1234 on 17-08-2026 16:03:14 at NEW SHAKTHI  . Avl bal Rs.24342.81. Not you call 18002161234",
            "AD-PLUXEE"
        )
        assertEquals("NEW SHAKTHI", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(40.0, parsed.amount ?: Double.NaN, 0.001)
    }
}
