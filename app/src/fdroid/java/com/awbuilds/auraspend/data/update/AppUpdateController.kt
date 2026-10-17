package com.awbuilds.auraspend.data.update

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * F-Droid's no-op update controller (ADR 0013). F-Droid builds update through
 * F-Droid; they carry no Play Core SDK. The Settings row is hidden by
 * `BuildConfig.IN_APP_UPDATES`, so this implementation is never user-visible.
 */
private class NoOpAppUpdateController : AppUpdateController {
    override val isAvailable: Boolean = false
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Unsupported)
    override val state: StateFlow<AppUpdateState> = _state

    override fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        _state.value = AppUpdateState.Unsupported
    }

    override fun onUpdateFlowResult(resultCode: Int) = Unit

    override fun completeUpdate() = Unit
}

fun createAppUpdateController(@Suppress("UNUSED_PARAMETER") context: Context): AppUpdateController =
    NoOpAppUpdateController()
