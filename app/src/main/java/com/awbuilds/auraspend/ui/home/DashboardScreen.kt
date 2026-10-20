package com.awbuilds.auraspend.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Subscriptions
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
import java.time.ZoneId

/**
 * Home is a glance that is also a dashboard.
 *
 * The Aurora home (0.1.71) carried a hero, four quick actions, a cash-flow
 * chart, a carousel of budget rings, a subscriptions card and the recent rows.
 * The Quiet Ledger pass reduced it to a bare glance and moved the chart to
 * Insights. Both were right about their own question — and this screen now
 * answers both, in the concept's language rather than Aurora's:
 *
 * - **First screenful**: the hero (what you have), four quick actions (record
 *   something), and the needs-you rail (whatever needs a decision).
 * - **Below it**: the seven-day cash flow, the budgets as rings, the
 *   subscriptions, the account ledger, and today's rows.
 *
 * Everything here is flat paper, hairline edges and ink figures, with colour
 * reserved for data (a category, a status). The one thing the Aurora hero had
 * that this one does not is its gradient: the concept retired the brand sweep,
 * and a flat tonal block says the same thing without it.
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
    // ── The seven days, for the chart. The area chart scales the values against
    // their own maximum, so they stay raw amounts; only the x-axis needs times.
    val weekPoints = remember(state.dailySpend) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        state.dailySpend.mapIndexed { index, amount ->
            today.minusDays((6 - index).toLong()).atStartOfDay(zone).toInstant().toEpochMilli() to amount
        }
    }
    val weekTotal = remember(state.dailySpend) { state.dailySpend.sum() }
    val locale = LocalConfiguration.current.locales[0]
    val weekLabels = remember(state.dailySpend, locale) {
        val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
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
                // The ledger, when a message has stated a balance. Nothing
                // here is invented: a bank row is money the bank says is there,
                // a card row is the credit the card says is left.
                val bankBalance = state.accounts.filter { !it.isCard }.sumOf { it.balance }
                val cardCredit = state.accounts.filter { it.isCard }.sumOf { it.balance }
                BalanceBlock(
                    available = if (state.accounts.any { !it.isCard }) bankBalance else null,
                    cardCredit = if (state.accounts.any { it.isCard }) cardCredit else null,
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

            // ── The cash flow of the last seven days. The area chart the Aurora
            // home carried, in the concept's ink: a quiet day reads as a dip to
            // the baseline rather than a missing column.
            if (weekPoints.any { it.second > 0.0 }) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.home_cash_flow),
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
                        AuraCard(
                            style = AuraCardStyle.Outlined,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onNavigateToAnalytics
                        ) {
                            Spacer(modifier = Modifier.height(AuraSpacing.sm))
                            AuraAreaChart(
                                points = weekPoints,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp),
                                // Ink, not the brand violet: the chart is chrome,
                                // not data, and the concept spends colour on data.
                                lineColor = MaterialTheme.colorScheme.onBackground,
                                fillTop = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                contentDescription = stringResource(R.string.home_cash_flow)
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

            // ── The budgets, one card each with its ring. The month's totals
            // ride in the section header, so the aggregate is not lost: the
            // carousel says which budget, the header says how the month stands.
            if (state.budgets.isNotEmpty()) {
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
                    LazyRow(
                        contentPadding = PaddingValues(
                            start = AuraSpacing.gutter,
                            end = AuraSpacing.gutter
                        ),
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                    ) {
                        items(state.budgets, key = { it.id }) { budget ->
                            val category = state.categories.find { it.id == budget.categoryId }
                            AuraBudgetRingCard(
                                name = category?.name ?: stringResource(R.string.home_unknown_category),
                                emoji = categoryIconGlyph(category?.icon),
                                color = categoryColor(category?.color),
                                spent = budget.spentAmount,
                                limit = budget.limitAmount,
                                onClick = onNavigateToPlan
                            )
                        }
                    }
                }
            }

            // ── The subscriptions, and what they cost a month. Read from the
            // same table the Subscriptions screen reads, so the two cannot
            // disagree about how many there are.
            if (state.activeSubscriptions.isNotEmpty()) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.home_subscriptions),
                        modifier = Modifier.padding(top = AuraSpacing.section)
                    )
                }
                item {
                    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
                        AuraCard(
                            style = AuraCardStyle.Tonal,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = AuraSpacing.lg, vertical = 14.dp),
                            onClick = onNavigateToPlan
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Icon(
                                    Icons.Outlined.Subscriptions,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(AuraSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        pluralStringResource(
                                            R.plurals.home_subscriptions_active,
                                            state.activeSubscriptions.size,
                                            state.activeSubscriptions.size
                                        ),
                                        style = AuraType.rowTitle,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        stringResource(
                                            R.string.home_subscription_monthly,
                                            formatMoney(state.totalSubscriptionCost, fractionDigits = 0)
                                        ),
                                        style = AuraType.rowMeta,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── The ledger: one row per account or card, from the balance the
            // bank last stated. Only shows instruments a message has carried a
            // figure for; nothing is estimated.
            if (state.accounts.isNotEmpty()) {
                item {
                    AuraSectionHeader(
                        title = stringResource(R.string.home_accounts),
                        modifier = Modifier.padding(top = AuraSpacing.section)
                    )
                }
                item {
                    val accountLocale = LocalConfiguration.current.locales[0]
                    AuraCard(
                        style = AuraCardStyle.Outlined,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuraSpacing.gutter)
                    ) {
                        state.accounts.forEachIndexed { index, account ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AuraSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        account.bankName ?: stringResource(
                                            if (account.isCard) R.string.home_account_card
                                            else R.string.home_account_bank
                                        ),
                                        style = AuraType.rowTitle,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        buildString {
                                            if (account.last4 != null) append("•••• ").append(account.last4).append(" · ")
                                            append(
                                                stringResource(
                                                    R.string.home_account_as_of,
                                                    account.asOf.format(
                                                        DateTimeFormatter.ofPattern("d MMM", accountLocale)
                                                    )
                                                )
                                            )
                                        },
                                        style = AuraType.rowMeta,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Air between the meta line and the figure: at a
                                // 200 % font scale the meta wraps, and without
                                // this gap it ran into the amount.
                                Spacer(modifier = Modifier.width(AuraSpacing.sm))
                                Text(
                                    formatMoney(account.balance, fractionDigits = 0),
                                    style = AuraType.moneySmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
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
 * The hero: what you have, then how much of this month's income is gone.
 *
 * Aurora's hero was a gradient block with the balance and two stats split by a
 * hairline. This one keeps the shape — a label, a figure, and two figures
 * either side of a hairline — and drops the gradient: the concept's flat tonal
 * block says the same thing without a brand sweep. The bar between the figure
 * and the stats is the fastest read of "am I ahead this month".
 */
@Composable
private fun BalanceBlock(
    available: Double?,
    cardCredit: Double?,
    balance: Double,
    income: Double,
    expense: Double,
    onOpenAnalytics: () -> Unit
) {
    val fraction = if (income > 0) (expense / income).toFloat() else 0f

    AuraCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.gutter),
        style = AuraCardStyle.Tonal,
        shape = RoundedCornerShape(24.dp),
        onClick = onOpenAnalytics
    ) {
        AuraEyebrow(
            stringResource(
                if (available != null) R.string.home_available else R.string.home_this_month
            )
        )
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        AnimatedMoney(
            amount = available ?: balance,
            style = AuraType.moneyHero,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (available != null && cardCredit != null) {
            Spacer(modifier = Modifier.height(AuraSpacing.xs))
            Text(
                stringResource(
                    R.string.home_card_credit,
                    formatMoney(cardCredit, fractionDigits = 0)
                ),
                style = AuraType.flowCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
        Spacer(modifier = Modifier.height(AuraSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroStat(
                label = stringResource(R.string.home_out_this_month),
                value = formatMoney(expense, fractionDigits = 0),
                modifier = Modifier.weight(1f)
            )
            HeroDivider()
            HeroStat(
                label = stringResource(R.string.home_in_this_month),
                value = formatMoney(income, fractionDigits = 0),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One side of the hero: what it is, and what it is.
 *
 * Aurora's hero stats were a label above a figure, split by a hairline. The
 * label is the metric label (small, quiet) and the figure is money, so the two
 * halves of the hero read as one scale rather than as two different apps.
 *
 * The figure is rounded to whole rupees. Two money strings share one line at a
 * 200 % font scale, and "₹46,006.40" wraps mid-number — which reads as two
 * numbers. The paise are in Activity, where an exact figure belongs; the hero
 * answers "roughly how much", and the ledger card below rounds the same way.
 */
@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = AuraType.metricLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = AuraType.moneySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

/** The hairline between the hero's two stats — the concept's `lineSoft`. */
@Composable
private fun HeroDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = AuraSpacing.sm)
            .width(1.dp)
            .heightIn(min = 30.dp)
            .background(MaterialTheme.extendedColors.lineSoft)
    )
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
