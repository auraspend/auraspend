package com.awbuilds.auraspend.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.domain.model.BudgetSpending
import com.awbuilds.auraspend.domain.model.RecurrenceFrequency
import com.awbuilds.auraspend.domain.model.RecurringCost
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraGradients
import com.awbuilds.auraspend.ui.designsystem.AuraProgressRing
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.SettingsAvatarButton
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.LocalDateTime

/**
 * Plan hub — the home for everything forward-looking: budgets, subscriptions
 * and (from P3) savings goals.
 */
@Composable
fun PlanHubScreen(
    repository: TransactionRepository,
    onOpenBudgets: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val storedBudgets by repository.getAllBudgets().collectAsState(initial = emptyList())
    val subscriptions by repository.getActiveSubscriptions().collectAsState(initial = emptyList())
    val goals by repository.getAllSavingsGoals().collectAsState(initial = emptyList())

    // `spentAmount` is a denormalised column that nothing ever writes back — the
    // Budget row stores 0.0 at creation and only BudgetViewModel recomputes it in
    // memory. Reading the stored column here made this hub report "₹0 of ₹26,000,
    // 0% used" while the Dashboard, using the same budgets, showed real spend.
    //
    // Spend now comes from the indexed per-category SQL aggregate — the same one
    // the Dashboard reads — instead of loading every transaction row and scanning
    // it here (ADR 0003). Budget, Plan and the Dashboard derive the number from
    // `observeExpenseByCategory` over the same `BudgetSpending.periodEpochRange`,
    // so the three screens cannot disagree about it.
    val now = remember { LocalDateTime.now() }
    val weeklyRange = remember(now) { BudgetSpending.periodEpochRange(BudgetPeriod.WEEKLY, now) }
    val monthlyRange = remember(now) { BudgetSpending.periodEpochRange(BudgetPeriod.MONTHLY, now) }
    val yearlyRange = remember(now) { BudgetSpending.periodEpochRange(BudgetPeriod.YEARLY, now) }
    // The flows are remembered so `collectAsState` keys its effect on a stable
    // instance: the repository hands back a fresh cold flow per call, and a new one
    // on every recomposition would re-run each aggregate query each time.
    val weeklyFlow = remember(weeklyRange) {
        repository.observeExpenseByCategory(weeklyRange.first, weeklyRange.second)
    }
    val monthlyFlow = remember(monthlyRange) {
        repository.observeExpenseByCategory(monthlyRange.first, monthlyRange.second)
    }
    val yearlyFlow = remember(yearlyRange) {
        repository.observeExpenseByCategory(yearlyRange.first, yearlyRange.second)
    }
    val weeklyTotals by weeklyFlow.collectAsState(initial = emptyList())
    val monthlyTotals by monthlyFlow.collectAsState(initial = emptyList())
    val yearlyTotals by yearlyFlow.collectAsState(initial = emptyList())

    val budgets = remember(storedBudgets, weeklyTotals, monthlyTotals, yearlyTotals) {
        val weekly = weeklyTotals.associate { it.categoryId to it.amount }
        val monthly = monthlyTotals.associate { it.categoryId to it.amount }
        val yearly = yearlyTotals.associate { it.categoryId to it.amount }
        storedBudgets.map { budget ->
            val totals = when (budget.period) {
                BudgetPeriod.WEEKLY -> weekly
                BudgetPeriod.MONTHLY -> monthly
                BudgetPeriod.YEARLY -> yearly
            }
            budget.copy(spentAmount = totals[budget.categoryId] ?: 0.0)
        }
    }

    val totalLimit = remember(budgets) { budgets.sumOf { it.limitAmount } }
    val totalSpent = remember(budgets) { budgets.sumOf { it.spentAmount } }
    val budgetProgress = if (totalLimit > 0) (totalSpent / totalLimit).toFloat().coerceIn(0f, 1f) else 0f
    val monthlySubscriptions = remember(subscriptions) {
        RecurringCost.monthlyTotal(subscriptions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = AuraSpacing.gutter)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.plan_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = AuraSpacing.sm, bottom = AuraSpacing.xs)
                    .semantics { heading() }
            )
            SettingsAvatarButton(onClick = onOpenSettings)
        }

        Spacer(modifier = Modifier.height(AuraSpacing.md))

        // ── Budget health hero
        AuraCard(style = AuraCardStyle.Hero, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.plan_budgets_month),
                        style = AuraType.metricLabel,
                        color = AuraGradients.onAurora.copy(alpha = 0.82f)
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.xs))
                    Text(
                        stringResource(R.string.plan_budget_of, formatMoney(totalSpent), formatMoney(totalLimit)),
                        style = AuraType.moneyMedium,
                        color = AuraGradients.onAurora
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.xs))
                    Text(
                        if (totalLimit > 0) stringResource(R.string.plan_budget_percent_used, (budgetProgress * 100).toInt())
                        else stringResource(R.string.plan_no_budgets),
                        style = MaterialTheme.typography.bodySmall,
                        color = AuraGradients.onAurora.copy(alpha = 0.8f)
                    )
                }
                AuraProgressRing(
                    progress = budgetProgress,
                    color = AuraGradients.onAurora,
                    trackColor = AuraGradients.onAurora.copy(alpha = 0.25f),
                    modifier = Modifier.size(64.dp),
                    stroke = 6.dp
                ) {
                    Text(
                        stringResource(R.string.common_percent, (budgetProgress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AuraGradients.onAurora
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.lg))

        // ── Forward-looking totals
        AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.plan_recurring_monthly),
                style = AuraType.metricLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AuraSpacing.xs))
            Text(
                stringResource(
                    R.string.plan_monthly_recurring,
                    formatMoney(monthlySubscriptions, fractionDigits = 0)
                ),
                style = AuraType.moneyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(AuraSpacing.xs))
            Text(
                pluralStringResource(R.plurals.plan_active_subscriptions, subscriptions.size, subscriptions.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textLight
            )
        }

        Spacer(modifier = Modifier.height(AuraSpacing.lg))

        // ── Management entries
        PlanRow(
            icon = Icons.Default.AccountBalance,
            title = stringResource(R.string.plan_budgets),
            subtitle = stringResource(R.string.plan_budgets_subtitle),
            onClick = onOpenBudgets
        )
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        PlanRow(
            icon = Icons.Default.Subscriptions,
            title = stringResource(R.string.plan_subscriptions),
            subtitle = stringResource(R.string.plan_subscriptions_subtitle),
            onClick = onOpenSubscriptions
        )
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        PlanRow(
            icon = Icons.Default.Savings,
            title = stringResource(R.string.plan_savings_goals),
            subtitle = if (goals.isEmpty()) {
                stringResource(R.string.plan_savings_goals_empty_subtitle)
            } else {
                pluralStringResource(
                    R.plurals.plan_goals_saved,
                    goals.size,
                    goals.size,
                    formatMoney(goals.sumOf { it.currentAmount })
                )
            },
            onClick = onOpenGoals
        )
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        PlanRow(
            icon = Icons.Default.Category,
            title = stringResource(R.string.plan_categories),
            subtitle = stringResource(R.string.plan_categories_subtitle),
            onClick = onOpenCategories
        )

        Spacer(modifier = Modifier.height(AuraSpacing.xxl))
    }
}

@Composable
private fun PlanRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    AuraCard(
        style = AuraCardStyle.Outlined,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = AuraSpacing.lg, vertical = AuraSpacing.lg),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(AuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textLight
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
