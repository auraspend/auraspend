package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.TransactionClassifier
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * EPFO contribution confirmations carry two amounts — the passbook balance
 * first, the month's contribution second — and end "…has been received".
 * The owner's call (2026-10-14): a PF contribution is an investment, not
 * income, and the contribution amount is the transaction, not the balance.
 */
class EpfoParserTest {

    private val parser = EpfoParser()

    private val contributionBody =
        "Dear XXXXXXXX41 your passbook balance against PYKRP1234567 is Rs. 1,23,456.78. " +
            "Contribution of Rs. 3,600/- for due month Jun-26 has been received."

    @Test
    fun `registry routes both EPFO DLT senders`() {
        assertEquals("EPFO", BankParserRegistry.parserFor("VA-EPFOHO-G")?.bankName)
        assertEquals("EPFO", BankParserRegistry.parserFor("AX-EPFOHO-S")?.bankName)
    }

    @Test
    fun `contribution confirmation books the contribution amount as an investment`() {
        val p = parser.extract(contributionBody)
        assertEquals("the passbook balance must not win", 3600.0, p.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("cat_investment", p.categoryId)
        assertEquals("EPF", p.merchant)
        assertEquals("EPFO", p.bankName)
        assertNull("the due month is a period, not a transaction day", p.date)
    }

    @Test
    fun `classify routes an EPFO sender to the contribution read`() {
        val p = TransactionClassifier.classify(contributionBody, "VA-EPFOHO-G")
        assertEquals(3600.0, p.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("cat_investment", p.categoryId)
    }

    @Test
    fun `otp and linking notices from the same sender keep the base parse`() {
        val otp = "Dear Member (UAN : XXXX XXXX 4242), OTP to login to Member " +
            "Interface is 123456 (valid for 10 mins) at EPFO."
        assertNull(parser.extract(otp).amount)

        val linked = "Your UAN 101234567890 has been linked by establishment ACME " +
            "SOLUTIONS PVT LTD with date 01/07/2026."
        val linkedParsed = parser.extract(linked)
        assertNull(linkedParsed.amount)
        assertNull(linkedParsed.type)
    }

    @Test
    fun `a contribution clause without a readable amount gets no override`() {
        val body = "Dear Member, contribution of Rs. -- for due month Jun-26 " +
            "has been received."
        assertNull(parser.extract(body).categoryId)
    }
}
