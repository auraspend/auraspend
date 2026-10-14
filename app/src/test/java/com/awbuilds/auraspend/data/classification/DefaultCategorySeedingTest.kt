package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Startup seeding reconciles defaults instead of only filling an empty table:
 * a category added later (Investments) must reach existing installs, because
 * the EPFO parser files real transactions into its id.
 */
class DefaultCategorySeedingTest {

    @Test
    fun `an empty install receives every default`() {
        val missing = missingDefaultCategories(emptySet())
        assertEquals(defaultCategories.size, missing.size)
        assertTrue(missing.any { it.id == "cat_investment" })
    }

    @Test
    fun `only the missing categories are seeded`() {
        val existing = defaultCategories.map { it.id }.toSet() - "cat_investment"
        assertEquals(listOf("cat_investment"), missingDefaultCategories(existing).map { it.id })
    }

    @Test
    fun `a complete install seeds nothing`() {
        assertTrue(missingDefaultCategories(defaultCategories.map { it.id }.toSet()).isEmpty())
    }
}
