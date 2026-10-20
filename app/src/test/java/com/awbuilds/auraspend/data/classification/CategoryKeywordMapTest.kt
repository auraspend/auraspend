package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryKeywordMapTest {

    @Test
    fun `resolves merchants to the seeded category ids`() {
        assertEquals("cat_food", CategoryKeywordMap.resolve("Swiggy", isExpense = true))
        assertEquals("cat_grocery", CategoryKeywordMap.resolve("BigBasket", isExpense = true))
        assertEquals("cat_transport", CategoryKeywordMap.resolve("IOCL PETROL PUMP", isExpense = true))
        assertEquals("cat_subscription", CategoryKeywordMap.resolve("Netflix", isExpense = true))
        assertEquals("cat_bills", CategoryKeywordMap.resolve("Adani Electricity", isExpense = true))
    }

    @Test
    fun `travel booking portals are transport`() {
        // The map had airlines and two bus/air portals, but no hotel side, so a
        // hotel booking fell into "Other" while the other parser called it
        // travel. Found by comparing parse results over a message corpus
        // (handoff 0068).
        assertEquals("cat_transport", CategoryKeywordMap.resolve("AGODA COMPANY P", isExpense = true))
        assertEquals("cat_transport", CategoryKeywordMap.resolve("MakeMyTrip", isExpense = true))
        assertEquals("cat_transport", CategoryKeywordMap.resolve("TRIVAGO", isExpense = true))
    }

    @Test
    fun `a retail chain is shopping`() {
        // The other parser filed this one as shopping; this app left it blank.
        assertEquals("cat_shopping", CategoryKeywordMap.resolve("BIG BAZAAR", isExpense = true))
    }

    @Test
    fun `income never resolves to a spending category`() {
        // A refund from Swiggy is not a Swiggy expense. Reading it through the
        // expense table would file income as spending and corrupt every budget.
        assertEquals("cat_other", CategoryKeywordMap.resolve("refund from Swiggy", isExpense = false))
        assertEquals("cat_salary", CategoryKeywordMap.resolve("salary credit Acme", isExpense = false))
    }

    @Test
    fun `an unknown merchant resolves to null so the caller keeps looking`() {
        // Null, not cat_other: defaulting here would silently dump every
        // unrecognised merchant into a real budget bucket.
        assertNull(CategoryKeywordMap.resolve("ZZQ Unknown Merchant", isExpense = true))
        assertNull(CategoryKeywordMap.resolve("", isExpense = true))
        assertNull(CategoryKeywordMap.resolve(null, isExpense = true))
    }

    @Test
    fun `matching is on whole words, never substrings`() {
        // The invariant in CLAUDE.md: substring-unsafe keys caused real
        // misfiling. "Big" must not match "BigBasket", nor must a bare "vi".
        assertNull(CategoryKeywordMap.resolve("Big Box Warehouse", isExpense = true))
        assertNull(CategoryKeywordMap.resolve("Vikram Stores", isExpense = true))
    }

    @Test
    fun `the longest matching keyword wins`() {
        assertEquals("cat_subscription", CategoryKeywordMap.resolve("amazon prime video", isExpense = true))
        assertEquals("cat_shopping", CategoryKeywordMap.resolve("amazon", isExpense = true))
    }

    @Test
    fun `multi-word phrases do not match inside longer words`() {
        // "prime video" must not fire on "composite videography".
        assertEquals("cat_shopping", CategoryKeywordMap.resolve("composite videography shop", isExpense = true))
    }
}
