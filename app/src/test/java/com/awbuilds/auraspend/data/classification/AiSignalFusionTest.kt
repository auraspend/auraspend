package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.ai.SmsExtraction
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSignalFusionTest {

    private fun parsed(
        amount: Double? = 250.0,
        type: TransactionType? = TransactionType.EXPENSE,
        merchant: String? = null,
        categoryId: String? = null,
        raw: String = "INR 250 debited"
    ) = ParsedBankMessage(
        amount = amount,
        type = type,
        merchant = merchant,
        categoryId = categoryId,
        confidence = 0.85f,
        rawMessage = raw
    )

    private fun ai(
        isTransaction: Boolean = true,
        type: TransactionType? = TransactionType.EXPENSE,
        categoryId: String? = "cat_food",
        isSubscription: Boolean = false,
        merchant: String? = null,
        probability: Float? = null
    ) = SmsExtraction(
        isTransaction, type, categoryId, merchant, isSubscription, "{}",
        confidence = null, isTransactionProbability = probability
    )

    @Test
    fun `llm veto wipes amount and type`() {
        val fused = AiSignalFusion.fuse(parsed(), ai(isTransaction = false))
        assertNull(fused.parsed.amount)
        assertNull(fused.parsed.type)
        assertFalse(fused.isSubscription)
        assertTrue(fused.usedAi)
    }

    @Test
    fun `an unsure model verdict must not discard a transaction`() {
        val fused = AiSignalFusion.fuse(
            parsed(),
            ai(isTransaction = false, probability = 0.4f)
        )
        assertNotNull(
            "a 0.4-confidence veto must not delete a parsed transaction",
            fused.parsed.amount
        )
        assertNotNull(fused.parsed.type)
    }

    @Test
    fun `a confident model verdict still cannot delete a parsed transaction`() {
        // Even a 0.99 veto is refused here, because the regex layer *did* read
        // this message. Confidence alone is not enough; the parser's success is
        // the second, independent condition.
        val fused = AiSignalFusion.fuse(
            parsed(),
            ai(isTransaction = false, probability = 0.99f)
        )
        assertNotNull(fused.parsed.amount)
    }

    @Test
    fun `a confident veto may discard a message the parser could not read`() {
        val fused = AiSignalFusion.fuse(
            parsed(amount = null, type = null, raw = "Your OTP is 482913 do not share"),
            ai(isTransaction = false, probability = 0.99f)
        )
        assertNull("a 0.99 veto on an unreadable message may discard it", fused.parsed.amount)
    }

    @Test
    fun `a confident veto may discard a message the parser read with no amount`() {
        // The parser recognised the message but found no money in it, so there
        // is nothing to protect.
        val fused = AiSignalFusion.fuse(
            parsed(amount = null, raw = "Avl balance Rs 25,000.00 - SBI"),
            ai(isTransaction = false, probability = 0.95f)
        )
        assertTrue("usedAi should still be reported", fused.usedAi)
    }

    @Test
    fun `a runtime with no calibrated head keeps the previous unconditional veto`() {
        // Qwen reports no probability, so null means "no opinion" and today's
        // behaviour must be preserved exactly. This is the control that makes
        // the seam change behaviour-preserving for the shipped model.
        val fused = AiSignalFusion.fuse(parsed(), ai(isTransaction = false, probability = null))
        assertNull("an uncalibrated runtime must still veto", fused.parsed.amount)
    }

    @Test
    fun `regex keyword wins on type conflict`() {
        val fused = AiSignalFusion.fuse(
            parsed(type = TransactionType.EXPENSE),
            ai(type = TransactionType.INCOME)
        )
        assertEquals(TransactionType.EXPENSE, fused.parsed.type)
    }

    @Test
    fun `ai fills missing type`() {
        val fused = AiSignalFusion.fuse(
            parsed(type = null, amount = 10.0),
            ai(type = TransactionType.INCOME)
        )
        assertEquals(TransactionType.INCOME, fused.parsed.type)
    }

    @Test
    fun `curated regex category survives when not a subscription`() {
        val fused = AiSignalFusion.fuse(
            parsed(categoryId = "cat_grocery"),
            ai(categoryId = "cat_other")
        )
        assertEquals("cat_grocery", fused.parsed.categoryId)
    }

    @Test
    fun `ai category used when regex has none`() {
        val fused = AiSignalFusion.fuse(
            parsed(categoryId = null),
            ai(categoryId = "cat_transport")
        )
        assertEquals("cat_transport", fused.parsed.categoryId)
    }

    @Test
    fun `ai subscription forces subscription category`() {
        val fused = AiSignalFusion.fuse(
            parsed(categoryId = "cat_entertainment"),
            ai(isSubscription = true, categoryId = "cat_subscription")
        )
        assertTrue(fused.isSubscription)
        assertEquals("cat_subscription", fused.parsed.categoryId)
    }

    @Test
    fun `recurring keywords force subscription even without ai`() {
        val fused = AiSignalFusion.fuse(
            parsed(raw = "Rs 199 auto-debit for HOTSTAR renewal"),
            ai = null
        )
        assertTrue(fused.isSubscription)
        assertEquals("cat_subscription", fused.parsed.categoryId)
        assertFalse(fused.usedAi)
    }

    @Test
    fun `ai merchant fills gap only`() {
        val keep = AiSignalFusion.fuse(parsed(merchant = "Swiggy"), ai(merchant = "Swiggy Order"))
        assertEquals("Swiggy", keep.parsed.merchant)

        val fill = AiSignalFusion.fuse(parsed(merchant = null), ai(merchant = "Netflix"))
        assertEquals("Netflix", fill.parsed.merchant)
    }

    @Test
    fun `agreement boosts confidence and conflict reduces it`() {
        val agree = AiSignalFusion.fuse(parsed(), ai(type = TransactionType.EXPENSE))
        val disagree = AiSignalFusion.fuse(parsed(), ai(type = TransactionType.INCOME))
        assertTrue(agree.parsed.confidence > disagree.parsed.confidence)
    }

    @Test
    fun `an insurance premium is a recurring subscription`() {
        val parsed = BankMessageParser.parse("Rs 4500 debited towards LIC insurance premium")
        val fused = AiSignalFusion.fuse(parsed, ai = null)
        org.junit.Assert.assertTrue("LIC premium should be recurring", fused.isSubscription)
        org.junit.Assert.assertEquals("cat_subscription", fused.parsed.categoryId)
    }

    @Test
    fun `a model cannot assign the transfer category`() {
        // A company credit is full of transfer language ("NEFT Cr-…"), so the
        // model files it as Transfer — and a Transfer is excluded from the
        // balance, which made five months of salary vanish from Home.
        val parsed = ParsedBankMessage(
            amount = 55000.0,
            type = TransactionType.INCOME,
            merchant = "SOME COMPANY LIMITED",
            rawMessage = "NEFT Cr-ACME0000001-SOME COMPANY LIMITED-NEFT ref 123456"
        )
        val ai = SmsExtraction(
            isTransaction = true,
            type = TransactionType.INCOME,
            category = "cat_transfer",
            merchant = "SOME COMPANY LIMITED",
            isSubscription = false,
            confidence = 0.9f
        )
        val fused = AiSignalFusion.fuse(parsed, ai)
        assertNotEquals("cat_transfer", fused.parsed.categoryId)
    }

}
