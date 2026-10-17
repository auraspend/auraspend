package com.awbuilds.auraspend.data.classification

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two keyword tables against the failure mode that is invisible in
 * code review: a duplicate key.
 *
 * `"mart"` was declared as `cat_grocery` under a comment explaining that groceries
 * must be listed "before Shopping, so 'bigbasket' is never read as retail" — and
 * then declared *again* as `cat_shopping`. A Kotlin map keeps the last value, and
 * `CategoryKeywordMap.resolve` is longest-key-wins rather than insertion-ordered,
 * so neither declaration looked wrong on its own while every "Reliance Mart"-style
 * merchant was filed as Shopping. No test pinned either value.
 *
 * The scan is source-level because the built map cannot report the collision — by
 * the time it exists, the loser is already gone.
 */
class KeywordMapGuardTest {

    private val classificationDir = listOf(
        File("src/main/java/com/awbuilds/auraspend/data/classification"),
        File("app/src/main/java/com/awbuilds/auraspend/data/classification")
    ).firstOrNull { it.isDirectory }
        ?: error("classification source directory not found; unit tests run with the module dir as CWD")

    /** `"key" to "value"` keys inside the block starting at [declaration]. */
    private fun keysIn(file: File, declaration: String): List<String> {
        val text = file.readText()
        val start = text.indexOf(declaration)
        require(start >= 0) { "declaration '$declaration' not found in ${file.name}" }
        // The block ends at the first column-0 closing paren after the declaration.
        val end = Regex("\n\\)\n").find(text, start)?.range?.first ?: text.length
        return Regex("\"([^\"]+)\"\\s+to\\s+\"")
            .findAll(text.substring(start, end))
            .map { it.groupValues[1] }
            .toList()
    }

    private fun assertNoDuplicates(label: String, keys: List<String>) {
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(
            "$label declares the same key more than once: $duplicates. A map keeps " +
                "the last value, so the earlier entry silently does nothing — and " +
                "resolution here is longest-key-wins, not insertion order, so " +
                "reordering cannot fix it.",
            duplicates.isEmpty()
        )
    }

    @Test
    fun `the fusion keyword map has no duplicate keys`() {
        val file = File(classificationDir, "CategoryKeywordMap.kt")
        val expense = keysIn(file, "private val EXPENSE")
        val income = keysIn(file, "private val INCOME")
        assertTrue("the expense table should not be empty", expense.size > 40)
        assertNoDuplicates("CategoryKeywordMap.EXPENSE", expense)
        assertNoDuplicates("CategoryKeywordMap.INCOME", income)
    }

    @Test
    fun `the parser keyword map has no duplicate keys`() {
        val file = File(classificationDir, "DefaultCategories.kt")
        val keys = keysIn(file, "val categoryKeywordMap")
        assertTrue("the parser table should not be empty", keys.size > 200)
        assertNoDuplicates("categoryKeywordMap", keys)
    }

    @Test
    fun `a mart merchant is grocery, the way the table always claimed`() {
        // The pinned value the duplicate key used to decide by accident.
        assertEquals("cat_grocery", CategoryKeywordMap.resolve("Reliance Mart", isExpense = true))
        assertEquals("cat_grocery", CategoryKeywordMap.resolve("Vijay Mart", isExpense = true))
        // Whole-word matching keeps the key safe: "Walmart" has no "mart" token,
        // so it resolves to nothing rather than being filed as a grocery shop.
        assertNull(CategoryKeywordMap.resolve("Walmart", isExpense = true))
    }

    @Test
    fun `the known divergences between the two maps are deliberate and pinned`() {
        // The parser table calls streaming services entertainment; the fusion
        // table calls them subscriptions. Traced deliberately before pinning it:
        // in the production path `AiSignalFusion` resolves `isSubscription` first
        // (`looksLikeSubscriptionService` names these same services), so
        // `cat_subscription` wins and the divergence is invisible in the app. It
        // shows only in the regex-only measurement, where it costs category
        // accuracy. Unifying the tables would move measured category accuracy, so
        // it is a deliberate change with a re-measurement, not a drive-by edit.
        // ("NETFLIX SUBSCRIPTION" would resolve to cat_subscription in this table
        // too — "subscription" is the longer key — so the bare service name is the
        // case that shows the divergence.)
        assertEquals("cat_subscription", keywordCategoryFor("NETFLIX"))
        assertEquals("cat_subscription", CategoryKeywordMap.resolve("Netflix", isExpense = true))
        assertEquals("cat_subscription", AiSignalFusion.fuse(
            BankMessageParser.parse("INR 649 debited for NETFLIX SUBSCRIPTION"),
            ai = null
        ).parsed.categoryId)
    }
}
