package com.awbuilds.auraspend.data.update

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.awbuilds.auraspend.core.AuraLog
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "AppUpdate"
private val FLEXIBLE_FLOW_TYPE = AppUpdateType.FLEXIBLE

/**
 * Play In-App Updates (ADR 0013). Play Core is only on the dev/prod
 * classpath; F-Droid compiles the no-op in `src/fdroid`.
 *
 * The manager is created lazily so JVM/Robolectric tests that never open
 * Settings do not touch Play Core at startup.
 */
private class PlayAppUpdateController(context: Context) : AppUpdateController {
    private val appContext = context.applicationContext
    private val manager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(appContext)
    }
    override val isAvailable: Boolean by lazy {
        runCatching {
            appContext.packageManager
                .getInstallSourceInfo(appContext.packageName)
                .installingPackageName == "com.android.vending"
        }.getOrDefault(false)
    }
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    override val state: StateFlow<AppUpdateState> = _state
    private var lastFlowType: Int = FLEXIBLE_FLOW_TYPE

    private val listener = InstallStateUpdatedListener { installState ->
        when (installState.installStatus()) {
            InstallStatus.DOWNLOADED -> _state.value = AppUpdateState.Downloaded
            InstallStatus.DOWNLOADING -> _state.value = AppUpdateState.Downloading
            InstallStatus.FAILED -> _state.value = AppUpdateState.Failed
            InstallStatus.INSTALLED -> _state.value = AppUpdateState.Idle
            else -> Unit
        }
    }

    override fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (_state.value == AppUpdateState.Downloaded) return
        _state.value = AppUpdateState.Checking
        runCatching {
            manager.registerListener(listener)
            manager.appUpdateInfo.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    AuraLog.w(TAG, "Play update check failed", task.exception)
                    _state.value = AppUpdateState.Failed
                    return@addOnCompleteListener
                }
                val info = task.result
                if (info == null) {
                    _state.value = AppUpdateState.Failed
                } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    _state.value = AppUpdateState.Downloaded
                } else if (info.installStatus() == InstallStatus.DOWNLOADING) {
                    _state.value = AppUpdateState.Downloading
                } else if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                    _state.value = AppUpdateState.UpToDate
                } else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    startFlow(info, AppUpdateType.FLEXIBLE, launcher)
                } else if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    startFlow(info, AppUpdateType.IMMEDIATE, launcher)
                } else {
                    _state.value = AppUpdateState.Failed
                }
            }
        }.onFailure { error ->
            AuraLog.w(TAG, "Play update manager unavailable", error)
            _state.value = AppUpdateState.Failed
        }
    }

    private fun startFlow(
        info: AppUpdateInfo,
        type: Int,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        lastFlowType = type
        val started = runCatching {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.defaultOptions(type)
            )
        }.getOrElse { error ->
            AuraLog.w(TAG, "Play update flow failed to start", error)
            false
        }
        _state.value = if (started) {
            if (type == FLEXIBLE_FLOW_TYPE) AppUpdateState.Downloading else AppUpdateState.Idle
        } else {
            AppUpdateState.Failed
        }
    }

    override fun onUpdateFlowResult(resultCode: Int) {
        _state.value = when {
            resultCode != Activity.RESULT_OK -> AppUpdateState.Idle
            lastFlowType == FLEXIBLE_FLOW_TYPE -> AppUpdateState.Downloading
            else -> AppUpdateState.Idle
        }
    }

    override fun completeUpdate() {
        if (_state.value != AppUpdateState.Downloaded) return
        runCatching { manager.completeUpdate() }
            .onFailure { AuraLog.w(TAG, "Play completeUpdate failed", it) }
        _state.value = AppUpdateState.Idle
    }
}

fun createAppUpdateController(context: Context): AppUpdateController =
    PlayAppUpdateController(context)
