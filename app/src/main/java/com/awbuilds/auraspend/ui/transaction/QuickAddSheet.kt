package com.awbuilds.auraspend.ui.transaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.core.UiError
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraChip
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraEyebrow
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.categoryColor
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import java.time.LocalDate
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
    // "Today" is decided once per sheet, not per recomposition.
    val today = remember { LocalDate.now() }

    val amount = input.toDoubleOrNull() ?: 0.0
    val canSave = amount > 0.0 && selectedCategoryId != null
    val haptics = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        // Open expanded, not at the half-height peek: the concept's sheet shows
        // amount, category and Save at once, and a partial sheet put Save below
        // the fold even though the content fits.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                .padding(bottom = AuraSpacing.section),
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

            // Amount, keypad and category scroll when the screen is short or the
            // font scale is large; Save and the two ways out stay pinned below, so
            // the fastest path (amount → category → save) never needs a scroll.
            // This is the concept's sheet order: chips sit directly under the
            // keypad, and merchant and date share one compact row beneath them.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AuraSegmentedControl(
                    options = listOf(
                        stringResource(R.string.quick_add_type_expense),
                        stringResource(R.string.quick_add_type_income)
                    ),
                    selectedIndex = if (type == TransactionType.EXPENSE) 0 else 1,
                    onSelect = {
                        type = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME
                    },
                    modifier = Modifier.widthIn(max = 200.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ── Amount display: 44sp, the concept's own size for the sheet,
                // with 14dp above and 4dp below before its caption.
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    // formatMoney, not a literal "₹0": the placeholder is money on
                    // screen and must follow the currency picked in Settings.
                    text = if (input.isEmpty()) formatMoney(0.0) else formatMoney(amount),
                    style = AuraType.amountDisplay,
                    color = if (type == TransactionType.EXPENSE) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.extendedColors.incomeAmount,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                AuraEyebrow(
                    text = if (type == TransactionType.EXPENSE) stringResource(R.string.quick_add_type_expense)
                    else stringResource(R.string.quick_add_type_income)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ── Keypad
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(".", "0", "⌫")
                )
                keys.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
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
                    if (index != keys.lastIndex) Spacer(modifier = Modifier.height(7.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Category chips: the same control as the Activity filter row
                // and the transaction editor (AuraChip), so it is learned once.
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        AuraChip(
                            label = category.name,
                            selected = category.id == selectedCategoryId,
                            dotColor = categoryColor(category.color),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedCategoryId = category.id
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AuraSpacing.md))

                // ── Merchant and date share one row; both stay optional. Both are
                // the concept's 40dp meta button, not Material fields: a text field
                // and an outlined button at their default heights made this row the
                // tallest thing in the sheet and pushed Save under the fold.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MerchantField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        hint = stringResource(R.string.quick_add_merchant_hint),
                        modifier = Modifier.weight(1f)
                    )
                    MetaButton(
                        label = if (date.toLocalDate() == today) stringResource(R.string.activity_group_today)
                        else date.format(DateTimeFormatter.ofPattern("d MMM")),
                        icon = Icons.Default.Event,
                        onClick = { showDatePicker = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.md))

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
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    // Ink, not violet: the concept has exactly one filled button
                    // and it is the darkest object on the sheet. Violet marks the
                    // things you can *navigate* to, not the thing you press last.
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                    disabledContainerColor = MaterialTheme.extendedColors.track,
                    disabledContentColor = MaterialTheme.extendedColors.faint
                )
            ) {
                Text(stringResource(R.string.action_save), style = AuraType.buttonPrimary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centred as a pair, not stretched to the edges: the concept's
            // `.sheet-foot` is `justify-content:center; gap:18px`, and two weighted
            // buttons read as a second row of actions rather than as the quiet ways
            // out underneath the one primary button.
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                SheetFootAction(
                    label = stringResource(R.string.quick_add_smart_add),
                    icon = Icons.Default.AutoAwesome,
                    onClick = onSmartAdd
                )
                SheetFootAction(
                    label = stringResource(R.string.quick_add_manual),
                    icon = null,
                    onClick = onManualAdd
                )
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
        targetValue = if (pressed) 0.96f else 1f,
        label = "keypadScale"
    )
    val background by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onBackground.copy(alpha = if (pressed) 0.11f else 0.05f),
        label = "keypadBackground"
    )

    Box(
        modifier = modifier
            // heightIn, not height: the glyph is text and must be able to grow.
            .heightIn(min = 50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label == "⌫") {
            Icon(
                Icons.AutoMirrored.Filled.Backspace,
                contentDescription = stringResource(R.string.quick_add_backspace),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                label,
                // The keypad's own numeric style: Plus Jakarta Sans has
                // old-style figures in its display cut, and a digit that drops
                // below the baseline inside a key reads as a rendering bug.
                style = AuraType.keypadDigit,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The concept's "Merchant or note" meta button: a 40dp hairline pill that is
 * also a text field.
 *
 * A Material `OutlinedTextField` carries a 56dp floor and a label slot of its
 * own, which made this row the tallest thing in the sheet and pushed Save under
 * the fold on a short screen. The 40dp pill sits in a 48dp touch target, the
 * same trick `AuraChip` uses.
 */
@Composable
private fun MerchantField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable { focusRequester.requestFocus() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    hint,
                    style = AuraType.chipLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AuraType.chipLabel.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }
}

/** The date button beside [MerchantField] — same 40dp pill, an icon and a label. */
@Composable
private fun MetaButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                style = AuraType.chipLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** "Smart add" / "Manual entry" — the sheet's two quiet ways out. */
@Composable
private fun SheetFootAction(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = AuraSpacing.sm, vertical = 6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(label, style = AuraType.sectionAction, maxLines = 1)
    }
}

