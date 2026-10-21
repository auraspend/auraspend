package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.awbuilds.auraspend.TestApplication
import com.awbuilds.auraspend.ui.theme.AuraSpendTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two surfaces that say "there is an update" without a screen of their own.
 *
 * They matter because none of this feature can be seen on a development build:
 * `IN_APP_UPDATES` is prod-only and `isAvailable` is true only for a Play
 * install, so a sideloaded APK hides both. These tests are the only place the
 * badge and the banner are actually rendered, which is also why they assert the
 * one thing a screenshot would not catch — that the badge changes what a screen
 * reader announces, and that a notice with nothing to dismiss offers no dismiss
 * target.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    // SDK 34 explicitly: compileSdk is 37, which this Robolectric cannot
    // provision (`SwipeToActTest` records the same reason).
    sdk = [34],
    qualifiers = "w411dp-h891dp-420dpi",
    application = TestApplication::class
)
class UpdateSurfacesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the badge changes what the avatar announces`() {
        compose.setContent {
            AuraSpendTheme {
                SettingsAvatarButton(onClick = {}, showUpdateBadge = true)
            }
        }

        // "Settings" alone would tell a screen-reader user nothing about why the
        // header grew a mark.
        compose.onNodeWithContentDescription("Settings, update available")
            .assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Settings").assertCountEquals(0)
    }

    @Test
    fun `without a badge the avatar is just Settings`() {
        compose.setContent {
            AuraSpendTheme {
                SettingsAvatarButton(onClick = {})
            }
        }

        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Settings, update available")
            .assertCountEquals(0)
    }

    @Test
    fun `a notice offers its action and a dismiss`() {
        var actioned = 0
        var dismissed = 0
        compose.setContent {
            AuraSpendTheme {
                AuraNoticeBanner(
                    title = "A newer version is available",
                    message = "Google Play has an update ready for you.",
                    icon = Icons.Default.SystemUpdate,
                    actionLabel = "Update",
                    onAction = { actioned++ },
                    onDismiss = { dismissed++ }
                )
            }
        }

        compose.onNodeWithText("Update").performClick()
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(1, actioned)
        assertEquals(1, dismissed)
    }

    @Test
    fun `a notice with nothing to dismiss has no dismiss target`() {
        // The restart notice is the case: the only way past it is the restart,
        // so a close button there would be a tap that does nothing.
        compose.setContent {
            AuraSpendTheme {
                AuraNoticeBanner(
                    title = "Update ready",
                    message = "Restart AuraSpend to finish installing it.",
                    icon = Icons.Default.SystemUpdate,
                    actionLabel = "Restart",
                    onAction = {},
                    onDismiss = null
                )
            }
        }

        compose.onNodeWithText("Restart").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Dismiss").assertCountEquals(0)
    }
}
