package com.awbuilds.auraspend.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose's `LocalContentColor` default is black. Nothing else provided one, so
 * untinted icons (back buttons, the month chevrons) and bare text were black on
 * every background: readable in LIGHT, invisible in DARK and AMOLED — found on
 * the owner's device. The theme now provides the scheme's onBackground; this
 * pins that for every mode, including AMOLED which builds its own scheme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w411dp-h891dp-420dpi",
    application = com.awbuilds.auraspend.TestApplication::class
)
class ThemeContentColorTest {

    @get:Rule
    val compose = createComposeRule()

    private fun assertContentFollowsScheme(mode: AppThemeMode) {
        var content: Color? = null
        var expected: Color? = null
        compose.setContent {
            AuraSpendTheme(themeMode = mode) {
                content = LocalContentColor.current
                expected = MaterialTheme.colorScheme.onBackground
            }
        }
        compose.waitForIdle()
        assertEquals("untinted content must follow the scheme in $mode", expected, content)
        assertNotEquals("never Compose's black default in $mode", Color.Black, content)
    }

    @Test
    fun `light theme provides its scheme content colour`() {
        assertContentFollowsScheme(AppThemeMode.LIGHT)
    }

    @Test
    fun `dark theme provides its scheme content colour`() {
        assertContentFollowsScheme(AppThemeMode.DARK)
    }

    @Test
    fun `amoled theme provides its scheme content colour`() {
        assertContentFollowsScheme(AppThemeMode.AMOLED)
    }
}
