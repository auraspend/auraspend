package com.awbuilds.auraspend.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

import com.awbuilds.auraspend.data.classification.defaultCategories

/**
 * The category editor's choices must be renderable and sane: an icon the
 * renderer cannot resolve would draw the neutral fallback (the picker would
 * look broken), and duplicate/translucent colors would make selection
 * ambiguous or invisible.
 */
class CategoryAppearanceTest {

    @Test
    fun `every offered icon renders`() {
        categoryIconChoices.forEach { choice ->
            assertEquals(
                "'$choice' must render through the glyph map, not fall back",
                choice,
                categoryIconGlyph(choice)
            )
        }
    }

    @Test
    fun `every default category icon renders`() {
        defaultCategories.forEach { category ->
            assertEquals(
                "default category '${category.name}' must use an icon the renderer knows",
                category.icon,
                categoryIconGlyph(category.icon)
            )
        }
    }

    @Test
    fun `offered colors are distinct`() {
        assertEquals(categoryColorChoices.size, categoryColorChoices.toSet().size)
    }

    @Test
    fun `offered colors are opaque`() {
        categoryColorChoices.forEach { choice ->
            assertEquals("0x%08X".format(choice), 0xFF, (choice ushr 24) and 0xFF)
        }
    }
}
