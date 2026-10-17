package com.awbuilds.auraspend.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraGradients
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraErrorState
import com.awbuilds.auraspend.ui.designsystem.AuraProgressRing
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.LocalCurrencyStyle
import com.awbuilds.auraspend.ui.designsystem.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Deleting a budget is irreversible and used to be one tap on a trash icon with
    // no confirmation — the same defect the review found on the Recurring screen,
    // where the fix was a dialog. Held here so the row's action only *asks*.
    var confirmRemove by remember { mutableStateOf<Budget?>(null) }
    val errorMessage = state.error?.let { stringResource(it.messageRes) }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(BudgetViewIntent.LoadBudgets)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budget_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.handleIntent(BudgetViewIntent.StartAdd) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.budget_add))
                    }
                }
            )
        }
    ) { padding ->
        // A failed save was set in the ViewModel and then never rendered, so the
        // sheet simply stopped responding. Surface it inline, keeping the list visible.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The banner is for a failure with data still on screen. A load that
            // failed with nothing to show renders AuraErrorState below instead, so
            // the reason and a retry replace the whole screen (see State.kt).
            if (state.budgets.isNotEmpty()) {
                state.error?.let { error ->
                    AuraErrorBanner(
                        message = stringResource(error.messageRes),
                        onDismiss = { viewModel.handleIntent(BudgetViewIntent.ClearError) }
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.sm))
                }
            }
            if (state.budgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (state.error != null) {
                    AuraErrorState(
                        icon = Icons.Default.CloudOff,
                        title = stringResource(R.string.error_load_failed),
                        message = stringResource(R.string.error_load_failed_detail),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = { viewModel.handleIntent(BudgetViewIntent.LoadBudgets) }
                    )
                } else {
                    AuraEmptyState(
                        icon = Icons.Default.AccountBalance,
                        title = stringResource(R.string.budget_empty_title),
                        message = stringResource(R.string.budget_empty_message),
                        actionLabel = stringResource(R.string.budget_add),
                        onAction = { viewModel.handleIntent(BudgetViewIntent.StartAdd) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = AuraSpacing.gutter,
                    vertical = AuraSpacing.lg
                ),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.md)
            ) {
                // ── Overall budget health ──────────────────────────────────────
                item {
                    val totalLimit = state.budgets.sumOf { it.limitAmount }
                    val totalSpent = state.budgets.sumOf { it.spentAmount }
                    val overall = if (totalLimit > 0) (totalSpent / totalLimit).toFloat() else 0f
                    val today = java.time.LocalDate.now()
                    val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
                    AuraCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = AuraCardStyle.Hero
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.budget_all),
                                    style = AuraType.metricLabel,
                                    color = AuraGradients.onAurora.copy(alpha = 0.82f)
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                Text(
                                    stringResource(
                                        R.string.budget_spent_of,
                                        formatMoney(totalSpent),
                                        formatMoney(totalLimit)
                                    ),
                                    style = AuraType.moneyMedium,
                                    color = AuraGradients.onAurora
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                Text(
                                    pluralStringResource(
                                        R.plurals.budget_days_left, daysLeft, daysLeft
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AuraGradients.onAurora.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(AuraSpacing.lg))
                            AuraProgressRing(
                                progress = overall.coerceIn(0f, 1f),
                                color = AuraGradients.onAurora,
                                trackColor = AuraGradients.onAurora.copy(alpha = 0.25f),
                                modifier = Modifier.size(72.dp),
                                stroke = 7.dp
                            ) {
                                Text(
                                    stringResource(R.string.common_percent, (overall.coerceIn(0f, 1f) * 100).toInt()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AuraGradients.onAurora
                                )
                            }
                        }
                    }
                }

                items(state.budgets) { budget ->
                    val category = state.categories.find { it.id == budget.categoryId }
                    val progress = if (budget.limitAmount > 0) (budget.spentAmount / budget.limitAmount).toFloat().coerceIn(0f, 1f) else 0f
                    val overBy = budget.spentAmount - budget.limitAmount
                    val progressColor = when {
                        progress >= 1f -> MaterialTheme.colorScheme.error
                        progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val categoryColor = category?.let { Color(it.color.toLong()) }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant

                    AuraCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = AuraCardStyle.Outlined
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor)
                            )
                            Spacer(modifier = Modifier.width(AuraSpacing.sm))
                            Text(
                                category?.name ?: stringResource(R.string.budget_unknown_category),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { confirmRemove = budget }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AuraSpacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AuraProgressRing(
                                progress = progress,
                                color = progressColor,
                                modifier = Modifier.size(64.dp),
                                stroke = 6.dp
                            ) {
                                Text(
                                    stringResource(R.string.common_percent, (progress * 100).toInt()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(AuraSpacing.lg))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.budget_spent, formatMoney(budget.spentAmount)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                if (overBy > 0) {
                                    Text(
                                        stringResource(R.string.budget_over_by, formatMoney(overBy)),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.budget_limit, formatMoney(budget.limitAmount)),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(AuraSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(budget.period.labelRes()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (progress >= 1f) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(AuraSpacing.xs))
                                    Text(
                                        stringResource(R.string.budget_limit_reached),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else if (progress >= 0.8f) {
                                Text(
                                    stringResource(R.string.budget_percent_used, (progress * 100).toInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                // The row is SpaceBetween, so without this the
                                // right half was dead space on every healthy budget.
                                // Headroom is the number people actually want here.
                                Text(
                                    stringResource(
                                        R.string.budget_remaining,
                                        formatMoney(budget.limitAmount - budget.spentAmount)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (state.isAdding || state.editingBudget != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.handleIntent(BudgetViewIntent.CancelEdit) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.xxl)
                    .padding(bottom = AuraSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.xl)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            if (state.editingBudget != null) stringResource(R.string.budget_edit_title)
                            else stringResource(R.string.budget_new_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.budget_editor_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
                    Text(
                        stringResource(R.string.budget_category),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.categories.isEmpty()) {
                        Text(
                            stringResource(R.string.budget_no_categories),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.categories.take(6).forEach { cat ->
                            val catColor = Color(cat.color.toLong())
                            FilterChip(
                                selected = state.selectedCategoryId == cat.id,
                                onClick = { viewModel.handleIntent(BudgetViewIntent.SelectCategory(cat.id)) },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(catColor)
                                        )
                                        Text(cat.name, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.limitAmount,
                    onValueChange = { viewModel.handleIntent(BudgetViewIntent.AmountChanged(it)) },
                    label = { Text(stringResource(R.string.budget_monthly_limit)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    // Theme currency, not a hardcoded ₹; the field holds raw digits,
                    // so the prefix cannot double a symbol. The decorative
                    // `Icons.Default.CurrencyRupee` that used to sit next to it said
                    // "rupees" in every locale and is gone with it.
                    prefix = { Text(LocalCurrencyStyle.current.symbol) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
                    Text(
                        stringResource(R.string.budget_period),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                    ) {
                        BudgetPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = state.selectedPeriod == period,
                                onClick = { viewModel.handleIntent(BudgetViewIntent.PeriodChanged(period)) },
                                label = {
                                    Text(
                                        stringResource(period.labelRes()),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.handleIntent(BudgetViewIntent.CancelEdit) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = { viewModel.handleIntent(BudgetViewIntent.SaveBudget) },
                        modifier = Modifier.weight(1f),
                        enabled = state.selectedCategoryId.isNotBlank() && state.limitAmount.toDoubleOrNull() != null
                    ) {
                        Text(stringResource(R.string.budget_save))
                    }
                }
            }
        }

    confirmRemove?.let { budget ->
        val category = state.categories.firstOrNull { it.id == budget.categoryId }
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = {
                Text(
                    stringResource(
                        R.string.budget_delete_title,
                        category?.name ?: stringResource(R.string.budget_unknown_category)
                    ),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.budget_delete_message))
                    // A failed delete keeps the dialog open so the user can retry,
                    // and says so instead of vanishing as if the row were removed.
                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(AuraSpacing.md))
                        AuraErrorBanner(message = message)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(BudgetViewIntent.DeleteBudget(budget.id))
                        confirmRemove = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
    }
}

/**
 * A budget period as copy.
 *
 * `BudgetPeriod` is a domain enum, so `period.name.lowercase()` put the identifier
 * on screen in every locale. Deliberately exhaustive — an `else` arm would let a
 * period added later render a raw identifier instead of failing the build here.
 */
private fun BudgetPeriod.labelRes(): Int = when (this) {
    BudgetPeriod.WEEKLY -> R.string.budget_period_weekly
    BudgetPeriod.MONTHLY -> R.string.budget_period_monthly
    BudgetPeriod.YEARLY -> R.string.budget_period_yearly
}
