package com.awbuilds.auraspend.ui.applock

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awbuilds.auraspend.MainActivity
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.data.security.AppLockManager
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app lock, on a device.
 *
 * This is the only place the gate can be shown *end to end*: it lives in
 * `MainActivity` (a `FragmentActivity`, for the biometric prompt), it keys off
 * the real background/return lifecycle, and its whole claim is that the
 * navigation host is **not composed** while locked. Robolectric covers the lock
 * screen itself (`AppLockScreenTest`) and the policy (`AppLockManagerTest`);
 * neither can start the activity.
 *
 * Run with:
 * ```
 * ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDevDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.awbuilds.auraspend.ui.applock.AppLockDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class AppLockDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: AppLockManager get() = AppLockManager(context)

    /** Never leave the emulator or phone locked behind a PIN a test invented. */
    @After
    fun removeTheLock() = manager.clearPin()

    @Test
    fun the_gate_replaces_the_app_until_the_pin_is_entered() {
        manager.setPin(PIN)
        manager.isEnabled = true
        // A cold start: no recorded background moment, so the app must ask.
        manager.lastBackgroundedAt = 0L

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(timeoutMillis = TIMEOUT_MS) {
                compose.onAllNodesWithTag("lock-forgot").fetchSemanticsNodes().isNotEmpty()
            }
            // The load-bearing assertion: while locked, the dashboard is not
            // merely covered — it is not composed at all.
            compose.onAllNodesWithText("Total balance").assertCountEquals(0)
            compose.onAllNodesWithText(context.getString(R.string.splash_tagline)).assertCountEquals(0)

            PIN.forEach { digit -> compose.onNodeWithTag("lock-digit-$digit").performClick() }

            compose.waitUntil(timeoutMillis = TIMEOUT_MS) {
                compose.onAllNodesWithTag("lock-forgot").fetchSemanticsNodes().isEmpty()
            }
            // The app is usable again: either the splash is up (a fresh install
            // has not finished onboarding, so there is no dashboard yet) or the
            // dashboard has already drawn. Asserting only on the dashboard was
            // the first version of this test, and it failed on a fresh install
            // for the right reason — the app was showing its splash.
            val splash = context.getString(R.string.splash_tagline)
            compose.waitUntil(timeoutMillis = TIMEOUT_MS) {
                compose.onAllNodesWithText(splash).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Total balance").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun a_wrong_pin_leaves_the_gate_up() {
        manager.setPin(PIN)
        manager.isEnabled = true
        manager.lastBackgroundedAt = 0L

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(timeoutMillis = TIMEOUT_MS) {
                compose.onAllNodesWithTag("lock-forgot").fetchSemanticsNodes().isNotEmpty()
            }
            // 9 is not a digit of PIN, so four of them is four wrong attempts'
            // worth of input for a single auto-submitted entry.
            repeat(4) { compose.onNodeWithTag("lock-digit-9").performClick() }
            compose.waitForIdle()

            compose.onNodeWithTag("lock-forgot").assertExists()
            compose.onAllNodesWithText("Total balance").assertCountEquals(0)
            compose.onAllNodesWithText(context.getString(R.string.splash_tagline)).assertCountEquals(0)
        }
    }

    private companion object {
        /** Digits 2, 4, 6, 8 — so a wrong-PIN test can use 9 safely. */
        const val PIN = "2468"
        const val TIMEOUT_MS = 15_000L
    }
}
