package com.awbuilds.auraspend.ui.applock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.data.security.AppLockManager
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing

/**
 * The full-screen PIN gate shown while the app is locked. The nav host is not
 * composed behind it, so nothing user-owned renders until the PIN verifies.
 * "Forgot PIN" is the documented last resort: it wipes everything, including
 * the lock — there is no back door.
 */
@Composable
fun AppLockScreen(
    lockManager: AppLockManager,
    onUnlocked: () -> Unit,
    onForgotPin: () -> Unit
) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val biometricReady = remember(context) {
        runCatching {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }
    val biometricUnlockEnabled = lockManager.useBiometric && biometricReady && activity != null
    // Resolved at composition so the prompt stays configuration-aware (lint:
    // LocalContextGetResourceValueCall) and follows locale changes.
    val biometricTitle = stringResource(R.string.app_lock_title)
    val biometricCancel = stringResource(R.string.action_cancel)
    val prompt = remember(activity) {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(it),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.gutter)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(AuraSpacing.lg))
            PinDots(AppLockManager.PIN_LENGTH, entered.length)
            if (wrong) {
                Spacer(modifier = Modifier.height(AuraSpacing.sm))
                Text(
                    text = stringResource(R.string.app_lock_wrong_pin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(AuraSpacing.lg))
            PinKeypad(
                onDigit = { digit ->
                    if (entered.length < AppLockManager.PIN_LENGTH) {
                        val next = entered + digit
                        entered = next
                        if (next.length == AppLockManager.PIN_LENGTH) {
                            if (lockManager.verifyPin(next)) {
                                onUnlocked()
                            } else {
                                wrong = true
                                entered = ""
                            }
                        }
                    }
                },
                onBackspace = {
                    entered = entered.dropLast(1)
                    wrong = false
                },
                bottomLeft = {
                    if (biometricUnlockEnabled && prompt != null) {
                        IconButton(
                            onClick = {
                                prompt.authenticate(
                                    BiometricPrompt.PromptInfo.Builder()
                                        .setTitle(biometricTitle)
                                        .setNegativeButtonText(biometricCancel)
                                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                        .build()
                                )
                            },
                            modifier = Modifier.testTag("lock-biometric")
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = stringResource(R.string.app_lock_biometric),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            TextButton(onClick = { showWipeConfirm = true }, modifier = Modifier.testTag("lock-forgot")) {
                Text(
                    text = stringResource(R.string.app_lock_forgot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_delete_data_title),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = { Text(stringResource(R.string.settings_delete_data_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeConfirm = false
                        onForgotPin()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
