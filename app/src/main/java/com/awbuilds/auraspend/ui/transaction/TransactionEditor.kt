package com.awbuilds.auraspend.ui.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.designsystem.AuraChip
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.LocalCurrencyStyle
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Full transaction editor used for new entries and edits.
 *
 * Categories are laid out with [FlowRow] (no nested scrolling grids) so the
 * editor stays smooth regardless of the number of categories.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditor(
    transaction: Transaction?,
    categories: List<Category>,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by remember(transaction) { mutableStateOf(transaction?.amount?.let { formatAmountInput(it) } ?: "") }
    var merchant by remember(transaction) { mutableStateOf(transaction?.merchant ?: "") }
    var note by remember(transaction) { mutableStateOf(transaction?.note ?: "") }
    var type by remember(transaction) { mutableStateOf(transaction?.type ?: TransactionType.EXPENSE) }
    var categoryId by remember(transaction) { mutableStateOf(transaction?.categoryId) }
    var date by remember(transaction) { mutableStateOf(transaction?.date ?: LocalDateTime.now()) }
    var isRecurring by remember(transaction) { mutableStateOf(transaction?.isRecurring ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val parsedAmount = amount.toDoubleOrNull()
    val valid = parsedAmount != null && parsedAmount > 0.0 && categoryId != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuraSpacing.gutter)
            .padding(bottom = AuraSpacing.xxl)
    ) {
        Spacer(modifier = Modifier.height(AuraSpacing.md))

        AuraSegmentedControl(
            options = listOf(
                stringResource(R.string.txn_editor_type_expense),
                stringResource(R.string.txn_editor_type_income)
            ),
            selectedIndex = if (type == TransactionType.EXPENSE) 0 else 1,
            onSelect = { type = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME }
        )

        Spacer(modifier = Modifier.height(AuraSpacing.lg))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(stringResource(R.string.txn_editor_amount)) },
            // Theme currency, not a hardcoded ₹: the field holds raw digits, so the
            // prefix cannot double a symbol on already-formatted output.
            prefix = { Text(LocalCurrencyStyle.current.symbol) },
            singleLine = true,
            isError = amount.isNotBlank() && parsedAmount == null,
            supportingText = if (amount.isNotBlank() && parsedAmount == null) {
                { Text(stringResource(R.string.txn_editor_amount_invalid)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AuraSpacing.md))

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text(stringResource(R.string.txn_editor_merchant)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AuraSpacing.md))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.txn_editor_note)) },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AuraSpacing.md))

        OutlinedButton(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(AuraSpacing.sm))
            Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a")))
        }

        Spacer(modifier = Modifier.height(AuraSpacing.lg))

        Text(
            stringResource(R.string.txn_editor_category),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(AuraSpacing.sm))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
        ) {
            categories.forEach { category ->
                val selected = category.id == categoryId
                AuraChip(
                    label = category.name,
                    selected = selected,
                    dotColor = androidx.compose.ui.graphics.Color(category.color.toLong()),
                    onClick = { categoryId = category.id }
                )
            }
        }

        // A manual entry had no way to say "this repeats" — only the detector
        // could set the flag. Rent, SIPs and hand-added subscriptions need it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.txn_editor_recurring),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.txn_editor_recurring_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
        }

        Spacer(modifier = Modifier.height(AuraSpacing.xxl))

        Button(
            onClick = {
                val id = transaction?.id ?: UUID.randomUUID().toString()
                onSave(
                    Transaction(
                        id = id,
                        amount = parsedAmount ?: return@Button,
                        categoryId = categoryId ?: return@Button,
                        note = note,
                        merchant = merchant.ifBlank { null },
                        bankName = transaction?.bankName,
                        date = date,
                        type = type,
                        isRecurring = isRecurring,
                        recurrenceFrequency = transaction?.recurrenceFrequency,
                        nextDueDate = transaction?.nextDueDate,
                        subscriptionName = transaction?.subscriptionName,
                        sourceSmsId = transaction?.sourceSmsId
                    )
                )
            },
            enabled = valid,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            Text(
                if (isNew) stringResource(R.string.txn_editor_save_new)
                else stringResource(R.string.txn_editor_save_changes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(AuraSpacing.sm))

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(millis),
                                ZoneId.systemDefault()
                            )
                            date = date.withYear(picked.year).withMonth(picked.monthValue).withDayOfMonth(picked.dayOfMonth)
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** "1234.5" → "1234.5" (trailing zero trimmed) for the amount field. */
private fun formatAmountInput(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
