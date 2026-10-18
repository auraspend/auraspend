package com.awbuilds.auraspend.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.domain.model.BudgetSpending
import com.awbuilds.auraspend.domain.model.RecurringCost
import com.awbuilds.auraspend.domain.model.SavingsGoal
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEyebrow
import com.awbuilds.auraspend.ui.designsystem.AuraProgressBar
import com.awbuilds.auraspend.ui.designsystem.AuraSectionHeader
import com.awbuilds.auraspend.ui.designsystem.AuraSectionLink
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.MoneyConfig
import com.awbuilds.auraspend.ui.designsystem.SettingsAvatarButton
import androidx.compose.ui.platform.LocalContext
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import androidx.compose.ui.platform.LocalContext
import com.awbuilds.auraspend.ui.designsystem.formatMoneyCompact
import com.awbuilds.auraspend.ui.designsystem.rememberMonthName
import com.awbuilds.auraspend.ui.recurring.SuggestionDismissals
import com.awbuilds.auraspend.ui.recurring.detectedSubscriptions
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Plan hub — the home for everything forward-looking: budgets, subscriptions
 * and savings goals.
 *
 * Forward-looking money is checked weekly, not hourly, which is why this is a tab
 * of its own rather than a strip on Home. The screen shows the numbers instead of
 * linking to four more screens: a hub of arrows made the user open a second
 * screen to find out whether they were over budget, when the answer was already
 * in this screen's state.
 *
 * Progress is a bar, not a ring. Six budgets stacked are a comparison, and
 * comparing two lengths is something people are good at; comparing six arcs
 * against a circle is not.
 *
 * Geometry is the interface concept's, to the pixel: a 6/14 header, a hero whose
 * number is one step down from Home's (two heroes never share a screen), a
 * hairline-separated card of budget rows, a bleeding rail of goal cards and one
 * link row for subscriptions. The horizontal gutter therefore lives on each
 * block rather than on the scrolling column — the goals rail has to reach both
 * screen edges, which a padded parent makes impossible.
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
    // The Subscriptions screen shows tracked rows **and** the recurring payments
    // the classifier flagged. Counting only the first made this hub say "0 active
    // subscriptions" above a screen listing four of them (owner report,
    // 2026-10-18). Both lists are read here, with the same dismissals the screen
    // applies, so the number can never contradict what the tap reveals.
    val recurringPayments by repository.getRecurringTransactions().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val dismissedSuggestions = remember { SuggestionDismissals.load(context) }
    val detected = remember(recurringPayments, subscriptions, dismissedSuggestions) {
        detectedSubscriptions(
            recurringPayments,
            trackedNames = subscriptions.map { it.name },
            dismissedNames = dismissedSuggestions
        )
    }
    val goals by repository.getAllSavingsGoals().collectAsState(initial = emptyList())
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())

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
    // not agree, with no hint of how far over the user is. The bar's width is the
    // only thing a full track can cap; the text must tell the truth.
    val budgetRatio = if (totalLimit > 0) (totalSpent / totalLimit).toFloat() else 0f
    val overBy = totalSpent - totalLimit
    val monthlySubscriptions = remember(subscriptions) {
        RecurringCost.monthlyTotal(subscriptions)
    }
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    // The concept rounds every percentage (79 %, 122 %), so a truncating cast is
    // not good enough: 61.6 % must read 62 %, and an over-budget row must keep its
    // real number rather than a capped 100 %.
    val heroPercent = (budgetRatio * 100).roundToInt()
    // `plan_monthly_recurring` is "%1$s/mo". Formatted with no amount it yields
    // just the localized "/mo" suffix, which lets the amount and the suffix carry
    // the two weights the concept gives them without adding a second string.
    val perMonthSuffix = stringResource(R.string.plan_monthly_recurring, "")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Header: eyebrow, title, avatar. The gutter is on the title column so
        // the avatar's own 16 dp end inset lands its circle on the gutter instead
        // of 21 dp inside it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AuraSpacing.gutter)
            ) {
                AuraEyebrow(
                    rememberMonthName(),
                    color = MaterialTheme.extendedColors.faint
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    stringResource(R.string.plan_title),
                    style = AuraType.screenTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
            }
            SettingsAvatarButton(onClick = onOpenSettings)
        }

        // Everything below the pinned header scrolls. The hub is taller than a
        // phone at 200% font scale, and a plain Column clips that overflow with no
        // way to reach it.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Budget health: one number, one bar, the truth about overspend
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.gutter)
                    // `.plan-hero` breathes 2 dp above and 4 dp below its content.
                    .padding(top = 2.dp, bottom = 4.dp)
            ) {
                AuraEyebrow(
                    stringResource(R.string.plan_spent_against),
                    color = MaterialTheme.extendedColors.faint
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    formatMoney(totalSpent, fractionDigits = 0),
                    style = AuraType.moneyHeroSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (totalLimit <= 0) {
                    Text(
                        stringResource(R.string.plan_no_budgets),
                        style = AuraType.heroMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Baseline-aligned, not centre-aligned: the bold figure and the
                    // sentence beside it are one line of reading, and centring two
                    // different weights leaves the shared line visibly kinked.
                    Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
                        Text(
                            stringResource(R.string.plan_budget_percent_used, heroPercent),
                            style = AuraType.heroMetaStrong,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            "·",
                            style = AuraType.heroMeta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.plan_across_budgets,
                                budgets.size,
                                stringResource(
                                    R.string.home_budget_of,
                                    formatMoney(totalLimit, fractionDigits = 0)
                                ),
                                budgets.size
                            ),
                            style = AuraType.heroMeta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
                if (totalLimit > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    // Ink, always. The concept paints the aggregate bar as the ink
                    // rule it is; the brick belongs to the per-category rows below,
                    // where it points at the rows that caused the overspend.
                    AuraProgressBar(
                        progress = budgetRatio,
                        color = MaterialTheme.colorScheme.onBackground,
                        trackColor = MaterialTheme.extendedColors.track,
                        height = 8.dp
                    )
                    if (overBy > 0) {
                        // The two numbers above already say the size of the
                        // overspend; this line says where it is.
                        val unknownName = stringResource(R.string.home_unknown_category)
                        val overNames = budgets
                            .filter { it.limitAmount > 0 && it.spentAmount > it.limitAmount }
                            .map { categoryById[it.categoryId]?.name ?: unknownName }
                        Spacer(modifier = Modifier.height(9.dp))
                        Text(
                            pluralStringResource(
                                R.plurals.plan_over_categories,
                                overNames.size,
                                overNames.size,
                                overNames.joinToString(", ")
                            ),
                            style = AuraType.flowCaption,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ── Per-category budgets
            Spacer(modifier = Modifier.height(AuraSpacing.section))
            AuraSectionHeader(
                title = stringResource(R.string.plan_budgets),
                trailing = {
                    AuraSectionLink(
                        text = stringResource(R.string.action_edit),
                        onClick = onOpenBudgets
                    )
                }
            )
            AuraCard(
                style = AuraCardStyle.Outlined,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.gutter)
            ) {
                if (budgets.isEmpty()) {
                    Text(
                        stringResource(R.string.plan_no_budgets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    budgets.forEachIndexed { index, budget ->
                        BudgetRow(
                            name = categoryById[budget.categoryId]?.name
                                ?: stringResource(R.string.home_unknown_category),
                            spent = budget.spentAmount,
                            limit = budget.limitAmount
                        )
                        // Between rows only: a rule above the first row would fence
                        // the card's own padding, and one below the last would double
                        // the card's bottom edge.
                        if (index != budgets.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.extendedColors.lineSoft
                            )
                        }
                    }
                }
            }

            // ── Savings goals
            Spacer(modifier = Modifier.height(AuraSpacing.section))
            AuraSectionHeader(
                title = stringResource(R.string.plan_savings_goals),
                trailing = {
                    AuraSectionLink(
                        text = stringResource(R.string.home_view_all),
                        onClick = onOpenGoals
                    )
                }
            )
            if (goals.isEmpty()) {
                AuraCard(
                    style = AuraCardStyle.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.gutter),
                    onClick = onOpenGoals
                ) {
                    Text(
                        stringResource(R.string.plan_savings_goals_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // A rail, not a list: goals are few, and a horizontal strip keeps
                // Plan one screen tall even when several are in flight. It runs
                // edge to edge — the content padding, not a parent gutter, is what
                // holds the first card on the gutter.
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = AuraSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(goals, key = { it.id }) { goal ->
                        GoalCard(goal = goal, onClick = onOpenGoals)
                    }
                }
            }

            // ── Recurring costs and categories
            Spacer(modifier = Modifier.height(AuraSpacing.section))
            AuraCard(
                style = AuraCardStyle.Outlined,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.gutter),
                contentPadding = PaddingValues(horizontal = AuraSpacing.lg, vertical = 14.dp)
            ) {
                LinkRow(
                    icon = Icons.Default.Subscriptions,
                    title = stringResource(R.string.plan_subscriptions),
                    subtitle = if (detected.isEmpty()) {
                        pluralStringResource(
                            R.plurals.plan_active_subscriptions,
                            subscriptions.size,
                            subscriptions.size
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.plan_subscriptions_and_detected,
                            detected.size,
                            subscriptions.size,
                            detected.size
                        )
                    },
                    onClick = onOpenSubscriptions,
                    trailing = if (subscriptions.isEmpty()) {
                        null
                    } else {
                        {
                            Text(
                                formatMoney(monthlySubscriptions, fractionDigits = 0),
                                style = AuraType.moneySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.alignByBaseline()
                            )
                            Text(
                                perMonthSuffix,
                                style = AuraType.budgetSub,
                                color = MaterialTheme.extendedColors.faint,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                    }
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.extendedColors.lineSoft
                )
                LinkRow(
                    icon = Icons.Default.Category,
                    title = stringResource(R.string.plan_categories),
                    subtitle = stringResource(R.string.plan_categories_subtitle),
                    onClick = onOpenCategories
                )
            }

            Spacer(modifier = Modifier.height(AuraSpacing.xxl))
        }
    }
}

/**
 * One budget as a comparable row.
 *
 * Over-budget keeps the true percentage and says by how much — never a clamped
 * "100 % used" beside a number that is visibly larger.
 */
@Composable
private fun BudgetRow(name: String, spent: Double, limit: Double) {
    val fraction = if (limit > 0) (spent / limit).toFloat() else 0f
    val over = limit > 0 && spent > limit
    // Rounded, not truncated: the concept reads 62 % where the raw ratio is
    // 61.6 %, and an over-budget row keeps its real number (122 %, never 100 %).
    val percent = (fraction * 100).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                style = AuraType.budgetName,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatMoney(spent, fractionDigits = 0),
                style = AuraType.budgetAmount,
                color = if (over) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AuraProgressBar(
            progress = fraction,
            color = if (over) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onBackground,
            trackColor = MaterialTheme.extendedColors.track,
            height = 5.dp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                if (over) stringResource(R.string.budget_over_by, formatMoney(spent - limit, fractionDigits = 0))
                else stringResource(R.string.plan_budget_percent_used, percent),
                style = if (over) AuraType.budgetSub.copy(fontWeight = FontWeight.SemiBold)
                else AuraType.budgetSub,
                color = if (over) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                // Over budgets keep the true percentage on the right; the "over
                // by" already owns the left cell.
                if (over) stringResource(R.string.common_percent, percent)
                else stringResource(
                    R.string.home_budget_of,
                    formatMoney(limit, fractionDigits = 0)
                ),
                style = AuraType.budgetSub,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoalCard(goal: SavingsGoal, onClick: () -> Unit) {
    val fraction = if (goal.targetAmount > 0) {
        (goal.currentAmount / goal.targetAmount).toFloat()
    } else {
        0f
    }

    AuraCard(
        modifier = Modifier.width(196.dp),
        style = AuraCardStyle.Outlined,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp),
        onClick = onClick
    ) {
        Text(
            goal.name,
            style = AuraType.goalTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(9.dp))
        // "₹68,000 of ₹1,20,000" on one line: the goal's whole story is the pair,
        // and splitting it across the card made the reader join it up. The target
        // is a step down in size *and* in weight, so the saved amount stays the
        // loudest thing on the card.
        Row {
            Text(
                formatMoney(goal.currentAmount, fractionDigits = 0),
                style = AuraType.goalAmount,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(modifier = Modifier.width(AuraSpacing.xs))
            Text(
                stringResource(
                    R.string.home_budget_of,
                    formatMoney(goal.targetAmount, fractionDigits = 0)
                ),
                style = AuraType.budgetSub,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
        }
        Spacer(modifier = Modifier.height(9.dp))
        AuraProgressBar(
            progress = fraction,
            color = MaterialTheme.colorScheme.onBackground,
            trackColor = MaterialTheme.extendedColors.track,
            height = 4.dp
        )
        Spacer(modifier = Modifier.height(7.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.common_percent, (fraction * 100).roundToInt()),
                style = AuraType.budgetSub,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            val remaining = goal.targetAmount - goal.currentAmount
            if (remaining > 0) {
                Text(
                    // Compact on purpose: this cell is a footnote to the bar, and
                    // the full figure is already written above it.
                    stringResource(
                        R.string.plan_goal_to_go,
                        formatMoneyCompact(remaining, style = MoneyConfig.current)
                    ),
                    style = AuraType.budgetSub,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * One link row inside a card: a tinted square glyph tile, a title over its meta,
 * and an optional trailing value.
 *
 * The row is `heightIn(min = 48.dp)` rather than the concept's tighter padding:
 * its content is only ~33 dp tall, and this is the only way to reach
 * Subscriptions and Categories from Plan.
 */
@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.inkSoft,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(AuraSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = AuraType.rowLinkTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                subtitle,
                style = AuraType.rowLinkMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.let { slot ->
            Row(verticalAlignment = Alignment.CenterVertically) { slot() }
        }
    }
}
