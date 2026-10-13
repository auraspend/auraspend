package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.TransactionClassifier
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave-3 routing: the remaining national banks get sender→bank attribution
 * over the global engine, and the card/wallet cohort (HSBC, Standard
 * Chartered, AMEX, OneCard, Slice, CRED, LazyPay, Jupiter, JioPay, Airtel,
 * NSDL, Pluxee, Amazon Pay, Cashfree) routes its senders.
 *
 * Corrections for these banks are staged for wave 4 where their fixtures
 * demand them; this wave pins the routing, which is what short messages
 * without a bank name in the body actually need.
 */
class WaveThreeBankParsersTest {

    @Test
    fun `wave-3 sender ids route to their bank`() {
        assertEquals("Indian Bank", BankParserRegistry.parserFor("AD-INDBNK-S")?.bankName)
        assertEquals("Indian Overseas Bank", BankParserRegistry.parserFor("AD-IOBCHN-S")?.bankName)
        assertEquals("IDBI Bank", BankParserRegistry.parserFor("VM-IDBIBK-S")?.bankName)
        assertEquals("UCO Bank", BankParserRegistry.parserFor("AD-UCOBNK-S")?.bankName)
        assertEquals("Karnataka Bank", BankParserRegistry.parserFor("AD-KBLBNK-S")?.bankName)
        assertEquals("City Union Bank", BankParserRegistry.parserFor("AD-CUBLTD-S")?.bankName)
        assertEquals("Dhanlaxmi Bank", BankParserRegistry.parserFor("AD-DHANBK-S")?.bankName)
        assertEquals("Department of Post", BankParserRegistry.parserFor("AD-DOPBNK-S")?.bankName)
        assertEquals("Equitas Small Finance Bank", BankParserRegistry.parserFor("AD-EQUTAS-S")?.bankName)
        assertEquals("Punjab & Sind Bank", BankParserRegistry.parserFor("AD-PSBANK-S")?.bankName)
        assertEquals("Utkarsh Small Finance Bank", BankParserRegistry.parserFor("AD-UTKSPR-S")?.bankName)
        assertEquals("JK Bank", BankParserRegistry.parserFor("AD-JKBANK-S")?.bankName)
        assertEquals("HSBC", BankParserRegistry.parserFor("VM-HSBCIN-S")?.bankName)
        assertEquals("Standard Chartered", BankParserRegistry.parserFor("AD-SCBANK-S")?.bankName)
        assertEquals("American Express", BankParserRegistry.parserFor("AD-AMEXIN-S")?.bankName)
        assertEquals("OneCard", BankParserRegistry.parserFor("AD-ONECRD-S")?.bankName)
        assertEquals("Slice", BankParserRegistry.parserFor("AD-SLCEIT-S")?.bankName)
        assertEquals("CRED", BankParserRegistry.parserFor("JK-CREDIN-S")?.bankName)
        assertEquals("LazyPay", BankParserRegistry.parserFor("AD-LZYPAY-S")?.bankName)
        assertEquals("Jupiter", BankParserRegistry.parserFor("AD-JTEDGE-S")?.bankName)
        assertEquals("Jio Payments Bank", BankParserRegistry.parserFor("AD-JIOPBS-S")?.bankName)
        assertEquals("JioPay", BankParserRegistry.parserFor("JM-JIOPAY-S")?.bankName)
        assertEquals("Airtel Payments Bank", BankParserRegistry.parserFor("AD-AIRBNK-S")?.bankName)
        assertEquals("NSDL Payments Bank", BankParserRegistry.parserFor("AD-NSDLPB-S")?.bankName)
        assertEquals("Pluxee", BankParserRegistry.parserFor("AD-PLUXEE-S")?.bankName)
        assertEquals("Amazon Pay", BankParserRegistry.parserFor("AD-JUSPAY-S")?.bankName)
        assertEquals("Cashfree", BankParserRegistry.parserFor("JX-CSHFRE-S")?.bankName)
        assertEquals("Saraswat Bank", BankParserRegistry.parserFor("AD-SARBNK-S")?.bankName)
    }

    @Test
    fun `south indian senders never fall to the indian bank parser`() {
        assertEquals("South Indian Bank", BankParserRegistry.parserFor("AD-SIBSMS-S")?.bankName)
        assertEquals("Indian Bank", BankParserRegistry.parserFor("INDIAN BANK")?.bankName)
    }

    @Test
    fun `earlier waves keep their senders`() {
        assertEquals("PNB", BankParserRegistry.parserFor("VA-PNBSMS-S")?.bankName)
        assertEquals("PNB", BankParserRegistry.parserFor("VA-PNBCCD-S")?.bankName)
        assertEquals("Kotak Mahindra", BankParserRegistry.parserFor("JD-KOTAKB-S")?.bankName)
    }

    @Test
    fun `a wave-3 sender stamps its bank name on the parse`() {
        val parsed = TransactionClassifier.classify(
            "Rs.500.00 debited from A/c XX1234 on 20-10-26 at SWIGGY. Avl Bal Rs. 10,000.00",
            "AD-INDBNK-S"
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Indian Bank", parsed.bankName)
    }
}
