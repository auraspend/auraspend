package com.awbuilds.auraspend.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    // The ratio is deliberately NOT clamped. It used to be, and the hero then read
    // "₹33,467 of ₹26,000 — 100% used": two numbers on one card that visibly do
    // not agree, with no hint of how far over the user is. The ring's arc is the
    // only thing a full circle can cap; the text must tell the truth. AuraProgressRing
    // clamps its own sweep, so passing the raw ratio is safe.
    val budgetRatio = if (totalLimit > 0) (totalSpent / totalLimit).toFloat() else 0f
    val overBy = totalSpent - totalLimit
    val monthlySubscriptions = remember(subscriptions) {
        RecurringCost.monthlyTotal(subscriptions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.gutter),
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

        // Everything below the pinned header scrolls. The hub is taller than a
        // phone at 200% font scale (the four rows and two cards scale past the
        // viewport on a 411dp-wide device), and a plain Column clips that overflow
        // with no way to reach it.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AuraSpacing.gutter)
        ) {
            Spacer(modifier = Modifier.height(AuraSpacing.md))

            // ── Budget health hero
            AuraCard(style = AuraCardStyle.Tonal, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.plan_budgets_month),
                            style = AuraType.metricLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(AuraSpacing.xs))
                        Text(
                            stringResource(R.string.plan_budget_of, formatMoney(totalSpent), formatMoney(totalLimit)),
                            style = AuraType.moneyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AuraSpacing.xs))
                        Text(
                            when {
                                totalLimit <= 0 -> stringResource(R.string.plan_no_budgets)
                                // Over budget, "129% used" understates what happened and
                                // duplicates the ring; the number people want is how much
                                // over. Same wording as the per-budget rows downstairs.
                                overBy > 0 -> stringResource(R.string.budget_over_by, formatMoney(overBy))
                                else -> stringResource(R.string.plan_budget_percent_used, (budgetRatio * 100).toInt())
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (totalLimit > 0 && overBy > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.extendedColors.textLight
                        )
                    }
                    AuraProgressRing(
                        // Raw ratio on purpose: the ring caps its own sweep at a full
                        // circle, and the number inside must not.
                        progress = budgetRatio,
                        color = when {
                            totalLimit <= 0 -> MaterialTheme.colorScheme.primary
                            budgetRatio >= 1f -> MaterialTheme.colorScheme.error
                            budgetRatio >= 0.8f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(64.dp),
                        stroke = 6.dp
                    ) {
                        Text(
                            stringResource(R.string.common_percent, (budgetRatio * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
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
                    .size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
