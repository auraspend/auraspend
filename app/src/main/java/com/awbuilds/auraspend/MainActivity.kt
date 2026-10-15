package com.awbuilds.auraspend

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.awbuilds.auraspend.data.ai.ModelDownloadManager
import com.awbuilds.auraspend.ui.applock.AppLockScreen
import com.awbuilds.auraspend.ui.designsystem.readStoredCurrencyCode
import com.awbuilds.auraspend.ui.designsystem.writeStoredCurrencyCode
import com.awbuilds.auraspend.ui.navigation.AuraSpendNavHost
import com.awbuilds.auraspend.ui.theme.AppThemeMode
import com.awbuilds.auraspend.ui.theme.AuraSpendTheme
import kotlinx.coroutines.launch

/**
 * Single-activity shell. The optional app lock wraps the whole UI here: while
 * [locked] the nav host is not even composed, and the wipe recovery for a
 * forgotten PIN reuses the same sweep the Settings action runs.
 */
class MainActivity : FragmentActivity() {

    private val locked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val app = application as AuraSpendApp
        // Cold start: a lock with a PIN always asks once.
        locked.value = app.appLockManager.shouldLock()

        setContent {
            val context = LocalContext.current
            val prefs = remember(context) {
                context.getSharedPreferences("auraspend_prefs", Context.MODE_PRIVATE)
            }
            // Tolerant parse: a corrupt or legacy value falls back to SYSTEM
            // instead of throwing IllegalArgumentException during startup.
            var themeMode by remember {
                mutableStateOf(AppThemeMode.fromName(prefs.getString("theme_mode", null)))
            }
            var dynamicColor by remember {
                mutableStateOf(
                    // Brand-first: Aurora colors by default; Material You is opt-in.
                    prefs.getBoolean("dynamic_color", false)
                )
            }
            // Null means "follow the device locale", which is what an install that
            // never opened the picker should do.
            var currencyCode by remember { mutableStateOf(readStoredCurrencyCode(prefs)) }

            AuraSpendTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                currencyCode = currencyCode
            ) {
                if (locked.value) {
                    AppLockScreen(
                        lockManager = app.appLockManager,
                        onUnlocked = { locked.value = false },
                        onForgotPin = {
                            // The honest recovery for a purely local PIN: wipe
                            // everything, including the lock itself.
                            lifecycleScope.launch {
                                app.wipeAllData()
                                app.appLockManager.clearPin()
                                locked.value = false
                            }
                        }
                    )
                } else {
                    AuraSpendNavHost(
                        repository = app.transactionRepository,
                        themeMode = themeMode,
                        onThemeChanged = { mode ->
                            themeMode = mode
                            prefs.edit().putString("theme_mode", mode.name).apply()
                        },
                        dynamicColor = dynamicColor,
                        onDynamicColorChanged = { enabled ->
                            dynamicColor = enabled
                            prefs.edit().putBoolean("dynamic_color", enabled).apply()
                        },
                        currencyCode = currencyCode,
                        onCurrencyChanged = { code ->
                            currencyCode = code
                            writeStoredCurrencyCode(prefs, code)
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after a long background stay; within the grace period (share
        // sheet, SAF picker, Drive sign-in returns) the session stays unlocked.
        val manager = (application as AuraSpendApp).appLockManager
        if (manager.shouldLock()) locked.value = true
    }

    override fun onStop() {
        (application as AuraSpendApp).appLockManager.lastBackgroundedAt = System.currentTimeMillis()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Keep model download/ready status fresh when returning to the app.
        ModelDownloadManager.sync(this)
    }
}
