package com.awbuilds.auraspend.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The restraint rules for the update surfaces.
 *
 * This feature puts five surfaces on one silent check — a banner on Home, a dot
 * on the avatar, a line in Settings, Play's blocking flow on launch, and a
 * system notification — which is exactly how a helpful feature becomes the
 * thing the user turns off. Each test below is one of the ways that happens:
 * announcing a version twice, announcing a version that has waited zero days,
 * opening a blocking screen from a notification, or bringing a dismissed banner
 * back on every launch.
 *
 * [UpdatePolicy] is pure on purpose so all of it is decidable here, without a
 * device, a Play Store install or a UI.
 */
class UpdatePolicyTest {

    private fun availability(
        available: Boolean = true,
        availableVersionCode: Int? = 42,
        installedVersionCode: Long = 15L,
        priority: Int = 0,
        stalenessDays: Int? = 5,
        flexibleAllowed: Boolean = true,
        immediateAllowed: Boolean = false,
        readyToRestart: Boolean = false,
    ) = AppUpdateAvailability(
        available = available,
        availableVersionCode = availableVersionCode,
        installedVersionCode = installedVersionCode,
        priority = priority,
        stalenessDays = stalenessDays,
        flexibleAllowed = flexibleAllowed,
        immediateAllowed = immediateAllowed,
        readyToRestart = readyToRestart,
    )

    // ── In-app ────────────────────────────────────────────────────────────────

    @Test
    fun `nothing available says nothing`() {
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.inApp(availability(available = false, availableVersionCode = null), null)
        )
    }

    @Test
    fun `a finished download outranks an available one`() {
        // The download already happened. Behind an "update available" banner it
        // would look like the app forgot its own progress.
        assertEquals(
            AppUpdatePrompt.Restart,
            UpdatePolicy.inApp(availability(readyToRestart = true), null)
        )
    }

    @Test
    fun `a finished download is offered even to a user who dismissed the banner`() {
        assertEquals(
            AppUpdatePrompt.Restart,
            UpdatePolicy.inApp(
                availability(readyToRestart = true),
                dismissedInstalledVersion = 15L
            )
        )
    }

    @Test
    fun `an urgent release goes to Play's own flow`() {
        assertEquals(
            AppUpdatePrompt.Immediate,
            UpdatePolicy.inApp(
                availability(priority = UpdatePolicy.IMMEDIATE_PRIORITY, immediateAllowed = true),
                null
            )
        )
    }

    @Test
    fun `an urgent release the update type forbids falls back to the banner`() {
        // Priority is the uploader's opinion; `isUpdateTypeAllowed` is Play's
        // answer about this install. Starting a flow Play does not allow would
        // fail, and the banner is still a working path.
        assertEquals(
            AppUpdatePrompt.Banner,
            UpdatePolicy.inApp(
                availability(priority = 5, immediateAllowed = false),
                null
            )
        )
    }

    @Test
    fun `priority three is not urgent`() {
        assertEquals(
            AppUpdatePrompt.Banner,
            UpdatePolicy.inApp(availability(priority = 3, immediateAllowed = true), null)
        )
    }

    @Test
    fun `a dismissal holds for the version it was made on`() {
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.inApp(availability(installedVersionCode = 15L), 15L)
        )
    }

    @Test
    fun `a dismissal does not survive an update`() {
        // The user said "not now" about a build they have since replaced.
        assertEquals(
            AppUpdatePrompt.Banner,
            UpdatePolicy.inApp(availability(installedVersionCode = 16L), 15L)
        )
    }

    @Test
    fun `an update with no permitted flow says nothing`() {
        // Play reports the release but allows neither the flexible nor the
        // immediate flow — the practical case is a release withdrawn between the
        // check and the tap. The banner's one button would fail, so there is no
        // banner.
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.inApp(
                availability(flexibleAllowed = false, immediateAllowed = false),
                null
            )
        )
    }

    @Test
    fun `a null dismissal is not a dismissal`() {
        assertEquals(AppUpdatePrompt.Banner, UpdatePolicy.inApp(availability(), null))
    }

    // ── Background ────────────────────────────────────────────────────────────

    @Test
    fun `a notification waits for the update to be worth it`() {
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.background(availability(stalenessDays = 1), null)
        )
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.background(availability(stalenessDays = 2), null)
        )
        assertEquals(
            AppUpdatePrompt.Notify,
            UpdatePolicy.background(
                availability(stalenessDays = UpdatePolicy.NOTIFY_AFTER_STALE_DAYS),
                null
            )
        )
    }

    @Test
    fun `unknown staleness is not staleness`() {
        // Play returns null when it cannot trust the device clock. Guessing zero
        // would fire on the first check; guessing "stale" would nag every day.
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.background(availability(stalenessDays = null), null)
        )
    }

    @Test
    fun `an urgent release never becomes a notification`() {
        // It opens Play's blocking full-screen flow. A notification that leads
        // to a wall the user cannot dismiss is worse than silence.
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.background(
                availability(priority = UpdatePolicy.IMMEDIATE_PRIORITY, stalenessDays = 30),
                null
            )
        )
    }

    @Test
    fun `a release is announced once`() {
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.background(availability(availableVersionCode = 42), 42)
        )
        // A newer upload is a new thing to say.
        assertEquals(
            AppUpdatePrompt.Notify,
            UpdatePolicy.background(availability(availableVersionCode = 43), 42)
        )
    }

    @Test
    fun `a finished download is not notified about`() {
        // The card in the app is the prompt; a notification would ask the user
        // to open the app to do something it must do itself.
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.background(availability(readyToRestart = true), null)
        )
    }

    @Test
    fun `nothing available says nothing in the background either`() {
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.background(
                availability(available = false, availableVersionCode = null, stalenessDays = 30),
                null
            )
        )
    }

    @Test
    fun `a missing version code is not announced`() {
        // Without a code there is no key for "already told them", so the same
        // notification would repeat on every daily check.
        assertEquals(
            AppUpdatePrompt.None,
            UpdatePolicy.background(availability(availableVersionCode = null), null)
        )
    }
}
