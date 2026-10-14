package com.awbuilds.auraspend.ui.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The persistence shape behind "Not a subscription".
 *
 * Pure parse/serialize only — the SharedPreferences wrapper is deliberately
 * thin. The failure direction matters: a malformed value must degrade to
 * "nothing dismissed" (a suggestion re-appears) and never to "everything
 * dismissed" (suggestions vanish with no way back).
 */
class SuggestionDismissalsTest {

    @Test
    fun `serialize and parse round-trip`() {
        val names = setOf("netflix com", "spotify india p")

        assertEquals(names, SuggestionDismissals.parse(SuggestionDismissals.serialize(names)))
    }

    @Test
    fun `missing or blank values mean nothing dismissed`() {
        assertTrue(SuggestionDismissals.parse(null).isEmpty())
        assertTrue(SuggestionDismissals.parse("").isEmpty())
        assertTrue(SuggestionDismissals.parse("   ").isEmpty())
    }

    @Test
    fun `a malformed value degrades to nothing dismissed`() {
        assertTrue(SuggestionDismissals.parse("not json at all").isEmpty())
        assertTrue(SuggestionDismissals.parse("{broken").isEmpty())
    }

    @Test
    fun `serialization is sorted and skips blanks`() {
        val raw = SuggestionDismissals.serialize(setOf("b", "a", ""))

        assertEquals(SuggestionDismissals.parse(raw), setOf("a", "b"))
    }
}
