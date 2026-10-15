package com.awbuilds.auraspend.ui.applock

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awbuilds.auraspend.MainActivity
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.data.security.AppLockManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

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
 * The PIN is set by an outer rule, before the compose rule launches the
 * activity: a PIN that does not exist when `MainActivity.onCreate` runs is a PIN
 * that does not lock anything, and `createEmptyComposeRule` with a manual
 * `ActivityScenario.launch` — the first version of this class — could not attach
 * to the app's composition at all on a slow phone (`No compose hierarchies found
 * in the app`, and every retry timed out), even though the lock itself rendered
 * fine there.
 *
 * Run with:
 * ```
 * ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDevDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.awbuilds.auraspend.ui.applock.AppLockDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class AppLockDeviceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: AppLockManager get() = AppLockManager(context)

    /**
     * A cold start with a PIN already set, and — whatever happens — no lock left
     * behind on the device afterwards. It runs *outside* the activity rule
     * because `onCreate` reads the lock state.
     */
    private val coldStartLocked: TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    manager.setPin(PIN)
                    manager.isEnabled = true
                    manager.lastBackgroundedAt = 0L
                    try {
                        base.evaluate()
                    } finally {
                        manager.clearPin()
                    }
                }
            }
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(coldStartLocked).around(compose)

    /**
     * Wake the screen before anything is asserted.
     *
     * A phone that has been sitting in a pocket has its display off, and with it
     * off Compose produces **no semantics nodes at all** — so every query here
     * returns "nothing found", the tests fail with a confusing timeout, and
     * `screencap` returns a black frame. That cost an hour on a Moto G40 whose
     * lock screen was rendering perfectly the whole time. Asserting on a UI
     * requires the UI to be allowed to draw.
     */
    @Before
    fun wakeScreen() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }

    /** Poll a UI condition, tolerating "not composed yet" rather than failing on it. */
    private fun waitUntilUi(condition: () -> Boolean) =
        compose.waitUntil(timeoutMillis = TIMEOUT_MS) {
            runCatching(condition).getOrDefault(false)
        }

    private fun lockVisible(): Boolean =
        compose.onAllNodesWithTag("lock-forgot").fetchSemanticsNodes().isNotEmpty()

    private fun lockGone(): Boolean =
        compose.onAllNodesWithTag("lock-forgot").fetchSemanticsNodes().isEmpty()

    private fun textVisible(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun the_gate_replaces_the_app_until_the_pin_is_entered() {
        waitUntilUi { lockVisible() }
        // The load-bearing assertion: while locked, the app is not merely
        // covered — it is not composed at all.
        compose.onAllNodesWithText("Total balance").assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.splash_tagline)).assertCountEquals(0)

        PIN.forEach { digit -> compose.onNodeWithTag("lock-digit-$digit").performClick() }

        waitUntilUi { lockGone() }
        // Usable again: either the splash is up (a fresh install has not
        // finished onboarding, so there is no dashboard yet) or the dashboard
        // has already drawn.
        val splash = context.getString(R.string.splash_tagline)
        waitUntilUi { textVisible(splash) || textVisible("Total balance") }
    }

    @Test
    fun a_wrong_pin_leaves_the_gate_up() {
        waitUntilUi { lockVisible() }
        // 9 is not a digit of PIN, so four of them is one wrong auto-submitted
        // entry.
        repeat(4) { compose.onNodeWithTag("lock-digit-9").performClick() }
        compose.waitForIdle()

        compose.onNodeWithTag("lock-forgot").assertExists()
        compose.onAllNodesWithText("Total balance").assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.splash_tagline)).assertCountEquals(0)
    }

    private companion object {
        /** Digits 2, 4, 6, 8 — so a wrong-PIN test can use 9 safely. */
        const val PIN = "2468"
        /** Generous: a Moto G40 needs ~13 s of class verification before its first frame. */
        const val TIMEOUT_MS = 30_000L
    }
}
