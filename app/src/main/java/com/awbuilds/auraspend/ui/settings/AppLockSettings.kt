package com.awbuilds.auraspend.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.data.security.AppLockManager
import com.awbuilds.auraspend.ui.applock.PinDots
import com.awbuilds.auraspend.ui.applock.PinKeypad
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing

/** What a successful PIN verification unlocks next. */
enum class PinVerifyPurpose { DISABLE, CHANGE }

/**
 * Two-step "set a PIN" dialog: enter once, confirm once. On a mismatch the
 * whole pair restarts — a half-remembered first entry is never kept.
 */
@Composable
fun PinCreateDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (confirming) R.string.app_lock_confirm_title else R.string.app_lock_create_title
                ),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!confirming) {
                    Text(
                        text = stringResource(R.string.app_lock_create_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.sm))
                }
                if (mismatch) {
                    Text(
                        text = stringResource(R.string.app_lock_confirm_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.sm))
                }
                PinDots(AppLockManager.PIN_LENGTH, entry.length)
                Spacer(modifier = Modifier.height(AuraSpacing.sm))
                PinKeypad(
                    onDigit = { digit ->
                        if (entry.length < AppLockManager.PIN_LENGTH) {
                            val next = entry + digit
                            entry = next
                            if (next.length == AppLockManager.PIN_LENGTH) {
                                if (!confirming) {
                                    first = next
                                    entry = ""
                                    confirming = true
                                } else if (next == first) {
                                    onDone(next)
                                } else {
                                    mismatch = true
                                    first = ""
                                    entry = ""
                                    confirming = false
                                }
                            }
                        }
                    },
                    onBackspace = {
                        entry = entry.dropLast(1)
                        mismatch = false
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** "Enter your current PIN" — gates disabling or changing the lock. */
@Composable
fun PinVerifyDialog(
    verify: (String) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var entry by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.app_lock_verify_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (failed) {
                    Text(
                        text = stringResource(R.string.app_lock_wrong_pin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.sm))
                }
                PinDots(AppLockManager.PIN_LENGTH, entry.length)
                Spacer(modifier = Modifier.height(AuraSpacing.sm))
                PinKeypad(
                    onDigit = { digit ->
                        if (entry.length < AppLockManager.PIN_LENGTH) {
                            val next = entry + digit
                            entry = next
                            if (next.length == AppLockManager.PIN_LENGTH) {
                                if (verify(next)) onVerified() else {
                                    failed = true
                                    entry = ""
                                }
                            }
                        }
                    },
                    onBackspace = {
                        entry = entry.dropLast(1)
                        failed = false
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
