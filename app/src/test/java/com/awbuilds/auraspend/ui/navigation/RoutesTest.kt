package com.awbuilds.auraspend.ui.navigation

import com.awbuilds.auraspend.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Activity tab's two strings, kept apart.
 *
 * `Routes.ACTIVITY` used to *be* the destination pattern —
 * `activity?categoryId={categoryId}&query={query}` — and Home's "see all"
 * passed it straight to `navigate()`. Navigation matched it as written, so the
 * arguments arrived as the literal text "{categoryId}" and "{query}": the
 * search box opened pre-filled with `{query}` and filtered every row out
 * (reported 2026-10-21). The same mismatch meant the tab never drew itself as
 * selected, because a destination's `route` is the pattern and the bar
 * compared it with the bare string.
 *
 * There is no navigation test harness in this project, and composing the whole
 * graph to replay one tap would cost more than it catches. These assertions
 * pin the *contract* instead: the navigable route carries no placeholders, the
 * pattern is what the graph and the chrome use, and the two stay a pair.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class RoutesTest {

    @Test
    fun `the navigable Activity route has no placeholders`() {
        assertFalse(
            "a route passed to navigate() must not carry {…} templates",
            Routes.ACTIVITY.contains("{")
        )
        assertFalse(Routes.ACTIVITY.contains("?"))
    }

    @Test
    fun `the destination pattern is the navigable route plus its arguments`() {
        assertTrue(
            "the pattern must be the route the tab navigates to, with arguments",
            Routes.ACTIVITY_PATTERN.startsWith("${Routes.ACTIVITY}?")
        )
        assertTrue(Routes.ACTIVITY_PATTERN.contains("{categoryId}"))
        assertTrue(Routes.ACTIVITY_PATTERN.contains("{query}"))
    }

    @Test
    fun `chrome visibility matches the destination, not the navigate string`() {
        // `NavDestination.route` is the pattern, so this is the set the chrome
        // is compared against. Holding the bare route here would hide the bar
        // on Activity.
        assertTrue(Routes.topLevel.contains(Routes.ACTIVITY_PATTERN))
        assertFalse(Routes.topLevel.contains(Routes.ACTIVITY))
    }

    @Test
    fun `activity builds a complete route with both arguments`() {
        // Blank args mean "no filter"; a missing argument would leave the
        // placeholder in the URL, which is the bug this file exists for.
        assertEquals("activity?categoryId=&query=", Routes.activity())
        assertEquals(
            "activity?categoryId=cat_food&query=Cafe",
            Routes.activity(categoryId = "cat_food", query = "Cafe")
        )
    }
}
