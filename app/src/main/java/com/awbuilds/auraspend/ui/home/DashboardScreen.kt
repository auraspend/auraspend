package com.awbuilds.auraspend.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.designsystem.*
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter

/**
 * Home is a glance, not a dashboard.
 *
 * A money app is opened in three-second bursts, usually to answer one of two
 * questions: *can I afford this*, and *did that charge land*. So the screen reads
 * in this order — position, anything that needs action, how the month is going,
 * and today's rows — and all of it fits above the fold on a 411 dp phone.
 *
 * The cash-flow chart that used to sit below the rows now lives on Insights as
 * "Daily spending": analysis is a deliberate visit, and Home is a glance.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onQuickAdd: (TransactionType) -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToPlan: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenTransaction: (String) -> Unit = {},
    onOpenReviewQueue: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handleIntent(DashboardViewIntent.LoadDashboard)
    }

    // Today's rows: Home answers "did that charge land" for *today*. Older days
    // are the Activity list's job, whose sticky day headers already carry the
    // date, so these rows can show a time of day instead of "2d ago".
    val todaysRows = remember(state.recentTransactions) {
        val today = LocalDate.now()
        state.recentTransactions
            .filter { it.date.toLocalDate() == today }
            .take(4)
    }

    // Derived once per budget change, in composition — not inside LazyListScope,
    // where `remember` is not a composable context.
    val overBudget = remember(state.budgets) {
        state.budgets.filter { it.limitAmount > 0 && it.spentAmount > it.limitAmount }
            .sortedByDescending { it.spentAmount - it.limitAmount }
    }
    // ── The seven-day strip. A quiet day used to leave Home with nothing below
    // the budgets card but an empty "Today", so the strip carries the week even
    // when today has no rows. Values are normalised to the week's own maximum,
    // which is what AuraSparkColumns expects.
    val weekStrip = remember(state.dailySpend) {
        val max = state.dailySpend.maxOrNull() ?: 0.0
        state.dailySpend.map { if (max > 0.0) (it / max).toFloat() else 0f }
    }
    val weekTotal = remember(state.dailySpend) { state.dailySpend.sum() }
    val weekLabels = remember(state.dailySpend) {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val today = LocalDate.now()
        listOf(6, 3, 0).map { back -> today.minusDays(back.toLong()).format(formatter) }
    }
    val budgetLimit = remember(state.budgets) { state.budgets.sumOf { it.limitAmount } }
    val budgetSpent = remember(state.budgets) { state.budgets.sumOf { it.spentAmount } }

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
                .padding(top = 6.dp, bottom = 22.dp),
            // Top, not CenterVertically: the concept's `.head` is
            // `align-items:flex-start`, so the avatar sits on the eyebrow's line.
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AuraEyebrow(todayLabel())
                // 5dp, the concept's gap between an eyebrow and the title it
                // labels: tighter than a paragraph break, looser than leading.
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    greetingText(),
                    style = AuraType.screenTitle,
                    modifier = Modifier.semantics { heading() },
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
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
                        AuraSkeleton(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), shape = RoundedCornerShape(24.dp))
                        AuraSkeleton(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp))
                        AuraSkeleton(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), shape = RoundedCornerShape(20.dp))
                    }
                }
                return@LazyColumn
            }

            // ── Position: the balance, and how much of this month's income is gone
            item {
                BalanceBlock(
                    // The month's net, not "the balance". Income minus expense
                    // since the first message is not money you have — there is
                    // no opening balance and no account behind it — and calling
                    // it a balance made a credit-card spend look like cash
                    // leaving your pocket (2026-10-20). What the data can
                    // honestly say is what came in and what went out this
                    // month; a real balance needs the bank's own figure, which
                    // is the next step.
                    balance = state.monthlyIncome - state.monthlyExpense,
                    income = state.monthlyIncome,
                    expense = state.monthlyExpense,
                    onOpenAnalytics = onNavigateToAnalytics
                )
            }

            // ── Anything that needs a decision, in one swipeable row
            if (overBudget.isNotEmpty() || state.reviewQueueCount > 0) {
                item {
                    NeedsYouRail(
                        overBudget = overBudget.firstOrNull(),
                        overBudgetCategory = overBudget.firstOrNull()?.let { budget ->
                            state.categories.find { it.id == budget.categoryId }?.name
                        },
                        reviewQueueCount = state.reviewQueueCount,
                        onOpenPlan = onNavigateToPlan,
                        onOpenReviewQueue = onOpenReviewQueue
                    )
                }
            }

            // ── The month's budgets, as one aggregate rather than a carousel
            if (budgetLimit > 0) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.home_budgets_month, rememberMonthName()),
                        modifier = Modifier.padding(top = AuraSpacing.section),
                        trailing = {
                            AuraSectionLink(
                                text = stringResource(R.string.home_all_budgets),
                                onClick = onNavigateToPlan
                            )
                        }
                    )
                }
                item {
                    BudgetSummaryCard(
                        spent = budgetSpent,
                        limit = budgetLimit,
                        overCount = overBudget.size,
                        budgetCount = state.budgets.size,
                        onClick = onNavigateToPlan
                    )
                }
            }

            // ── The last seven days, so a quiet day is not a blank screen
            if (state.dailySpend.any { it > 0.0 }) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.home_last_seven_days),
                        modifier = Modifier.padding(top = AuraSpacing.section),
                        trailing = {
                            Text(
                                formatMoney(weekTotal, fractionDigits = 0),
                                style = AuraType.rankPercent,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
                item {
                    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
                        AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(AuraSpacing.lg))
                            AuraSparkColumns(
                                values = weekStrip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentDescription = stringResource(R.string.home_last_seven_days)
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.sm))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                weekLabels.forEach { label ->
                                    Text(
                                        label,
                                        style = AuraType.axisLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Today's rows
            item {
                AuraSectionHeader(
                    title = stringResource(R.string.activity_group_today),
                    modifier = Modifier.padding(top = AuraSpacing.section),
                    trailing = {
                        AuraSectionLink(
                            text = stringResource(R.string.home_all_activity),
                            onClick = onNavigateToTransactions
                        )
                    }
                )
            }

            if (state.recentTransactions.isEmpty()) {
                item {
                    AuraEmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.home_add_transaction),
                        onAction = { onQuickAdd(TransactionType.EXPENSE) }
                    )
                }
            } else if (todaysRows.isEmpty()) {
                // Rows exist, none today. Say so quietly: the user's question
                // ("did anything land today?") is answered by the absence, and
                // "All activity" is one tap away.
                item {
                    Text(
                        stringResource(R.string.home_nothing_today),
                        style = AuraType.rowMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = AuraSpacing.gutter,
                            vertical = AuraSpacing.sm
                        )
                    )
                }
            } else {
                itemsIndexed(todaysRows, key = { _, transaction -> transaction.id }) { index, transaction ->
                    val category = state.categories.find { it.id == transaction.categoryId }
                    TransactionEntryRow(
                        transaction = transaction,
                        categoryName = category?.name ?: stringResource(R.string.home_other_category),
                        categoryColor = categoryColor(category?.color),
                        categoryEmoji = categoryIconGlyph(category?.icon),
                        modifier = Modifier.animateItem(),
                        divider = index != todaysRows.lastIndex,
                        onClick = { onOpenTransaction(transaction.id) }
                    )
                }
            }
        }
    }
}

// ─── Pieces ───────────────────────────────────────────────────────────────────

/**
 * The one hero on this screen: the balance, then the share of this month's
 * income that has already gone. The bar is the fastest read of "am I ahead this
 * month" — faster than two numbers, and far faster than the ring it replaced.
 */
@Composable
private fun BalanceBlock(
    balance: Double,
    income: Double,
    expense: Double,
    onOpenAnalytics: () -> Unit
) {
    val fraction = if (income > 0) (expense / income).toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAnalytics)
            // `.balance{padding:2px 0 0}` — no bottom padding: the rail's own
            // 22dp margin is the whole gap between the flow captions and the cards.
            .padding(horizontal = AuraSpacing.gutter)
            .padding(top = 2.dp)
    ) {
        AuraEyebrow(stringResource(R.string.home_this_month))
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        AnimatedMoney(
            amount = balance,
            style = AuraType.moneyHero,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(18.dp))
        AuraProgressBar(
            progress = fraction,
            // Past 100 % of income the bar is reporting a problem, so it says so
            // in the status colour rather than reading as a full black rule.
            color = if (fraction >= 1f) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onBackground,
            trackColor = MaterialTheme.extendedColors.track,
            height = 7.dp
        )
        Spacer(modifier = Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlowCaption(
                dotColor = MaterialTheme.colorScheme.onSurface,
                amount = formatMoney(expense),
                word = stringResource(R.string.home_out_short)
            )
            Spacer(modifier = Modifier.weight(1f))
            FlowCaption(
                dotColor = MaterialTheme.extendedColors.incomeAmount,
                amount = formatMoney(income),
                word = stringResource(R.string.home_in_short)
            )
        }
    }
}

/**
 * "● ₹86,200 out" — one line per side of the bar.
 *
 * Two labelled columns ("Out this month" above the value) cost a line of height
 * and made the caption compete with the balance above it. The dot carries the
 * colour, the word carries the direction, and the bar carries the ratio — so the
 * amount itself is unsigned; a minus beside the word "out" says it twice.
 */
@Composable
private fun FlowCaption(dotColor: Color, amount: String, word: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "$amount $word",
            style = AuraType.flowCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The two things that ever need a decision, side by side.
 *
 * Capped at two cards on purpose: a rail of six "insights" is a feed, and this is
 * supposed to be a prompt. Everything here navigates to the screen that can act
 * on it — nothing is a dead-end notification.
 */
@Composable
private fun NeedsYouRail(
    overBudget: Budget?,
    overBudgetCategory: String?,
    reviewQueueCount: Int,
    onOpenPlan: () -> Unit,
    onOpenReviewQueue: () -> Unit
) {
    LazyRow(
        modifier = Modifier.padding(top = 22.dp),
        contentPadding = PaddingValues(start = AuraSpacing.gutter, end = AuraSpacing.gutter, top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (overBudget != null) {
            item {
                // A sentence, not a label: "Grocery is over" is the thing that
                // needs attention; "Grocery" alone is a category name the user
                // already knows.
                val category = overBudgetCategory ?: stringResource(R.string.home_unknown_category)
                AlertCard(
                    title = stringResource(R.string.home_budget_is_over, category),
                    detail = stringResource(
                        R.string.home_over_your_budget,
                        formatMoney(overBudget.spentAmount - overBudget.limitAmount),
                        formatMoney(overBudget.limitAmount)
                    ),
                    emphasise = true,
                    onClick = onOpenPlan
                )
            }
        }
        if (reviewQueueCount > 0) {
            item {
                AlertCard(
                    count = reviewQueueCount,
                    title = pluralStringResource(
                        R.plurals.home_review_queue,
                        reviewQueueCount,
                        reviewQueueCount
                    ),
                    detail = stringResource(R.string.home_review_queue_detail),
                    emphasise = false,
                    onClick = onOpenReviewQueue
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    title: String,
    detail: String,
    emphasise: Boolean,
    onClick: () -> Unit,
    count: Int? = null
) {
    // The concept paints the badge as a *tinted circle holding a glyph*, not as a
    // stroked icon: a Material circled-exclamation inside a 30 dp circle draws
    // two rings. The mark is a text glyph, the count is the same slot with a
    // number in it.
    val badgeBg = if (emphasise) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)
    val badgeFg = if (emphasise) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.inkSoft
    AuraCard(
        modifier = Modifier.width(258.dp),
        style = AuraCardStyle.Outlined,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                // A count badge instead of an icon where the card is about a
                // number ("3 messages to review") — the same circle, a different
                // payload.
                Text(
                    count?.toString() ?: "!",
                    style = AuraType.alertBadge,
                    color = badgeFg
                )
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column {
                Text(
                    title,
                    style = AuraType.alertTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    detail,
                    style = AuraType.alertMeta,
                    color = if (emphasise) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * One aggregate instead of a carousel of per-category cards.
 *
 * The carousel showed three budgets and hid the rest behind a horizontal scroll,
 * so "am I over this month" needed a swipe and a mental sum. The aggregate
 * answers it immediately; the per-category detail lives on Plan, one tap away.
 */
@Composable
private fun BudgetSummaryCard(
    spent: Double,
    limit: Double,
    overCount: Int,
    budgetCount: Int,
    onClick: () -> Unit
) {
    val fraction = if (limit > 0) (spent / limit).toFloat() else 0f
    val over = limit > 0 && spent > limit
    val barColor = when {
        over -> MaterialTheme.colorScheme.error
        fraction >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onBackground
    }

    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
        AuraCard(
            style = AuraCardStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AuraSpacing.lg, vertical = 15.dp),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${formatMoney(spent)} ${stringResource(R.string.home_budget_of, formatMoney(limit))}",
                    style = AuraType.budgetName,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.common_percent, (fraction * 100).toInt()),
                    style = AuraType.budgetAmount,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            AuraProgressBar(
                progress = fraction,
                color = barColor,
                trackColor = MaterialTheme.extendedColors.track
            )
            Spacer(modifier = Modifier.height(AuraSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    pluralStringResource(R.plurals.home_budgets_over, budgetCount, overCount, budgetCount),
                    style = AuraType.budgetSub,
                    color = if (overCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // The right cell always says where the month stands. It used to
                // disappear when the month went over, which left the row looking
                // half-rendered next to the concept; "₹7,467 over" is the same
                // cell answering the same question in the other direction.
                Text(
                    if (over) stringResource(R.string.home_over, formatMoney(spent - limit))
                    else stringResource(R.string.home_left, formatMoney(limit - spent)),
                    style = AuraType.budgetSub,
                    color = if (over) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

/** "Sunday, 18 October" — the date is more useful than the app's own name here. */
@Composable
private fun todayLabel(): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    }
}
