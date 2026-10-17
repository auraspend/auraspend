package com.awbuilds.auraspend.ui.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing

private val KeySize = 72.dp

/** The N-digit progress dots: filled up to [filled], outline after. */
@Composable
fun PinDots(length: Int, filled: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md), modifier = modifier) {
        repeat(length) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

/**
 * The shared 0-9 PIN pad (lock screen + setup dialogs). [bottomLeft] slots a
 * biometric button or stays empty; every key is a ≥72dp circular target.
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    bottomLeft: @Composable () -> Unit = {}
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AuraSpacing.xs)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.xl)) {
                row.forEach { digit -> PinDigit(digit, onDigit) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(KeySize)) { bottomLeft() }
            PinDigit(0, onDigit)
            IconButton(
                onClick = onBackspace,
                modifier = Modifier.size(KeySize).testTag("lock-backspace")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.app_lock_backspace),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun PinDigit(digit: Int, onDigit: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .clickable { onDigit(digit) }
            .testTag("lock-digit-$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$digit",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
