package com.awbuilds.auraspend.data.update

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
import com.awbuilds.auraspend.core.AuraLog
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "AppUpdate"
private val FLEXIBLE_FLOW_TYPE = AppUpdateType.FLEXIBLE

/** A day. The check is a call to the Play service, not to AuraSpend. */
private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

/**
 * Play In-App Updates (ADR 0013). Play Core is only on the dev/prod
 * classpath; F-Droid compiles the no-op in `src/fdroid`.
 *
 * The manager is created lazily so JVM/Robolectric tests that never open
 * Settings do not touch Play Core at startup.
 *
 * **This talks to the Play service on the device and nowhere else.** No
 * version, install id or usage detail reaches AuraSpend — there is no
 * AuraSpend server — so the update check does not weaken the privacy claim
 * anywhere in the app or on the website.
 */
private class PlayAppUpdateController(context: Context) : AppUpdateController {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy { UpdatePreferences.of(appContext) }
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

    private val _availability = MutableStateFlow<AppUpdateAvailability?>(null)
    override val availability: StateFlow<AppUpdateAvailability?> = _availability

    private val _dismissed = MutableStateFlow(
        prefs.getLong(UpdatePreferences.KEY_DISMISSED_VERSION, 0L).takeIf { it > 0L }
    )
    override val dismissedInstalledVersion: StateFlow<Long?> = _dismissed

    private var lastFlowType: Int = FLEXIBLE_FLOW_TYPE

    /** Play holds a list of listeners: registering the same one twice calls it twice. */
    private var listenerRegistered = false

    private fun registerListenerOnce() {
        if (listenerRegistered) return
        manager.registerListener(listener)
        listenerRegistered = true
    }

    private val listener = InstallStateUpdatedListener { installState ->
        when (installState.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                _state.value = AppUpdateState.Downloaded
                // A finished download outranks an "available" banner: the last
                // step is a restart, and the surface that offers it is driven by
                // `readyToRestart`.
                _availability.value = _availability.value?.copy(readyToRestart = true)
            }
            InstallStatus.DOWNLOADING -> _state.value = AppUpdateState.Downloading
            InstallStatus.FAILED -> _state.value = AppUpdateState.Failed
            InstallStatus.INSTALLED -> _state.value = AppUpdateState.Idle
            else -> Unit
        }
    }

    /** minSdk is 30, so `longVersionCode` always exists. */
    private fun installedVersionCode(): Long =
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
        }.getOrDefault(0L)

    /**
     * Turn Play's answer into the app's own vocabulary.
     *
     * `clientVersionStalenessDays()` is null when Play cannot trust the device
     * clock; that is a reason to stay quiet, not to assume zero.
     */
    private fun read(info: AppUpdateInfo): AppUpdateAvailability {
        val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        return AppUpdateAvailability(
            available = available,
            availableVersionCode = if (available) info.availableVersionCode() else null,
            installedVersionCode = installedVersionCode(),
            priority = info.updatePriority(),
            stalenessDays = runCatching { info.clientVersionStalenessDays() }.getOrNull(),
            flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            readyToRestart = info.installStatus() == InstallStatus.DOWNLOADED,
        )
    }

    /** Process-lifetime: the controller is owned by the Application. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Ask Play, and wait for the answer.
     *
     * `kotlinx-coroutines-play-services` is not in the dependency graph, so the
     * Play `Task` is bridged by hand. `Task` has no `cancel()`, so a resumed
     * caller is guarded instead: a late answer is dropped rather than resuming a
     * continuation nobody is waiting on.
     */
    override suspend fun checkNow(): AppUpdateAvailability? {
        if (!isAvailable) return null
        val info = runCatching {
            suspendCancellableCoroutine { continuation ->
                manager.appUpdateInfo.addOnCompleteListener { done ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    if (!done.isSuccessful) {
                        AuraLog.w(TAG, "Update check failed", done.exception)
                        continuation.resume(null)
                    } else {
                        continuation.resume(done.result)
                    }
                }
            }
        }.onFailure { error ->
            AuraLog.w(TAG, "Play update manager unavailable", error)
        }.getOrNull() ?: return null

        val answer = read(info)
        _availability.value = answer
        return answer
    }

    /** Ask Play without starting anything, at most once a day. */
    override fun checkSilently() {
        if (!isAvailable) return
        val now = System.currentTimeMillis()
        val last = prefs.getLong(UpdatePreferences.KEY_LAST_CHECK_MS, 0L)
        if (now - last in 0 until CHECK_INTERVAL_MS) return
        scope.launch {
            val answer = checkNow() ?: return@launch
            prefs.edit { putLong(UpdatePreferences.KEY_LAST_CHECK_MS, now) }
            // A silent check that finds one supersedes an earlier "you're up to
            // date" from a tap, which would otherwise leave Settings
            // contradicting itself (a stale verdict on top of a fresh one).
            if (answer.available && _state.value == AppUpdateState.UpToDate) {
                _state.value = AppUpdateState.Idle
            }
        }
    }

    /**
     * Read the install status on resume. Play's guidance is to do this on every
     * resume, not only when the user asks: a flexible download started last week
     * is otherwise invisible until they go looking.
     */
    override fun resumeDownloadedUpdate() {
        if (!isAvailable) return
        runCatching {
            registerListenerOnce()
            manager.appUpdateInfo.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    AuraLog.w(TAG, "Resume update check failed", task.exception)
                    return@addOnCompleteListener
                }
                val info = task.result ?: return@addOnCompleteListener
                _availability.value = read(info)
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    _state.value = AppUpdateState.Downloaded
                }
            }
        }.onFailure { error ->
            AuraLog.w(TAG, "Play update manager unavailable", error)
        }
    }

    override fun startImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val known = _availability.value ?: return
        if (!known.available || known.priority < UpdatePolicy.IMMEDIATE_PRIORITY) return
        runCatching {
            manager.appUpdateInfo.addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                val info = task.result ?: return@addOnCompleteListener
                // Re-check with Play rather than trusting the cached answer: the
                // release may have been withdrawn between the silent check and
                // the launch.
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnCompleteListener
                if (info.updatePriority() < UpdatePolicy.IMMEDIATE_PRIORITY) return@addOnCompleteListener
                if (!info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) return@addOnCompleteListener
                startFlow(info, AppUpdateType.IMMEDIATE, launcher)
            }
        }.onFailure { error ->
            AuraLog.w(TAG, "Immediate update flow failed to start", error)
        }
    }

    override fun dismissBanner() {
        val installed = installedVersionCode()
        prefs.edit { putLong(UpdatePreferences.KEY_DISMISSED_VERSION, installed) }
        _dismissed.value = installed
    }

    override fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (_state.value == AppUpdateState.Downloaded) return
        _state.value = AppUpdateState.Checking
        runCatching {
            registerListenerOnce()
            manager.appUpdateInfo.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    AuraLog.w(TAG, "Play update check failed", task.exception)
                    _state.value = AppUpdateState.Failed
                    return@addOnCompleteListener
                }
                val info = task.result
                if (info == null) {
                    _state.value = AppUpdateState.Failed
                    return@addOnCompleteListener
                }
                _availability.value = read(info)
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED ->
                        _state.value = AppUpdateState.Downloaded
                    info.installStatus() == InstallStatus.DOWNLOADING ->
                        _state.value = AppUpdateState.Downloading
                    info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE ->
                        _state.value = AppUpdateState.UpToDate
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                        startFlow(info, AppUpdateType.FLEXIBLE, launcher)
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                        startFlow(info, AppUpdateType.IMMEDIATE, launcher)
                    else -> _state.value = AppUpdateState.Failed
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
        if (_state.value != AppUpdateState.Downloaded &&
            _availability.value?.readyToRestart != true
        ) {
            return
        }
        runCatching { manager.completeUpdate() }
            .onFailure { AuraLog.w(TAG, "Play completeUpdate failed", it) }
        _state.value = AppUpdateState.Idle
    }
}

fun createAppUpdateController(context: Context): AppUpdateController =
    PlayAppUpdateController(context)
