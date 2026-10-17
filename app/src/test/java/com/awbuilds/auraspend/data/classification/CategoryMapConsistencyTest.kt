package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The parser's category map and the fusion gap-filler must agree on shared
 * keys. They did not: `netflix` was Entertainment in the parser map and
 * Subscriptions in [CategoryKeywordMap], so a payment's final category depended
 * on which layer happened to fill it first.
 */
class CategoryMapConsistencyTest {

    @Test
    fun `streaming services agree between both maps`() {
        val shared = listOf(
            "netflix", "spotify", "amazon prime", "prime video", "hotstar",
            "disney", "sony liv", "zee5", "gaana", "jiocinema",
            "icloud", "google one"
        )
        shared.forEach { key ->
            assertEquals(
                "category disagreement for '$key'",
                CategoryKeywordMap.resolve(key, isExpense = true),
                keywordCategoryFor(key)
            )
        }
    }
}
