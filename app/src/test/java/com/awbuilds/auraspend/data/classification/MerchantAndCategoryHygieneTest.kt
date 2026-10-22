package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Merchant hygiene and the category gaps a real export exposed (2026-10-22).
 *
 * The merchant column carried fragments on ~60 rows: a clause cut mid-account
 * ("debit of A"), a bare honorific, a bare number, a button label. Some came
 * from the extractor, some from the on-device classifier — which is why the
 * validation now happens in one place ([BankMessageParser.sanitizeMerchant])
 * and the fusion calls it too.
 *
 * The category half is small on purpose: only families that were landing in
 * *Transfer* (which hides a payment from every spending total) or in *Other*.
 * A merchant this project does not know stays unresolved — `cat_other` is the
 * honest answer, not a guess.
 */
class MerchantAndCategoryHygieneTest {

    // ─── Fragments are not merchants ─────────────────────────────────────────

    @Test
    fun `a clause cut mid-account is not a merchant`() {
        assertNull(BankMessageParser.sanitizeMerchant("debit of A"))
    }

    @Test
    fun `a bare honorific is not a merchant`() {
        assertNull(BankMessageParser.sanitizeMerchant("Mr"))
        assertNull(BankMessageParser.sanitizeMerchant("Mrs"))
    }

    @Test
    fun `a bare number is not a merchant`() {
        assertNull(BankMessageParser.sanitizeMerchant("60"))
    }

    @Test
    fun `a button label is not a merchant`() {
        assertNull(BankMessageParser.sanitizeMerchant("view now"))
        assertNull(BankMessageParser.sanitizeMerchant("view details"))
    }

    @Test
    fun `an honorific attached to a name is still a merchant`() {
        // "Mr K Sudishkumar" is a real counterparty in the export; only the
        // honorific *alone* is a fragment.
        assertNotNull(BankMessageParser.sanitizeMerchant("Mr K Sudishkumar"))
        assertNotNull(BankMessageParser.sanitizeMerchant("Mrs Lakshmi"))
    }

    @Test
    fun `an ordinary merchant survives`() {
        assertNotNull(BankMessageParser.sanitizeMerchant("A2Z SUPERMARKET"))
        assertNotNull(BankMessageParser.sanitizeMerchant("Swiggy"))
    }

    // ─── Families that were hidden from the totals ───────────────────────────

    @Test
    fun `a hospital is healthcare even when the name is abbreviated`() {
        assertEquals("cat_healthcare", keywordCategoryFor("KIMS AL SHIF"))
        assertEquals("cat_healthcare", CategoryKeywordMap.resolve("KIMS AL SHIF", isExpense = true))
    }

    @Test
    fun `a fuel station is transport even in the plural`() {
        assertEquals("cat_transport", keywordCategoryFor("PTP FUELS"))
        assertEquals("cat_transport", keywordCategoryFor("PEEVEER FUEL"))
    }

    @Test
    fun `a gold savings scheme is an investment`() {
        assertEquals("cat_investment", keywordCategoryFor("SAFE GOLD"))
        assertEquals("cat_investment", CategoryKeywordMap.resolve("SAFE GOLD", isExpense = true))
    }

    @Test
    fun `one supermarket, one category`() {
        // "A2Z SUPERMARKET" and "A TO Z SUPER" are the same shop; they used to
        // resolve to Shopping and Transfer respectively.
        assertEquals("cat_grocery", keywordCategoryFor("A2Z SUPERMARKET"))
        assertEquals("cat_grocery", keywordCategoryFor("A TO Z SUPER"))
    }

    @Test
    fun `a restaurant and a bakery are food`() {
        assertEquals("cat_food", keywordCategoryFor("SOCIAL KITCHEN"))
        assertEquals("cat_food", keywordCategoryFor("B R M BAKERY"))
        assertEquals("cat_food", keywordCategoryFor("ANITHA SWEETS"))
    }

    @Test
    fun `a supermarket chain is grocery`() {
        assertEquals("cat_grocery", keywordCategoryFor("LULU PALAKKAD"))
    }
}
