package com.awbuilds.auraspend.data.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * The state of the Play In-App Update check.
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
 * The Play-only update seam (ADR 0013).
 *
 * The real implementation lives in `src/gms` and uses the Play Core SDK; the
 * `fdroid` flavor supplies a no-op with the same type name. `BuildConfig.IN_APP_UPDATES`
 * is true only for prod, so the Settings entry point does not exist elsewhere.
 */
interface AppUpdateController {
    /** True only when this install came from the Play Store, not a sideload. */
    val isAvailable: Boolean
    val state: StateFlow<AppUpdateState>

    /** Ask Play whether an update is available and, if so, start the flow. */
    fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Forward the result of the Play update dialog. */
    fun onUpdateFlowResult(resultCode: Int)

    /** Complete a downloaded flexible update; Play restarts the app. */
    fun completeUpdate()
}
