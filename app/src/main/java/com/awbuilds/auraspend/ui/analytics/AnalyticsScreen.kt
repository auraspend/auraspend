package com.awbuilds.auraspend.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraEyebrow
import com.awbuilds.auraspend.ui.designsystem.AuraProgressBar
import com.awbuilds.auraspend.ui.designsystem.AuraSectionHeader
import com.awbuilds.auraspend.ui.designsystem.AuraSectionLink
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraSparkColumns
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.AnimatedMoney
import com.awbuilds.auraspend.ui.core.UpdateAwareSettingsAvatar
import com.awbuilds.auraspend.ui.designsystem.categoryColor
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class StatsPeriod(val labelRes: Int) {
    THIS_MONTH(R.string.analytics_period_this_month),
    LAST_30(R.string.analytics_period_last_30),
    ALL_TIME(R.string.analytics_period_all_time)
}

/** The daily spending strip: one normalised column per day, plus its x labels. */
private data class DailyStrip(
    val days: Int,
    val values: List<Float>,
    val xLabels: List<String>
)

// ─── Concept geometry ─────────────────────────────────────────────────────────
// Every constant below is the interface concept's own CSS value
// (docs/design/interface-concept/index.html — CSS px == dp 1:1). They are named
// rather than inlined so this screen can be diffed against the spec line by line.

/**
 * The avatar's 38dp art inside [SettingsAvatarButton]'s 48dp touch target: 5dp
 * of slack above, below and to the right of the circle.
 */

/**
 * `.head{padding:6px 0 12px}` — the concept sizes the head around the 38dp
 * circle (56dp tall, title and avatar both 6dp down), not around a touch
 * target. Left alone, the 48dp target makes the head 66dp and pushes every
 * block below it 10dp down the screen, so the slack is taken out of the head's
 * own padding and the title is put back on the art's top edge. The Settings
 * header compensates its back button the same way.
 */
private val HeadTopPadding = 6.dp
private val HeadBottomPadding = 12.dp

/** `.segsm{margin-top:2px}` — the period selector's gap under the head. */
private val SelectorGap = 2.dp

/** `.plan-hero{margin-top:24px;padding:2px 0 4px}` — the 2dp is the hero's own. */
private val HeroTopGap = AuraSpacing.xxl + AuraSpacing.xxs
private val HeroBottomGap = AuraSpacing.xs

/** `.plan-hero .money-hero{margin-top:6px}`. */
private val HeroEyebrowGap = 6.dp

/** The delta row's own `margin-top:10px`. */
private val HeroDeltaGap = 10.dp

/** `.delta{padding:5px 9px;border-radius:8px}`. */
private val DeltaShape = RoundedCornerShape(8.dp)
private val DeltaPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)

/** `.rank-row{padding:9px 0}` and `.rank-line{margin-bottom:6px}`. */
private val RankRowPadding = 9.dp
private val RankLineGap = 6.dp
private val RankBarHeight = 6.dp

/** `.rank-pct{margin-left:auto;padding-left:10px}`. */
private val RankPercentGap = 10.dp

/** `.spark{height:86px;margin-top:6px}` and `.spark-x{margin-top:7px}`. */
private val SparkHeight = 86.dp
private val SparkTopGap = 6.dp
private val SparkAxisGap = 7.dp

/** `.txn{padding:11px 0;gap:12px}` around a `.txn-av{width:36px;height:36px}`. */
private val MerchantRowPadding = 11.dp
private val MerchantBadgeSize = 36.dp

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

    // ── Daily spending strip: one column per day of the period so far
    val locale = LocalConfiguration.current.locales[0]
    val dailySpending = remember(expenseTxns, period, zone, locale) {
        if (period == StatsPeriod.ALL_TIME) return@remember null
        val today = LocalDate.now(zone)
        val start = when (period) {
            StatsPeriod.THIS_MONTH -> today.withDayOfMonth(1)
            else -> today.minusDays(29)
        }
        val days = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .toList()
        val byDay = expenseTxns.groupBy { it.date.toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val max = days.maxOfOrNull { byDay[it] ?: 0.0 } ?: 0.0
        val values = days.map { day ->
            if (max > 0.0) ((byDay[day] ?: 0.0) / max).toFloat() else 0f
        }
        val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
        val xLabels = listOfNotNull(days.firstOrNull(), days.getOrNull(days.size / 2), days.lastOrNull())
            .distinct()
            .map { it.format(formatter) }
        DailyStrip(days = days.size, values = values, xLabels = xLabels)
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
            if (abs(delta) > 999) null else delta to (thisMonth - prevMonth)
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
                .padding(start = AuraSpacing.gutter)
                .padding(top = HeadTopPadding, bottom = HeadBottomPadding),
            // Top, not CenterVertically: the concept's `.head` is
            // `align-items:flex-start`, so the title and the avatar share the
            // head's own 6dp line.
            verticalAlignment = Alignment.Top
        ) {
            Text(
                stringResource(R.string.analytics_title),
                style = AuraType.screenTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            UpdateAwareSettingsAvatar(onClick = onOpenSettings)
        }
        Spacer(modifier = Modifier.height(SelectorGap))
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
            // ── The hero: what left the account, and how that compares
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.gutter)
                        .padding(top = HeroTopGap, bottom = HeroBottomGap)
                ) {
                    AuraEyebrow(
                        stringResource(
                            R.string.analytics_spent_period,
                            stringResource(period.labelRes).lowercase()
                        ),
                        // The concept's eyebrow is the faint grey, not the muted
                        // one the component defaults to.
                        color = extended.faint
                    )
                    Spacer(modifier = Modifier.height(HeroEyebrowGap))
                    AnimatedMoney(
                        amount = totalExpense,
                        style = AuraType.moneyHeroSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    monthDelta?.let { (delta, change) ->
                        Spacer(modifier = Modifier.height(HeroDeltaGap))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DeltaPill(
                                // The percentage carries the direction itself —
                                // the string's own ↑/↓ is the glyph.
                                text = if (delta >= 0) {
                                    stringResource(R.string.analytics_delta_up, delta)
                                } else {
                                    stringResource(R.string.analytics_delta_down, -delta)
                                },
                                good = delta < 0
                            )
                            Spacer(modifier = Modifier.width(AuraSpacing.sm))
                            Text(
                                stringResource(
                                    if (delta >= 0) R.string.analytics_more_than_last
                                    else R.string.analytics_less_than_last,
                                    formatMoney(abs(change))
                                ),
                                style = AuraType.rowMeta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Ranked spending by category
            if (categorySpending.isNotEmpty()) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.analytics_spending_by_category),
                        modifier = Modifier.padding(top = AuraSpacing.section),
                        trailing = {
                            // "View all" opens the plain Activity tab, which is the
                            // concept's own target for this link (`data-nav="activity"`).
                            // A blank category id is the route's documented "no
                            // filter" case, so the existing drill-down callback
                            // already covers it — no new parameter, no new string.
                            AuraSectionLink(
                                text = stringResource(R.string.home_view_all),
                                onClick = { onOpenCategory("") }
                            )
                        }
                    )
                    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
                        AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
                            // A ranked bar list, not a donut. Comparing lengths is
                            // something people do well and comparing angles is not;
                            // the donut's legend was already doing all the reading,
                            // so the legend became the chart.
                            val maxAmount = categorySpending.first().second
                            val shown = categorySpending.take(6)
                            val rest = categorySpending.drop(6)
                            shown.forEach { (cat, amount) ->
                                CategoryRankRow(
                                    name = cat.name,
                                    amount = amount,
                                    share = sharePercent(amount, totalSpent),
                                    fraction = if (maxAmount > 0) (amount / maxAmount).toFloat() else 0f,
                                    color = categoryColor(cat.color),
                                    onClick = { onOpenCategory(cat.id) }
                                )
                            }
                            if (rest.isNotEmpty()) {
                                // Six rows is a glance; the tail is summed, not
                                // dropped — the same shape the concept draws.
                                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = extended.lineSoft
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = AuraSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        pluralStringResource(
                                            R.plurals.analytics_more_categories,
                                            rest.size,
                                            rest.size
                                        ),
                                        style = AuraType.budgetSub,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        formatMoney(rest.sumOf { it.second }),
                                        style = AuraType.budgetSub,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Daily spending: one column per day, the latest in ink
            dailySpending?.let { strip ->
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.analytics_daily_spending),
                        modifier = Modifier.padding(top = AuraSpacing.section),
                        trailing = {
                            Text(
                                pluralStringResource(R.plurals.analytics_daily_days, strip.days, strip.days),
                                style = AuraType.rankPercent,
                                color = extended.faint
                            )
                        }
                    )
                    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
                        AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(SparkTopGap))
                            AuraSparkColumns(
                                values = strip.values,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SparkHeight),
                                contentDescription = stringResource(R.string.analytics_daily_spending)
                            )
                            if (strip.xLabels.size > 1) {
                                Spacer(modifier = Modifier.height(SparkAxisGap))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    strip.xLabels.forEach { label ->
                                        Text(
                                            label,
                                            style = AuraType.axisLabel,
                                            color = extended.faint
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Top merchants: a plain ranked list — "who" needs no chart
            if (merchantSpending.isNotEmpty()) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.analytics_top_merchants),
                        modifier = Modifier.padding(top = AuraSpacing.section)
                    )
                    Column(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
                        merchantSpending.forEachIndexed { index, (name, amount, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenMerchant(name) }
                                    .padding(vertical = MerchantRowPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(MerchantBadgeSize)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        (index + 1).toString(),
                                        // 11sp/800: the concept's rank numeral. The
                                        // token is 11sp/600, so only the weight is
                                        // overridden — never the size.
                                        style = AuraType.rankPercent.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(AuraSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        style = AuraType.rowTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        pluralStringResource(R.plurals.analytics_txn_count, count, count),
                                        style = AuraType.rowMeta,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    formatMoney(amount),
                                    style = AuraType.rowMoney,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (index != merchantSpending.lastIndex) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = extended.lineSoft
                                )
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

/**
 * The "12% vs last month" pill: brick-soft with brick text when spending rose,
 * a 12% wash of the income green when it fell. The percentage already states the
 * direction, so the pill's colour is the only extra signal it needs to carry.
 */
@Composable
private fun DeltaPill(text: String, good: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val foreground = if (good) extended.incomeAmount else scheme.error
    Box(
        modifier = Modifier
            .clip(DeltaShape)
            .background(if (good) extended.incomeAmount.copy(alpha = 0.12f) else scheme.errorContainer)
            .padding(DeltaPadding)
    ) {
        Text(text, style = AuraType.deltaLabel, color = foreground)
    }
}

/**
 * One category in the ranked list: name, share, amount, then the bar.
 *
 * The bar is scaled against the largest category rather than the total, so the
 * list reads as a ranking; the percentage beside it is the share of the whole.
 * Name, share and amount sit on one baseline — the concept aligns the line by
 * baseline, which is what keeps a 11sp share level with a 12.5sp name.
 */
@Composable
private fun CategoryRankRow(
    name: String,
    amount: Double,
    share: Int,
    fraction: Float,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = RankRowPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = RankLineGap)
        ) {
            Text(
                name,
                style = AuraType.rankName,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
            )
            Text(
                stringResource(R.string.common_percent, share),
                style = AuraType.rankPercent,
                color = MaterialTheme.extendedColors.faint,
                modifier = Modifier
                    .padding(start = RankPercentGap)
                    .alignByBaseline()
            )
            Text(
                formatMoney(amount),
                style = AuraType.rankValue,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = AuraSpacing.sm)
                    .alignByBaseline()
            )
        }
        AuraProgressBar(
            progress = fraction,
            color = color,
            height = RankBarHeight
        )
    }
}
