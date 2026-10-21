package com.awbuilds.auraspend.data.update

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * F-Droid's no-op update controller (ADR 0013). F-Droid builds update through
 * F-Droid; they carry no Play Core SDK. The Settings row and the update banner
 * are hidden by `BuildConfig.IN_APP_UPDATES`, so this implementation is never
 * user-visible.
 *
 * It answers every question with "nothing to say" rather than throwing, so the
 * shared UI and the background worker compile and run against one type.
 */
private class NoOpAppUpdateController : AppUpdateController {
    override val isAvailable: Boolean = false
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Unsupported)
    override val state: StateFlow<AppUpdateState> = _state
    private val _availability = MutableStateFlow<AppUpdateAvailability?>(null)
    override val availability: StateFlow<AppUpdateAvailability?> = _availability
    private val _dismissed = MutableStateFlow<Long?>(null)
    override val dismissedInstalledVersion: StateFlow<Long?> = _dismissed

    override suspend fun checkNow(): AppUpdateAvailability? = null

    override fun checkSilently() = Unit

    override fun resumeDownloadedUpdate() = Unit

    override fun startImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) = Unit

    override fun dismissBanner() = Unit

    override fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        _state.value = AppUpdateState.Unsupported
    }

    override fun onUpdateFlowResult(resultCode: Int) = Unit

    override fun completeUpdate() = Unit
}

fun createAppUpdateController(@Suppress("UNUSED_PARAMETER") context: Context): AppUpdateController =
    NoOpAppUpdateController()
