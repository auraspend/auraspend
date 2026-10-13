package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.TransactionClassifier
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave-5 routing: the last Indian institutions with reference fixtures or
 * plain coverage — the two mutual-fund senders (with their in-clause payees)
 * and the remaining banks and payments banks.
 */
class WaveFiveBankParsersTest {

    @Test
    fun `wave-5 sender ids route to their institution`() {
        assertEquals("HDFC Mutual Fund", BankParserRegistry.parserFor("AD-HDFCMF-S")?.bankName)
        assertEquals("Navi Mutual Fund", BankParserRegistry.parserFor("AD-NAVAMC-S")?.bankName)
        assertEquals("Kerala Gramin Bank", BankParserRegistry.parserFor("AD-KGBANK-S")?.bankName)
        assertEquals("Kerala Bank", BankParserRegistry.parserFor("AD-KELBNK-S")?.bankName)
        assertEquals("DBS Bank", BankParserRegistry.parserFor("AD-DBSBNK-S")?.bankName)
        assertEquals("India Post Payments Bank", BankParserRegistry.parserFor("AD-IPBMSG-S")?.bankName)
        assertEquals("Jana Small Finance Bank", BankParserRegistry.parserFor("AD-JANABK-S")?.bankName)
    }

    @Test
    fun `the navi banking-arm sender is not claimed by the mutual fund parser`() {
        assertEquals(null, BankParserRegistry.parserFor("NAVI"))
    }

    @Test
    fun `an hdfc sip purchase names the fund`() {
        val parsed = TransactionClassifier.classify(
            "Your SIP Purchase in Folio 38822412/82 under HDFC Mid Cap Fund-DG for Rs. 7,000.65 has been processed at the NAV of 224.603 for 31.169 units and 24-Feb-2026. Sincerely, HDFCMF",
            "AD-HDFCMF-S"
        )
        assertEquals(7000.65, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("HDFC Mid Cap Fund-DG", parsed.merchant)
        assertEquals("HDFC Mutual Fund", parsed.bankName)
    }

    @Test
    fun `a navi sip allotment names the fund over the NAV tail`() {
        val parsed = TransactionClassifier.classify(
            "Unit Allotment Update:\nYour SIP purchase of Rs. 1,49,999.98 in Navi Largecap Fund DG has been processed at applicable NAV. The units will be alloted in 1-2 working days.\nTeam Navi Mutual Fund",
            "VK-NAVAMC-T"
        )
        assertEquals(149999.98, parsed.amount ?: Double.NaN, 0.001)
        assertEquals("Navi Largecap Fund DG", parsed.merchant)
        assertEquals("Navi Mutual Fund", parsed.bankName)
    }

    @Test
    fun `a navi nav update is not a transaction`() {
        val parsed = TransactionClassifier.classify(
            "NAV updated for Navi Largecap Fund. Latest NAV: 12.3456. Visit the Navi app for details.",
            "AD-NAVAMC-S"
        )
        assertEquals(null, parsed.type)
    }

    @Test
    fun `a kerala gramin phone-number credit keeps the number as counterparty`() {
        val parsed = TransactionClassifier.classify(
            "Dear Customer, Account XXXX123 is credited with INR 3000 on 20-10-2025 08:15:26 from 9000000042@upi. UPI Ref. no. 529807237409-Kerala Gramin Bank",
            "BX-KGBANK-S"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("9000000042", parsed.merchant)
        assertEquals("Kerala Gramin Bank", parsed.bankName)
    }
}
