package com.awbuilds.auraspend.ui.applock

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.TestApplication
import com.awbuilds.auraspend.data.security.AppLockManager
import com.awbuilds.auraspend.ui.theme.AppThemeMode
import com.awbuilds.auraspend.ui.theme.AuraSpendTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi", application = TestApplication::class)
class AppLockScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun manager(pin: String): AppLockManager =
        AppLockManager(ApplicationProvider.getApplicationContext<Context>()).apply {
            setPin(pin)
            isEnabled = true
        }

    @Test
    fun `entering the right pin unlocks`() {
        var unlocked = false
        compose.setContent {
            AuraSpendTheme(themeMode = AppThemeMode.LIGHT) {
                AppLockScreen(manager("1234"), onUnlocked = { unlocked = true }, onForgotPin = {})
            }
        }
        compose.onNodeWithTag("lock-digit-1").performClick()
        compose.onNodeWithTag("lock-digit-2").performClick()
        compose.onNodeWithTag("lock-digit-3").performClick()
        compose.onNodeWithTag("lock-digit-4").performClick()
        compose.waitForIdle()
        assertTrue(unlocked)
    }

    @Test
    fun `a wrong pin shows the error and does not unlock`() {
        var unlocked = false
        compose.setContent {
            AuraSpendTheme(themeMode = AppThemeMode.LIGHT) {
                AppLockScreen(manager("1234"), onUnlocked = { unlocked = true }, onForgotPin = {})
            }
        }
        compose.onNodeWithTag("lock-digit-9").performClick()
        compose.onNodeWithTag("lock-digit-9").performClick()
        compose.onNodeWithTag("lock-digit-9").performClick()
        compose.onNodeWithTag("lock-digit-9").performClick()
        compose.waitForIdle()
        assertFalse(unlocked)
        compose.onNodeWithText("Wrong PIN — try again").assertExists()
    }

    @Test
    fun `forgot pin offers and confirms the erase path`() {
        var forgot = false
        compose.setContent {
            AuraSpendTheme(themeMode = AppThemeMode.LIGHT) {
                AppLockScreen(manager("1234"), onUnlocked = {}, onForgotPin = { forgot = true })
            }
        }
        compose.onNodeWithTag("lock-forgot").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete all data?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertTrue(forgot)
    }
}
