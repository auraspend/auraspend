package com.awbuilds.auraspend.ui.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.domain.model.SavingsGoal
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.core.UiError
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraGradients
import com.awbuilds.auraspend.ui.designsystem.AuraProgressRing
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.AnimatedMoney
import com.awbuilds.auraspend.ui.designsystem.LocalCurrencyStyle
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Savings goals — the forward-looking half of the Plan hub. Progress rings
 * animate, milestones get a check badge, and adding funds is two taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(
    repository: TransactionRepository,
    onBack: () -> Unit
) {
    val goals by repository.getAllSavingsGoals().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editorGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var addFundsGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deleteGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    // A write that failed. The editor sheet, the add-funds dialog and the delete
    // confirmation all stay open on failure, so the failure is rendered inside the
    // surface that raised it — where the user is actually looking — rather than
    // leaving a dismissed sheet that silently dropped the edit.
    var error by remember { mutableStateOf<UiError?>(null) }
    val errorMessage = error?.let { stringResource(it.messageRes) }

    val totalSaved = remember(goals) { goals.sumOf { it.currentAmount } }
    val totalTarget = remember(goals) { goals.sumOf { it.targetAmount } }
    val overallProgress = if (totalTarget > 0) (totalSaved / totalTarget).toFloat().coerceIn(0f, 1f) else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.savings_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        error = null
                        editorGoal = null
                        showEditor = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.savings_add_goal))
                    }
                }
            )
        }
    ) { padding ->
        if (goals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                AuraEmptyState(
                    icon = Icons.Default.Savings,
                    title = stringResource(R.string.savings_empty_title),
                    message = stringResource(R.string.savings_empty_message),
                    actionLabel = stringResource(R.string.savings_create_goal),
                    onAction = {
                        error = null
                        editorGoal = null
                        showEditor = true
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    start = AuraSpacing.gutter,
                    end = AuraSpacing.gutter,
                    bottom = AuraSpacing.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.md)
            ) {
                item {
                    AuraCard(style = AuraCardStyle.Hero, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.savings_total_saved),
                                    style = AuraType.metricLabel,
                                    color = AuraGradients.onAurora.copy(alpha = 0.82f)
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                AnimatedMoney(
                                    amount = totalSaved,
                                    style = AuraType.moneyLarge,
                                    color = AuraGradients.onAurora
                                )
                                Text(
                                    stringResource(R.string.savings_of, formatMoney(totalTarget)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AuraGradients.onAurora.copy(alpha = 0.8f)
                                )
                            }
                            AuraProgressRing(
                                progress = overallProgress,
                                color = AuraGradients.onAurora,
                                trackColor = AuraGradients.onAurora.copy(alpha = 0.25f),
                                modifier = Modifier.size(72.dp),
                                stroke = 7.dp,
                                contentDescription = stringResource(
                                    R.string.savings_total_desc,
                                    (overallProgress * 100).toInt()
                                )
                            ) {
                                Text(
                                    stringResource(R.string.common_percent, (overallProgress * 100).toInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AuraGradients.onAurora
                                )
                            }
                        }
                    }
                }

                items(goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        onAddFunds = {
                            error = null
                            addFundsGoal = goal
                        },
                        onEdit = {
                            error = null
                            editorGoal = goal
                            showEditor = true
                        },
                        onDelete = {
                            error = null
                            deleteGoal = goal
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    if (showEditor) {
        GoalEditorSheet(
            goal = editorGoal,
            errorMessage = errorMessage,
            onSave = { goal ->
                scope.launch {
                    // A failed write keeps the sheet (and the form) on screen; the
                    // banner inside it says why, instead of the sheet closing as if
                    // the goal had been saved.
                    val saved = boundary(TAG, false) {
                        repository.saveSavingsGoal(goal)
                        true
                    }
                    if (saved) {
                        error = null
                        showEditor = false
                    } else {
                        error = UiError.SAVE_FAILED
                    }
                }
            },
            onDismiss = { showEditor = false }
        )
    }

    addFundsGoal?.let { goal ->
        AddFundsDialog(
            goal = goal,
            errorMessage = errorMessage,
            onAdd = { amount ->
                scope.launch {
                    val saved = boundary(TAG, false) {
                        repository.saveSavingsGoal(goal.copy(currentAmount = goal.currentAmount + amount))
                        true
                    }
                    if (saved) {
                        error = null
                        addFundsGoal = null
                    } else {
                        error = UiError.SAVE_FAILED
                    }
                }
            },
            onDismiss = { addFundsGoal = null }
        )
    }

    deleteGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleteGoal = null },
            title = { Text(stringResource(R.string.savings_delete_title, goal.name)) },
            text = {
                Column {
                    Text(stringResource(R.string.savings_delete_message))
                    // Deleting failed: the dialog stays open so the user can retry,
                    // and says so rather than vanishing as if the goal were gone.
                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(AuraSpacing.md))
                        AuraErrorBanner(message = message, onDismiss = { error = null })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val deleted = boundary(TAG, false) {
                                repository.deleteSavingsGoal(goal.id)
                                true
                            }
                            if (deleted) {
                                error = null
                                deleteGoal = null
                            } else {
                                error = UiError.DELETE_FAILED
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteGoal = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/** Log tag for this screen's IO boundaries. */
private const val TAG = "SavingsGoalsScreen"

@Composable
private fun GoalCard(
    goal: SavingsGoal,
    onAddFunds: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat() else 0f
    val complete = progress >= 1f
    val deadlineLabel = remember(goal.deadline) {
        goal.deadline?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
    }

    AuraCard(style = AuraCardStyle.Outlined, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuraProgressRing(
                progress = progress,
                color = if (complete) MaterialTheme.extendedColors.incomeAmount else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
                stroke = 5.dp,
                contentDescription = stringResource(
                    R.string.savings_goal_desc,
                    goal.name,
                    (progress.coerceIn(0f, 1f) * 100).toInt()
                )
            ) {
                if (complete) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.incomeAmount,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        stringResource(R.string.common_percent, (progress.coerceIn(0f, 1f) * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.width(AuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (complete) {
                        Spacer(modifier = Modifier.width(AuraSpacing.sm))
                        Text(
                            stringResource(R.string.savings_reached),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.extendedColors.incomeAmount,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.extendedColors.incomeAmount.copy(alpha = 0.14f),
                                    CircleShape
                                )
                                .padding(horizontal = AuraSpacing.sm, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AuraSpacing.xxs))
                Text(
                    stringResource(
                        R.string.savings_progress_of,
                        formatMoney(goal.currentAmount),
                        formatMoney(goal.targetAmount)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                deadlineLabel?.let {
                    Text(
                        stringResource(R.string.savings_target_date, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.extendedColors.textLight
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(AuraSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
        ) {
            // A reached goal has nothing left to fund; offering the button invited
            // the user to add money to a goal that is already complete.
            if (!complete) {
                Button(
                    onClick = onAddFunds,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(AuraSpacing.xs))
                    Text(stringResource(R.string.savings_add_funds))
                }
            } else {
                // Keep the edit/delete actions right-aligned instead of leaving
                // them stranded at the start of an otherwise empty row.
                Spacer(modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.savings_edit_goal))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.savings_delete_goal),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorSheet(
    goal: SavingsGoal?,
    /** A failed write, rendered inside the sheet so the form stays on screen. */
    errorMessage: String?,
    onSave: (SavingsGoal) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(goal) { mutableStateOf(goal?.name ?: "") }
    var target by remember(goal) { mutableStateOf(goal?.targetAmount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var deadlineMillis by remember(goal) { mutableStateOf(goal?.deadline) }
    var showDatePicker by remember { mutableStateOf(false) }

    val targetValue = target.toDoubleOrNull()
    val valid = name.isNotBlank() && targetValue != null && targetValue > 0
    val targetDateLabel = deadlineMillis?.let {
        stringResource(
            R.string.savings_target_date,
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        )
    } ?: stringResource(R.string.savings_target_date_hint)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.gutter)
                .padding(bottom = AuraSpacing.xxl)
        ) {
            Text(
                if (goal == null) stringResource(R.string.savings_new_goal_title)
                else stringResource(R.string.savings_edit_goal),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                // No onDismiss: clearing the failure while the sheet is open would
                // leave the user with an unexplained form. It clears on retry.
                AuraErrorBanner(message = message)
            }
            Spacer(modifier = Modifier.height(AuraSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.savings_goal_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text(stringResource(R.string.savings_target_amount)) },
                // Theme currency, not a hardcoded ₹: the field holds raw digits, so
                // the prefix cannot double a symbol on already-formatted output.
                prefix = { Text(LocalCurrencyStyle.current.symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(targetDateLabel)
            }
            if (deadlineMillis != null) {
                TextButton(
                    onClick = { deadlineMillis = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(stringResource(R.string.savings_remove_date)) }
            }
            Spacer(modifier = Modifier.height(AuraSpacing.lg))
            Button(
                onClick = {
                    onSave(
                        (goal ?: SavingsGoal(name = "", targetAmount = 0.0)).copy(
                            name = name.trim(),
                            targetAmount = targetValue ?: 0.0,
                            deadline = deadlineMillis
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
                    if (goal == null) stringResource(R.string.savings_create_goal)
                    else stringResource(R.string.savings_save_changes),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = deadlineMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { deadlineMillis = it }
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

@Composable
private fun AddFundsDialog(
    goal: SavingsGoal,
    /** A failed write, rendered inside the dialog so the typed amount survives. */
    errorMessage: String?,
    onAdd: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val parsed = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savings_add_funds_title, goal.name)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.savings_progress_of,
                        formatMoney(goal.currentAmount),
                        formatMoney(goal.targetAmount)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.savings_amount)) },
                    // Theme currency, not a hardcoded ₹: the field holds raw digits.
                    prefix = { Text(LocalCurrencyStyle.current.symbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(AuraSpacing.md))
                    AuraErrorBanner(message = message)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onAdd) },
                enabled = parsed != null && parsed > 0
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
