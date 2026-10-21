package com.awbuilds.auraspend.data.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * The state of the live Play In-App Update flow, as the Settings row reports
 * it while the user is watching.
 *
 * [Unsupported] is the F-Droid/dev no-op state; the Settings row is hidden in
 * those builds, so it is never rendered. It exists so the shared UI can compile
 * against one type without a nullable controller.
 */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data object Downloading : AppUpdateState
    data object Downloaded : AppUpdateState
    data object Failed : AppUpdateState
    data object Unsupported : AppUpdateState
}

/**
 * What a silent check found. Cached so the UI can read it without asking Play
 * again, and so the policy below can be a pure function of it.
 *
 * **Play exposes no version *name* for an available update** — only its version
 * code — so nothing here carries a label. A version code means nothing to a
 * person; "there is a newer version" is the honest sentence, and the app's own
 * [installedVersionCode] is the one number worth showing.
 */
data class AppUpdateAvailability(
    /** True when Play holds a newer build than the one installed. */
    val available: Boolean,
    /** The version code Play is offering, or null when it offered nothing. */
    val availableVersionCode: Int?,
    /** The version code this device is running — what a dismissal is scoped to. */
    val installedVersionCode: Long,
    /** Play's per-release priority, 0–5. Set by the upload, never guessed. */
    val priority: Int,
    /**
     * Days since the update became available, or null when Play cannot say.
     * It returns null when the device's clock is untrusted, which is a reason
     * to stay quiet rather than to guess.
     */
    val stalenessDays: Int?,
    val flexibleAllowed: Boolean,
    val immediateAllowed: Boolean,
    /** A flexible download already finished; the app only needs to restart. */
    val readyToRestart: Boolean,
) {
    companion object {
        /** Nothing known yet, or nothing to say. */
        fun none(installedVersionCode: Long) = AppUpdateAvailability(
            available = false,
            availableVersionCode = null,
            installedVersionCode = installedVersionCode,
            priority = 0,
            stalenessDays = null,
            flexibleAllowed = false,
            immediateAllowed = false,
            readyToRestart = false,
        )
    }
}

/** What the app should do about an update it knows about. */
sealed interface AppUpdatePrompt {
    /** Say nothing. */
    data object None : AppUpdatePrompt

    /** A flexible download finished: offer the restart. */
    data object Restart : AppUpdatePrompt

    /** Play's blocking full-screen flow, for a release that marked itself urgent. */
    data object Immediate : AppUpdatePrompt

    /** The quiet in-app banner. */
    data object Banner : AppUpdatePrompt

    /** A system notification, for someone who has not opened the app. */
    data object Notify : AppUpdatePrompt
}

/**
 * Which surface speaks, and when.
 *
 * Pure and tested, because the whole point of this feature is *restraint*: five
 * surfaces over one check become five interruptions very easily, and a rule
 * spread across the call sites is a rule nobody can read. Every decision here
 * is a function of what Play said and what the user already told us.
 *
 * The order of the checks is the policy:
 *
 *  1. **A finished download outranks everything.** It already happened; the
 *     last step is a restart, and hiding it behind an "available" banner would
 *     make the app look like it forgot.
 *  2. **An urgent release skips the quiet path** and goes to Play's own
 *     blocking flow. That is what a priority of 4 or 5 means, and it only
 *     happens when Play also says the immediate type is allowed.
 *  3. **A dismissal is honoured until the app is updated.** The user said "not
 *     now"; asking again on every release is the nagging failure this exists to
 *     avoid. The dismissal is scoped to the installed version, so a later
 *     update can ask once more.
 *  4. **A background notification waits.** It only fires once the update has
 *     been sitting there for a few days, for a version the user has not already
 *     been told about, and never for one that would open a blocking screen.
 */
object UpdatePolicy {
    /** How long an update waits before it is worth a notification. */
    const val NOTIFY_AFTER_STALE_DAYS = 3

    /** Play's 0–5 scale; 4 and 5 are the "this matters" end of it. */
    const val IMMEDIATE_PRIORITY = 4

    /**
     * The prompt for a user who has the app open.
     *
     * @param dismissedInstalledVersion the installed version code at the moment
     *   the banner was dismissed, or null if it never was.
     */
    fun inApp(
        availability: AppUpdateAvailability,
        dismissedInstalledVersion: Long?,
    ): AppUpdatePrompt = when {
        availability.readyToRestart -> AppUpdatePrompt.Restart
        !availability.available -> AppUpdatePrompt.None
        availability.priority >= IMMEDIATE_PRIORITY && availability.immediateAllowed ->
            AppUpdatePrompt.Immediate
        // Play says the update exists but permits neither flow — a release
        // withdrawn between the check and the tap. A banner whose only button
        // leads to "Couldn't check for updates" is worse than no banner.
        !availability.flexibleAllowed && !availability.immediateAllowed -> AppUpdatePrompt.None
        dismissedInstalledVersion == availability.installedVersionCode -> AppUpdatePrompt.None
        else -> AppUpdatePrompt.Banner
    }

    /**
     * The prompt for a background check, which may arrive as a notification.
     *
     * @param notifiedAvailableVersion the version code already notified about,
     *   so one release is announced once.
     */
    fun background(
        availability: AppUpdateAvailability,
        notifiedAvailableVersion: Int?,
    ): AppUpdatePrompt {
        if (!availability.available || availability.readyToRestart) return AppUpdatePrompt.None
        // An urgent update gets Play's own full-screen flow inside the app; a
        // notification that leads to a blocking screen is worse than none.
        if (availability.priority >= IMMEDIATE_PRIORITY) return AppUpdatePrompt.None
        val availableCode = availability.availableVersionCode ?: return AppUpdatePrompt.None
        if (notifiedAvailableVersion == availableCode) return AppUpdatePrompt.None
        // Unknown staleness is not evidence of staleness.
        val staleDays = availability.stalenessDays ?: return AppUpdatePrompt.None
        if (staleDays < NOTIFY_AFTER_STALE_DAYS) return AppUpdatePrompt.None
        return AppUpdatePrompt.Notify
    }
}

/**
 * The Play-only update seam (ADR 0013).
 *
 * The real implementation lives in `src/gms` and uses the Play Core SDK; the
 * `fdroid` flavor supplies a no-op with the same type name. `BuildConfig.IN_APP_UPDATES`
 * is true only for prod, so the entry points do not exist elsewhere.
 *
 * Two clearances of check exist on purpose:
 *
 *  * [checkSilently] asks Play without starting anything, so the app can know
 *    an update exists without interrupting anyone. It is throttled and its
 *    answer lands in [availability].
 *  * [checkForUpdate] is the user tapping the Settings row, and it starts the
 *    flow in the same breath.
 */
interface AppUpdateController {
    /** True only when this install came from the Play Store, not a sideload. */
    val isAvailable: Boolean
    val state: StateFlow<AppUpdateState>

    /** The last silent check, or null before one has run. */
    val availability: StateFlow<AppUpdateAvailability?>

    /**
     * The installed version code at which the banner was dismissed, or null.
     * Observable, so dismissing it recomposes the screen that showed it.
     */
    val dismissedInstalledVersion: StateFlow<Long?>

    /**
     * Ask Play now, without starting anything, and return its answer. Null when
     * Play could not be asked at all.
     *
     * The background worker uses this because it has to know the answer before
     * it decides whether to post a notification; the foreground uses
     * [checkSilently], which is the same check with a daily throttle.
     */
    suspend fun checkNow(): AppUpdateAvailability?

    /** Ask Play whether an update is available, without starting a flow. */
    fun checkSilently()

    /**
     * Read the install status on resume. A flexible download that finished
     * while the app was closed is invisible until this runs — Play's own
     * guidance is to check on resume, not only when the user asks.
     */
    fun resumeDownloadedUpdate()

    /** Play's blocking flow, for a release that marked itself urgent. */
    fun startImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Remember that the user dismissed the banner, scoped to this install. */
    fun dismissBanner()

    /** Ask Play whether an update is available and, if so, start the flow. */
    fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Forward the result of the Play update dialog. */
    fun onUpdateFlowResult(resultCode: Int)

    /** Complete a downloaded flexible update; Play restarts the app. */
    fun completeUpdate()
}
