package com.awbuilds.auraspend.ui.transaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.core.UiError
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.CategoryAvatar
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Amount-first quick capture.
 *
 * Type the number, tap a category, save. The keypad, chips and CTA all carry
 * press feedback and haptics; Smart Add / Manual stay one tap away for the
 * cases that need more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    categories: List<Category>,
    initialType: TransactionType = TransactionType.EXPENSE,
    onSave: (
        amount: Double,
        categoryId: String,
        type: TransactionType,
        merchant: String?,
        note: String,
        date: LocalDateTime
    ) -> Unit,
    onSmartAdd: () -> Unit,
    onManualAdd: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * A write that failed while this sheet was open. The sheet is *not* dismissed
     * on failure, so the typed amount and merchant survive and the banner shows
     * the user why nothing was saved.
     */
    error: UiError? = null,
    onErrorDismissed: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var merchant by remember { mutableStateOf("") }
    var selectedCategoryId by remember(categories, type) {
        mutableStateOf(defaultCategoryFor(categories, type))
    }
    var date by remember { mutableStateOf(LocalDateTime.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = input.toDoubleOrNull() ?: 0.0
    val canSave = amount > 0.0 && selectedCategoryId != null
    val haptics = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        // The sheet grows taller than the usable height once the keypad is in it,
        // so the default insets left the "Merchant (optional)" field underneath
        // the system navigation bar and unreachable. Found on-device
        // 2026-09-29: the field was half-hidden and untappable.
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.gutter)
                .padding(bottom = AuraSpacing.xxl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // A failed save is reported inside the sheet — where the user is
            // looking — instead of dismissing it as if the write had succeeded.
            error?.let { failure ->
                AuraErrorBanner(
                    message = stringResource(failure.messageRes),
                    onDismiss = onErrorDismissed
                )
                Spacer(modifier = Modifier.height(AuraSpacing.md))
            }

            AuraSegmentedControl(
                options = listOf(
                    stringResource(R.string.quick_add_type_expense),
                    stringResource(R.string.quick_add_type_income)
                ),
                selectedIndex = if (type == TransactionType.EXPENSE) 0 else 1,
                onSelect = {
                    type = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME
                }
            )

            Spacer(modifier = Modifier.height(AuraSpacing.lg))

            // ── Amount display
            Text(
                // formatMoney, not a literal "₹0": the placeholder is money on
                // screen and must follow the currency picked in Settings.
                text = if (input.isEmpty()) formatMoney(0.0) else formatMoney(amount),
                style = AuraType.moneyHero,
                color = if (type == TransactionType.EXPENSE) MaterialTheme.extendedColors.expenseAmount
                else MaterialTheme.extendedColors.incomeAmount,
                maxLines = 1
            )
            Text(
                text = if (type == TransactionType.EXPENSE) stringResource(R.string.quick_add_type_expense)
                else stringResource(R.string.quick_add_type_income),
                style = AuraType.metricLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(AuraSpacing.lg))

            // ── Keypad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(".", "0", "⌫")
            )
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                ) {
                    row.forEach { key ->
                        KeypadKey(
                            label = key,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                input = when (key) {
                                    "⌫" -> input.dropLast(1)
                                    "." -> if (input.contains('.')) input else if (input.isEmpty()) "0." else "$input."
                                    else -> if (input == "0") key else input + key
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AuraSpacing.sm))
            }

            Spacer(modifier = Modifier.height(AuraSpacing.xs))

            // ── Merchant (optional, compact)
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                placeholder = { Text(stringResource(R.string.quick_add_merchant_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AuraSpacing.md))

            // ── Category chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryChip(
                        category = category,
                        selected = category.id == selectedCategoryId,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedCategoryId = category.id
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.sm))

            // "Now" stays the default; the chip is for cash spent earlier.
            TextButton(onClick = { showDatePicker = true }) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(AuraSpacing.xs))
                Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
            }

            Spacer(modifier = Modifier.height(AuraSpacing.sm))

            Button(
                onClick = {
                    val categoryId = selectedCategoryId ?: return@Button
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSave(amount, categoryId, type, merchant.ifBlank { null }, "", date)
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(AuraSpacing.xs))

            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onSmartAdd) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(AuraSpacing.xs))
                    Text(stringResource(R.string.quick_add_smart_add))
                }
                TextButton(onClick = onManualAdd) {
                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(AuraSpacing.xs))
                    Text(stringResource(R.string.quick_add_manual))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(millis),
                                java.time.ZoneId.systemDefault()
                            )
                            // Keep the time of day; the picker chooses a day.
                            date = date.withYear(picked.year)
                                .withMonth(picked.monthValue)
                                .withDayOfMonth(picked.dayOfMonth)
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Most-used defaults: Food for spending, Salary for income. */
private fun defaultCategoryFor(categories: List<Category>, type: TransactionType): String? {
    val preferredId = if (type == TransactionType.EXPENSE) "cat_food" else "cat_salary"
    return categories.firstOrNull { it.id == preferredId }?.id ?: categories.firstOrNull()?.id
}

@Composable
private fun KeypadKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "keypadScale"
    )
    val background by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "keypadBackground"
    )

    Box(
        modifier = modifier
            .heightIn(min = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label == "⌫") {
            Icon(
                Icons.AutoMirrored.Filled.Backspace,
                contentDescription = stringResource(R.string.quick_add_backspace),
                tint = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                label,
                style = AuraType.keypadDigit,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "chipContainer"
    )
    AuraCard(
        modifier = Modifier.width(96.dp),
        style = if (selected) AuraCardStyle.Tonal else AuraCardStyle.Filled,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(0.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(container)
                .padding(vertical = AuraSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CategoryAvatar(
                icon = category.icon,
                color = androidx.compose.ui.graphics.Color(category.color.toLong()),
                size = 32.dp
            )
            Spacer(modifier = Modifier.height(AuraSpacing.xs))
            Text(
                category.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
