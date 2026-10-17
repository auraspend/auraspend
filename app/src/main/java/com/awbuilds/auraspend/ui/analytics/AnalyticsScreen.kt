package com.awbuilds.auraspend.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.designsystem.AuraAreaChart
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSlice
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.AnimatedMoney
import com.awbuilds.auraspend.ui.designsystem.AuraDonutChart
import com.awbuilds.auraspend.ui.designsystem.SettingsAvatarButton
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.designsystem.moneySign
import com.awbuilds.auraspend.ui.theme.extendedColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import java.time.LocalDate
import java.time.ZoneId

private enum class StatsPeriod(val labelRes: Int) {
    THIS_MONTH(R.string.analytics_period_this_month),
    LAST_30(R.string.analytics_period_last_30),
    ALL_TIME(R.string.analytics_period_all_time)
}

/**
 * Insights — spending analytics with animated data visualisations and
 * month-over-month context.
 */
/**
 * Share of [total] as a whole percentage, never rounding a real amount to "0%".
 *
 * Truncating made a category with genuine spend read as zero: the demo data
 * showed "Transport 0% ₹2,795" on-device, which looks like a bug next to a
 * non-zero amount. Anything above zero now floors at 1%, so a slice can be
 * small but never absent, and the legend still sums to a readable total.
 */
private fun sharePercent(amount: Double, total: Double): Int {
    if (total <= 0.0 || amount <= 0.0) return 0
    val percent = (amount / total * 100).toInt()
    return if (percent == 0) 1 else percent
}

@Composable
fun AnalyticsScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    /** Drill-down: open Activity filtered to one category slice. */
    onOpenCategory: (String) -> Unit = {},
    /** Drill-down: open Activity searched for one merchant. */
    onOpenMerchant: (String) -> Unit = {}
) {
    val extended = MaterialTheme.extendedColors
    var period by remember { mutableStateOf(StatsPeriod.THIS_MONTH) }
    val zone = ZoneId.systemDefault()

    val scoped = remember(transactions, period) {
        val today = LocalDate.now()
        val start = when (period) {
            StatsPeriod.THIS_MONTH -> today.withDayOfMonth(1)
            StatsPeriod.LAST_30 -> today.minusDays(29)
            StatsPeriod.ALL_TIME -> null
        }
        val startMillis = start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
        transactions.filter {
            it.date.atZone(zone).toInstant().toEpochMilli() >= startMillis
        }
    }

    val expenseTxns = remember(scoped) { scoped.filter { it.type == TransactionType.EXPENSE } }
    val totalExpense = remember(expenseTxns) { expenseTxns.sumOf { it.amount } }
    val totalIncome = remember(scoped) { scoped.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } }
    val net = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) ((net / totalIncome) * 100).coerceIn(-999.0, 100.0) else null

    val categorySpending = remember(expenseTxns, categories) {
        categories.map { cat ->
            cat to expenseTxns.filter { it.categoryId == cat.id }.sumOf { it.amount }
        }.filter { it.second > 0 }.sortedByDescending { it.second }
    }
    val totalSpent = remember(categorySpending) { categorySpending.sumOf { it.second } }

    val unlabelledMerchant = stringResource(R.string.analytics_unlabelled)
    val merchantSpending = remember(expenseTxns, unlabelledMerchant) {
        expenseTxns
            .groupBy { it.merchant?.takeIf { m -> m.isNotBlank() } ?: unlabelledMerchant }
            .map { (name, list) -> Triple(name.trim(), list.sumOf { it.amount }, list.size) }
            .sortedByDescending { it.second }
            .take(6)
    }

    // Month-over-month expense delta (only meaningful for "This Month").
    val monthDelta = remember(transactions, period) {
        if (period != StatsPeriod.THIS_MONTH) return@remember null
        val today = LocalDate.now()
        val thisStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val prevStart = today.minusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var thisMonth = 0.0
        var prevMonth = 0.0
        transactions.forEach { txn ->
            if (txn.type != TransactionType.EXPENSE) return@forEach
            val ts = txn.date.atZone(zone).toInstant().toEpochMilli()
            when {
                ts >= thisStart -> thisMonth += txn.amount
                ts in prevStart until thisStart -> prevMonth += txn.amount
            }
        }
        if (prevMonth <= 0.0) {
            null
        } else {
            val delta = ((thisMonth - prevMonth) / prevMonth * 100).toInt()
            // Suppress rather than print a meaningless figure. With a near-empty
            // previous month the ratio explodes: seeded demo data produced
            // "↑ 46843% vs last month" on-device, which reads as a bug and tells
            // the user nothing. Three digits is already past the point where a
            // month-over-month percentage is interpretable.
            if (abs(delta) > 999) null else delta
        }
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
                .padding(start = AuraSpacing.gutter, top = AuraSpacing.sm, bottom = AuraSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.analytics_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            SettingsAvatarButton(onClick = onOpenSettings)
        }
        AuraSegmentedControl(
            options = StatsPeriod.entries.map { stringResource(it.labelRes) },
            selectedIndex = period.ordinal,
            onSelect = { period = StatsPeriod.entries[it] },
            modifier = Modifier.padding(horizontal = AuraSpacing.gutter)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AuraSpacing.xxl)
        ) {
            // ── Summary hero
            item {
                Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.lg)) {
                    AuraCard(style = AuraCardStyle.Tonal, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.xxl)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.analytics_expense),
                                    style = AuraType.metricLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                AnimatedMoney(
                                    amount = totalExpense,
                                    style = AuraType.moneyLarge,
                                    color = extended.expenseAmount
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.analytics_income),
                                    style = AuraType.metricLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                AnimatedMoney(
                                    amount = totalIncome,
                                    style = AuraType.moneyLarge,
                                    color = extended.incomeAmount
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AuraSpacing.lg))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(AuraSpacing.md))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        R.string.analytics_net_period,
                                        stringResource(period.labelRes).lowercase()
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(
                                        if (net >= 0) R.string.common_amount_plus else R.string.common_amount_minus,
                                        formatMoney(kotlin.math.abs(net))
                                    ),
                                    style = AuraType.moneyMedium,
                                    color = if (net >= 0) extended.incomeAmount else extended.expenseAmount
                                )
                            }
                            savingsRate?.let { rate ->
                                val percent = rate.toInt()
                                // The sign comes from the shared money helpers (U+2212),
                                // not the platform's `-`: this badge sits directly under
                                // "Net this month −₹32,163" in the same card, and the two
                                // minus glyphs next to each other read as a rendering bug.
                                // A rate that truncates to 0 gets no sign — "-0%" is noise.
                                val sign = if (percent < 0) moneySign(rate) else ""
                                Badge(
                                    text = stringResource(R.string.analytics_saving_percent, sign + abs(percent)),
                                    color = if (rate >= 0) extended.incomeAmount else extended.expenseAmount
                                )
                            }
                        }
                        monthDelta?.let { delta ->
                            Spacer(modifier = Modifier.height(AuraSpacing.sm))
                            Badge(
                                text = if (delta >= 0) stringResource(R.string.analytics_delta_up, delta)
                                else stringResource(R.string.analytics_delta_down, -delta),
                                color = if (delta >= 0) extended.expenseAmount else extended.incomeAmount
                            )
                        }
                    }
                }
            }

            // ── Category donut + legend
            if (categorySpending.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
                        AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.analytics_spending_by_category),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.lg))
                            AuraDonutChart(
                                data = categorySpending.map { (cat, value) ->
                                    AuraSlice(cat.name, value, androidx.compose.ui.graphics.Color(cat.color.toLong()))
                                },
                                modifier = Modifier
                                    .size(190.dp)
                                    .align(Alignment.CenterHorizontally),
                                strokeWidth = 32.dp,
                                contentDescription = stringResource(
                                    R.string.analytics_spending_by_category_desc,
                                    formatMoney(totalSpent)
                                )
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.analytics_total), style = MaterialTheme.typography.labelMedium, color = extended.textLight)
                                    Text(
                                        formatMoney(totalSpent),
                                        style = AuraType.moneyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(AuraSpacing.lg))
                            categorySpending.forEachIndexed { index, (cat, amount) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenCategory(cat.id) }
                                        .padding(vertical = AuraSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(androidx.compose.ui.graphics.Color(cat.color.toLong()))
                                    )
                                    Spacer(modifier = Modifier.width(AuraSpacing.sm))
                                    Text(
                                        cat.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        stringResource(
                                            R.string.common_percent,
                                            sharePercent(amount, totalSpent)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = extended.textLight
                                    )
                                    Spacer(modifier = Modifier.width(AuraSpacing.md))
                                    Text(
                                        formatMoney(amount),
                                        // Match the merchant list and the transaction list:
                                        // an expense is an expense wherever it is summarised.
                                        color = extended.expenseAmount
                                    )
                                }
                                if (index != categorySpending.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Top merchants
            if (merchantSpending.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.lg)) {
                        AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.analytics_top_merchants),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.sm))
                            merchantSpending.forEachIndexed { index, (name, amount, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenMerchant(name) }
                                        .padding(vertical = AuraSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            pluralStringResource(R.plurals.analytics_txn_count, count, count),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = extended.textLight
                                        )
                                    }
                                    Text(
                                        formatMoney(amount),
                                        style = AuraType.moneySmall,
                                        color = extended.expenseAmount
                                    )
                                }
                                if (index != merchantSpending.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (categorySpending.isEmpty() && merchantSpending.isEmpty()) {
                item {
                    AuraEmptyState(
                        icon = Icons.Default.Insights,
                        title = if (period == StatsPeriod.ALL_TIME) stringResource(R.string.analytics_empty_title_all_time)
                        else stringResource(R.string.analytics_empty_title_period),
                        message = stringResource(R.string.analytics_empty_message)
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = AuraSpacing.md, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
