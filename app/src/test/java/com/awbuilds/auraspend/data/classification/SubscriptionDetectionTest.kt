package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Subscription intelligence: recurring-payment keywords and known subscription
 * services must flag transactions even when the on-device LLM is absent
 * (ai = null), because that is the default free-tier path.
 */
class SubscriptionDetectionTest {

    private fun parsed(raw: String, merchant: String? = null) = ParsedBankMessage(
        amount = 499.0,
        type = com.awbuilds.auraspend.domain.model.TransactionType.EXPENSE,
        merchant = merchant,
        confidence = 0.85f,
        rawMessage = raw
    )

    @Test
    fun `recurring keyword forces subscription without ai`() {
        val fused = AiSignalFusion.fuse(
            parsed("INR 199 auto-debit for HOTSTAR renewal"),
            ai = null
        )
        assertTrue(fused.isSubscription)
        assertEquals("cat_subscription", fused.parsed.categoryId)
    }

    @Test
    fun `nach mandate forces subscription`() {
        val fused = AiSignalFusion.fuse(
            parsed("Rs 1200 NACH debit GITHUB INDIA mandate ref 88123"),
            ai = null
        )
        assertTrue(fused.isSubscription)
    }

    @Test
    fun `known service merchant forces subscription without keywords`() {
        val fused = AiSignalFusion.fuse(
            parsed("Rs 649 spent at Netflix.com on card", merchant = "Netflix.Com"),
            ai = null
        )
        assertTrue(fused.isSubscription)
        assertEquals("cat_subscription", fused.parsed.categoryId)
    }

    @Test
    fun `spotify upi debit becomes subscription`() {
        val fused = AiSignalFusion.fuse(
            parsed("Sent Rs 119 to spotify@icici UPI ref 772345"),
            ai = null
        )
        assertTrue(fused.isSubscription)
    }

    @Test
    fun `plain amazon purchase is not a subscription`() {
        val fused = AiSignalFusion.fuse(
            parsed("Rs 2499 debited for AMAZON INDIA order", merchant = "Amazon India"),
            ai = null
        )
        assertFalse(fused.isSubscription)
    }

    @Test
    fun `grocery category survives non-subscription`() {
        val fused = AiSignalFusion.fuse(
            ParsedBankMessage(
                amount = 800.0,
                type = com.awbuilds.auraspend.domain.model.TransactionType.EXPENSE,
                categoryId = "cat_grocery",
                confidence = 0.85f,
                rawMessage = "Rs 800 debited at BigBasket"
            ),
            ai = null
        )
        assertFalse(fused.isSubscription)
        assertEquals("cat_grocery", fused.parsed.categoryId)
    }

    @Test
    fun `subscription refund stays income not subscription`() {
        val fused = AiSignalFusion.fuse(
            ParsedBankMessage(
                amount = 649.0,
                type = com.awbuilds.auraspend.domain.model.TransactionType.INCOME,
                merchant = "Netflix",
                confidence = 0.85f,
                rawMessage = "Rs 649 refunded by NETFLIX.COM"
            ),
            ai = null
        )
        assertFalse(fused.isSubscription)
    }

    @Test
    fun `an auto-debit word on a credit is not a subscription`() {
        val fused = AiSignalFusion.fuse(
            ParsedBankMessage(
                amount = 649.0,
                type = com.awbuilds.auraspend.domain.model.TransactionType.INCOME,
                merchant = "Netflix",
                confidence = 0.85f,
                rawMessage = "Rs 649 credited after auto-debit reversal"
            ),
            ai = null
        )
        assertFalse(fused.isSubscription)
    }

    // ─── SMS filter layer ────────────────────────────────────────────────────

    @Test
    fun `otp messages are skipped`() {
        assertTrue(SmsAutoClassifier.isOtpOrAlertMessage("Your OTP is 482913. Do not share. -HDFC"))
        assertTrue(SmsAutoClassifier.isOtpOrAlertMessage("123456 is your one time password"))
    }

    @Test
    fun `promo messages are skipped`() {
        assertTrue(SmsAutoClassifier.isOtpOrAlertMessage("Mega SALE! Flat 70% off today only. Limited offer!"))
        assertTrue(SmsAutoClassifier.isOtpOrAlertMessage("Congratulations! You won a lucky draw prize"))
    }

    @Test
    fun `balance alerts are skipped`() {
        assertTrue(SmsAutoClassifier.isOtpOrAlertMessage("Your available balance is Rs. 5000"))
    }

    @Test
    fun `real transaction survives alert words`() {
        // Contains "alert"-adjacent boilerplate but has amount + verb.
        assertFalse(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "Rs 500 debited from A/c XX1111 on 23-08-26. Avl Bal Rs 25000.00 - SBI"
            )
        )
    }

    // ─── Sender-ID gate ──────────────────────────────────────────────────────

    @Test
    fun `dlt bank sender ids recognised`() {
        assertTrue(SmsAutoClassifier.looksLikeBankSender("AD-HDFCBK"))
        assertTrue(SmsAutoClassifier.looksLikeBankSender("VM-ICICIB"))
        assertTrue(SmsAutoClassifier.looksLikeBankSender("SBICRD"))
        assertTrue(SmsAutoClassifier.looksLikeBankSender("JD-PAYTM"))
        assertFalse(SmsAutoClassifier.looksLikeBankSender("+919876543210"))
        assertFalse(SmsAutoClassifier.looksLikeBankSender(""))
    }
}
