package com.awbuilds.auraspend

import android.content.Context
import android.os.Bundle
import com.awbuilds.auraspend.data.ai.ModelDownloadManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.awbuilds.auraspend.ui.designsystem.readStoredCurrencyCode
import com.awbuilds.auraspend.ui.designsystem.writeStoredCurrencyCode
import com.awbuilds.auraspend.ui.navigation.AuraSpendNavHost
import com.awbuilds.auraspend.ui.theme.AppThemeMode
import com.awbuilds.auraspend.ui.theme.AuraSpendTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val app = application as AuraSpendApp

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

    override fun onResume() {
        super.onResume()
        // Keep model download/ready status fresh when returning to the app.
        ModelDownloadManager.sync(this)
    }
}
