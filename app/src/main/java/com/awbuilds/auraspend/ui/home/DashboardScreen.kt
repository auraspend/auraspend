package com.awbuilds.auraspend.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.designsystem.*
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Home is a daily money command centre: current position → next actions →
 * movement → plans → latest activity.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onQuickAdd: (TransactionType) -> Unit,
    onOpenSmartAdd: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToPlan: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenTransaction: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var hideAmounts by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(DashboardViewIntent.LoadDashboard)
    }

    val recent = state.recentTransactions.take(6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AuraSpacing.gutter, end = AuraSpacing.sm, top = AuraSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greetingText(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            HideAmountIconButton(hidden = hideAmounts, onToggle = { hideAmounts = !hideAmounts })
            SettingsAvatarButton(onClick = onOpenSettings)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AuraSpacing.xxl)
        ) {
            // A failed aggregate used to be set in the ViewModel and then silently
            // dropped here, leaving an empty-looking dashboard that read as "no
            // data yet". Show a real failure with a retry instead.
            if (state.error != null && state.recentTransactions.isEmpty()) {
                item {
                    AuraErrorState(
                        icon = Icons.Default.CloudOff,
                        title = stringResource(R.string.error_load_failed),
                        message = stringResource(R.string.error_load_failed_detail),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = { viewModel.handleIntent(DashboardViewIntent.LoadDashboard) }
                    )
                }
                return@LazyColumn
            }

            if (state.isLoading && state.recentTransactions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AuraSpacing.md)
                    ) {
                        AuraSkeleton(modifier = Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(24.dp))
                        AuraSkeleton(modifier = Modifier.fillMaxWidth().height(64.dp))
                        AuraSkeleton(modifier = Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(20.dp))
                    }
                }
                return@LazyColumn
            }

            item {
                Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.md)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(AuraGradients.aurora)
                    ) {
                        // Illustrated watermark: the terracotta mark sits inside the
                        // hero at low opacity, so the brand object appears on the
                        // screen's one gradient moment without competing with the
                        // balance.
                        AuraMark(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = AuraSpacing.md, bottom = AuraSpacing.sm)
                                .size(112.dp)
                                .graphicsLayer { alpha = 0.22f }
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AuraSpacing.xl)
                        ) {
                            Text(
                                stringResource(R.string.home_total_balance),
                                style = AuraType.metricLabel,
                                color = AuraGradients.onAurora.copy(alpha = 0.82f)
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.xs))
                            if (hideAmounts) {
                                Text(stringResource(R.string.state_hidden_amount), style = AuraType.moneyHero, color = AuraGradients.onAurora)
                            } else {
                                AnimatedMoney(
                                    amount = state.totalBalance,
                                    style = AuraType.moneyHero,
                                    color = AuraGradients.onAurora
                                )
                            }
                            Spacer(modifier = Modifier.height(AuraSpacing.xl))
                            Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
                                HeroStat(
                                    label = stringResource(R.string.home_in_this_month),
                                    value = if (hideAmounts) stringResource(R.string.state_hidden_amount)
                                    else stringResource(R.string.common_amount_plus, formatMoney(state.monthlyIncome)),
                                    onClick = onNavigateToAnalytics
                                )
                                HeroDivider()
                                HeroStat(
                                    label = stringResource(R.string.home_out_this_month),
                                    value = if (hideAmounts) stringResource(R.string.state_hidden_amount)
                                    else stringResource(R.string.common_amount_minus, formatMoney(state.monthlyExpense)),
                                    onClick = onNavigateToAnalytics
                                )
                            }
                        }
                    }
                }
            }

            // ── Quick actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                ) {
                    QuickAction(
                        icon = Icons.Default.Add,
                        label = stringResource(R.string.home_quick_expense),
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAdd(TransactionType.EXPENSE) }
                    )
                    QuickAction(
                        icon = Icons.Default.ArrowDownward,
                        label = stringResource(R.string.home_quick_income),
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAdd(TransactionType.INCOME) }
                    )
                    QuickAction(
                        icon = Icons.Default.AutoAwesome,
                        label = stringResource(R.string.home_quick_smart_add),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSmartAdd
                    )
                    QuickAction(
                        icon = Icons.Default.BarChart,
                        label = stringResource(R.string.home_quick_stats),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAnalytics
                    )
                }
            }

            // ── Cash flow
            if (state.dailySpending.isNotEmpty()) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.home_cash_flow),
                        modifier = Modifier.padding(top = AuraSpacing.lg)
                    )
                }
                item {
                    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
                        AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
                            // The chart draws nothing when every value is 0, which left an
                            // unexplained blank box. Say so instead of rendering emptiness.
                            val hasSpending = state.dailySpending.any { it.second > 0.0 }
                            if (!hasSpending) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_no_spending_last_7_days),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                val points = if (hideAmounts) state.dailySpending.map { it.first to 0.0 } else state.dailySpending
                                AuraAreaChart(
                                    points = points,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentDescription = stringResource(R.string.home_spending_last_7_days)
                                )
                            }
                            Spacer(modifier = Modifier.height(AuraSpacing.sm))
                            WeekdayLabels(epochDays = state.dailySpending.map { it.first })
                        }
                    }
                }
            }

            // ── Budgets carousel
            if (state.budgets.isNotEmpty()) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.home_budgets),
                        modifier = Modifier.padding(top = AuraSpacing.lg),
                        trailing = {
                            Text(
                                stringResource(R.string.home_manage),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onNavigateToPlan)
                                    .padding(horizontal = AuraSpacing.sm, vertical = AuraSpacing.xs)
                            )
                        }
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AuraSpacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
                    ) {
                        items(state.budgets, key = { it.id }) { budget ->
                            val category = state.categories.find { it.id == budget.categoryId }
                            BudgetCarouselCard(
                                name = category?.name ?: stringResource(R.string.home_unknown_category),
                                emoji = categoryIconGlyph(category?.icon),
                                color = categoryColor(category?.color),
                                spent = budget.spentAmount,
                                limit = budget.limitAmount,
                                hideAmounts = hideAmounts,
                                onClick = onNavigateToPlan
                            )
                        }
                    }
                }
            }

            // ── Subscriptions
            if (state.activeSubscriptions.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.lg)) {
                        AuraCard(
                            style = AuraCardStyle.Tonal,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(AuraSpacing.lg),
                            onClick = onNavigateToPlan
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
                                        Icons.Default.Subscriptions,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(AuraSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.home_subscriptions),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        pluralStringResource(
                                            R.plurals.home_subscriptions_active,
                                            state.activeSubscriptions.size,
                                            state.activeSubscriptions.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    if (hideAmounts) stringResource(R.string.state_hidden_amount)
                                    else stringResource(
                                        R.string.home_subscription_monthly,
                                        formatMoney(state.totalSubscriptionCost, fractionDigits = 0)
                                    ),
                                    style = AuraType.moneyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // ── Recent activity
            item {
                AuraSectionHeader(
                    title = stringResource(R.string.home_recent),
                    modifier = Modifier.padding(top = AuraSpacing.sm),
                    trailing = {
                        Text(
                            stringResource(R.string.home_view_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onNavigateToTransactions)
                                .padding(horizontal = AuraSpacing.sm, vertical = AuraSpacing.xs)
                        )
                    }
                )
            }

            if (recent.isEmpty()) {
                item {
                    AuraEmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.home_add_transaction),
                        onAction = { onQuickAdd(TransactionType.EXPENSE) }
                    )
                }
            } else {
                items(recent, key = { it.id }) { transaction ->
                    val category = state.categories.find { it.id == transaction.categoryId }
                    TransactionEntryRow(
                        transaction = transaction,
                        categoryName = category?.name ?: stringResource(R.string.home_other_category),
                        categoryColor = categoryColor(category?.color),
                        categoryEmoji = categoryIconGlyph(category?.icon),
                        modifier = Modifier.animateItem(),
                        onClick = { onOpenTransaction(transaction.id) }
                    )
                }
            }
        }
    }
}

// ─── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun HeroStat(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(AuraSpacing.sm)
    ) {
        Text(
            label,
            style = AuraType.metricLabel,
            color = AuraGradients.onAurora.copy(alpha = 0.78f)
        )
        Text(
            value,
            style = AuraType.moneySmall,
            color = AuraGradients.onAurora
        )
    }

}

@Composable
private fun HeroDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(AuraGradients.onAurora.copy(alpha = 0.2f))
    )
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = AuraSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WeekdayLabels(epochDays: List<Long>) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEE") }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        epochDays.forEach { epoch ->
            Text(
                Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(formatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetCarouselCard(
    name: String,
    emoji: String?,
    color: Color,
    spent: Double,
    limit: Double,
    hideAmounts: Boolean,
    onClick: () -> Unit
) {
    val progress = if (limit > 0) (spent / limit).toFloat() else 0f
    val overBudget = limit > 0 && spent > limit

    AuraCard(
        modifier = Modifier.width(168.dp),
        style = AuraCardStyle.Outlined,
        contentPadding = PaddingValues(AuraSpacing.lg),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuraProgressRing(
                progress = progress,
                color = if (overBudget) MaterialTheme.extendedColors.expenseAmount else color,
                modifier = Modifier.size(40.dp),
                stroke = 3.5.dp,
                contentDescription = stringResource(
                    R.string.home_budget_percent_used,
                    name,
                    (progress.coerceIn(0f, 1f) * 100).toInt()
                )
            ) {
                CategoryAvatar(icon = emoji, color = color, size = 26.dp)
            }
            Spacer(modifier = Modifier.width(AuraSpacing.sm))
            // Category names are user-entered and routinely longer than the ring's
            // row allows ("Food & dining" was clipped to "Food &"). Give the name
            // its own line so it is never silently truncated.
            //
            // Reserving two lines matters as much as allowing them: a card whose
            // name wraps grew taller than its neighbours, so the amount beneath it
            // sat at a different height across the carousel. Screenshot on-device
            // 2026-09-29: "Food &" / "Dining" over "₹4,928" while "Grocery" over
            // "₹7,299" — two baselines one row apart.
            //
            // heightIn, not height, so the reserve grows with the user's font scale
            // instead of clipping at 200%.
            val nameLineHeight = MaterialTheme.typography.titleSmall.lineHeight
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(
                    min = with(LocalDensity.current) { nameLineHeight.toDp() } * 2
                )
            )
        }
        Spacer(modifier = Modifier.height(AuraSpacing.md))
        Text(
            if (hideAmounts) stringResource(R.string.state_hidden_amount) else formatMoney(spent),
            style = AuraType.moneyMedium,
            color = if (overBudget) MaterialTheme.extendedColors.expenseAmount
            else MaterialTheme.colorScheme.onSurface
        )
        Text(
            if (hideAmounts) stringResource(R.string.home_budget_of, stringResource(R.string.state_hidden_amount))
            else stringResource(R.string.home_budget_of, formatMoney(limit)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun greetingText(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> stringResource(R.string.home_greeting_morning)
        hour < 17 -> stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_evening)
    }
}
