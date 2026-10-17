package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for keyword-driven category resolution.
 *
 * The old resolver matched keys as raw SUBSTRINGS, so "fee" matched "coffee"
 * (-> Education), "credit" matched every "Credit Card" payment (-> Salary),
 * "vi" matched "dividend"/"private" (-> Bills) and "mall" matched "small"
 * (-> Shopping). Resolution is now word-boundary + longest-key-wins; these
 * tests pin the failure cases.
 */
class CategoryResolutionTest {

    // ─── The substring-matching regressions ─────────────────────────────────

    @Test
    fun `coffee is food not education via fee substring`() {
        assertEquals("cat_food", keywordCategoryFor("COFFEE SHOP"))
        assertEquals("cat_food", keywordCategoryFor("Coffee Day Xpress"))
    }

    @Test
    fun `credit card payment is never salary`() {
        assertNull(keywordCategoryFor("Credit Card XX1234"))
        val parsed = BankMessageParser.parse("Spent Rs 500 on Credit Card XX1234 at AMAZON")
        assertEquals("cat_shopping", parsed.categoryId)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `dividend credit is not bills via vi substring`() {
        assertNull(keywordCategoryFor("Dividend credited to your account"))
    }

    @Test
    fun `small cafe is food not shopping via mall substring`() {
        assertEquals("cat_food", keywordCategoryFor("Small Cafe near me"))
    }

    @Test
    fun `facebook payment is not education via book substring`() {
        assertNull(keywordCategoryFor("Facebook ads paid"))
    }

    // ─── Word-boundary + longest-match behaviour ────────────────────────────

    @Test
    fun `swiggy instamart prefers grocery over food`() {
        assertEquals("cat_grocery", keywordCategoryFor("SWIGGY INSTAMART"))
    }

    @Test
    fun `jiomart is grocery while jio recharge is bills`() {
        assertEquals("cat_grocery", keywordCategoryFor("JioMart"))
        assertEquals("cat_bills", keywordCategoryFor("Jio recharge"))
    }

    @Test
    fun `merchant upi payment keeps its own category over transfer`() {
        assertEquals("cat_food", keywordCategoryFor("UPI/DR/512345/SWIGGY"))
        // A rail is routing metadata, not a category (owner's call, 2026-10-16):
        // "upi", "neft", "imps", "rtgs" and "transfer" no longer map to Transfer,
        // because that filing hid 43 % of a closed tester's export — including
        // payments to shops whose category was perfectly readable. A bare rail with
        // no merchant keyword resolves to *no* category here; the person-to-person
        // case (rail + a person-shaped counterparty) is decided in
        // `BankMessageParser.categoryIdFor`, where the direction is known, and is
        // pinned by `TransferAndInvestmentCategoryTest`.
        assertNull(keywordCategoryFor("UPI ref 512345 paid to rahim"))
    }

    @Test
    fun `hp petrol pump is transport not electronics`() {
        assertEquals("cat_transport", getCategoryIdForKeyword("HP PETROL PUMP"))
    }

    @Test
    fun `punctuation becomes whitespace so trailing commas still match`() {
        assertEquals("cat_food", keywordCategoryFor("Swiggy,"))
    }

    @Test
    fun `digits survive for keys like office 365 and 1mg`() {
        assertEquals("cat_subscription", keywordCategoryFor("Office 365 renewal"))
        assertEquals("cat_healthcare", keywordCategoryFor("Tata 1mg order"))
    }

    // ─── End-to-end through the parser (merchant-first resolution) ──────────

    @Test
    fun `swiggy debit classifies as food end to end`() {
        val parsed = BankMessageParser.parse("INR 250 debited A/c XX1234 at SWIGGY on 01-07")
        assertEquals("cat_food", parsed.categoryId)
    }

    @Test
    fun `netflix autopay classifies as a subscription pre-fusion`() {
        // The parser map and CategoryKeywordMap now agree; AiSignalFusion also
        // marks the recurring keyword. No layer has to "fix" the other.
        val parsed = BankMessageParser.parse("AutoPay of Rs 499 NETFLIX e-mandate successful")
        assertEquals("cat_subscription", parsed.categoryId)
    }

    @Test
    fun `salary income maps to salary`() {
        val parsed = BankMessageParser.parse("Rs 45000 salary credited to A/c XX1234 NEFT ACME CORP")
        assertEquals("cat_salary", parsed.categoryId)
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `income rent is not filed as a household bill`() {
        // The expense keyword map says rent -> Bills. Applying that map to an
        // income row turned "rent received from tenant" into a bill; income now
        // goes through its own direction-aware resolver.
        val parsed = BankMessageParser.parse("Rs 20000 rent received from tenant")
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("cat_other", parsed.categoryId)
    }

    @Test
    fun `a refund keeps the original merchant category`() {
        val parsed = BankMessageParser.parse("Rs 999 refunded for cancelled Zomato order")
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("cat_food", parsed.categoryId)
    }

    @Test
    fun `manual resolution uses the merchant when the parser left category null`() {
        val parsed = com.awbuilds.auraspend.domain.model.ParsedBankMessage(
            merchant = "Swiggy",
            type = TransactionType.EXPENSE,
            rawMessage = ""
        )
        assertEquals("cat_food", TransactionClassifier.resolveCategory(parsed))
    }
}
