package com.awbuilds.auraspend.ui.home

import com.awbuilds.auraspend.ui.core.UiError

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.RecurringCost
import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Subscription
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionSummary
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class DashboardViewModel(
    private val repository: TransactionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardViewState())
    val state: StateFlow<DashboardViewState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun handleIntent(intent: DashboardViewIntent) {
        when (intent) {
            is DashboardViewIntent.LoadDashboard -> loadDashboard()
            is DashboardViewIntent.RefreshTransactions -> loadDashboard()
        }
    }

    /**
     * Reactive dashboard driven by **SQL aggregates**.
     *
     * Nothing loads the full transaction table: summaries, category totals and
     * the weekly chart come from indexed `SUM/GROUP BY` queries that Room
     * re-runs on invalidation, and recent activity is a bounded `LIMIT 20`
     * query. Budget spend is derived from the same category totals per period.
     */
    private fun loadDashboard() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true) }

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        fun epoch(dateTime: LocalDateTime): Long =
            dateTime.atZone(zone).toInstant().toEpochMilli()

        val (monthStart, monthEnd) = periodRange(BudgetPeriod.MONTHLY, now)
        val (weekStart, weekEnd) = periodRange(BudgetPeriod.WEEKLY, now)
        val (yearStart, yearEnd) = periodRange(BudgetPeriod.YEARLY, now)
        // The chart is a rolling window ending today, not the calendar week: a
        // calendar week reads as an empty chart from Monday until the next spend
        // (the owner hit it with the latest spend six days old), while the
        // weekly *budget* window below stays the calendar week on purpose.
        val (chartStart, chartEnd) = chartWindow(now)
        val chartDates = (0..6).map { chartStart.plusDays(it.toLong()).toLocalDate() }

        val aggregates = combine(
            repository.observeSummary(epoch(monthStart), epoch(monthEnd)),
            repository.observeBalance(),
            repository.observeExpenseByCategory(epoch(monthStart), epoch(monthEnd)),
            repository.observeDailyExpense(epoch(chartStart), epoch(chartEnd)),
            repository.observeExpenseByCategory(epoch(weekStart), epoch(weekEnd))
        ) { summary, balance, monthCats, daily, weekCats ->
            Aggregates(
                summary = summary,
                balance = balance,
                monthlyByCategory = monthCats.associate { it.categoryId to it.amount },
                weeklyByCategory = weekCats.associate { it.categoryId to it.amount },
                dailyByDate = daily.associate { it.date to it.amount }
            )
        }

        val supporting = combine(
            repository.getRecentTransactions(RECENT_LIMIT),
            repository.getAllCategories(),
            repository.getAllBudgets(),
            repository.getActiveSubscriptions(),
            repository.observeExpenseByCategory(epoch(yearStart), epoch(yearEnd))
        ) { recent, categories, budgets, subscriptions, yearCats ->
            Supporting(
                recent = recent,
                categories = categories,
                budgets = budgets,
                subscriptions = subscriptions,
                yearlyByCategory = yearCats.associate { it.categoryId to it.amount }
            )
        }

        loadJob = viewModelScope.launch {
            combine(aggregates, supporting) { agg, sup -> agg to sup }
                .catch { e ->
                    AuraLog.e(TAG, "Dashboard aggregation failed", e)
                    // Report a reason, not e.message: the raw message reaches the user.
                    _state.update { it.copy(isLoading = false, error = UiError.LOAD_FAILED) }
                }
                .collect { (agg, sup) -> publish(agg, sup, chartDates, zone) }
        }
    }

    private fun publish(
        agg: Aggregates,
        sup: Supporting,
        chartDates: List<LocalDate>,
        zone: ZoneId
    ) {
        val budgets = sup.budgets.map { budget ->
            val totals = when (budget.period) {
                BudgetPeriod.WEEKLY -> agg.weeklyByCategory
                BudgetPeriod.MONTHLY -> agg.monthlyByCategory
                BudgetPeriod.YEARLY -> sup.yearlyByCategory
            }
            budget.copy(spentAmount = totals[budget.categoryId] ?: 0.0)
        }

        val dailySpending = chartDates.map { day ->
            day.atStartOfDay(zone).toInstant().toEpochMilli() to
                (agg.dailyByDate[day] ?: 0.0)
        }

        val categoryTotals = agg.monthlyByCategory.entries
            .map { it.key to it.value }
            .sortedByDescending { it.second }

        _state.update {
            it.copy(
                totalBalance = agg.balance,
                monthlyIncome = agg.summary.income,
                monthlyExpense = agg.summary.expense,
                monthlyIncomeCount = agg.summary.incomeCount,
                monthlyExpenseCount = agg.summary.expenseCount,
                recentTransactions = sup.recent,
                categories = sup.categories,
                budgets = budgets,
                activeSubscriptions = sup.subscriptions,
                // Monthly-equivalent, not the raw sum. Summing raw amounts counted
                // a YEARLY charge twelve times over, so this card disagreed with
                // the Plan hub about the same figure. See RecurringCost.
                totalSubscriptionCost = RecurringCost.monthlyTotal(sup.subscriptions),
                dailySpending = dailySpending,
                categoryMonthTotals = categoryTotals,
                isLoading = false,
                error = null
            )
        }
    }

    /** Inclusive [start, end] range of the calendar period containing [now]. */
    private fun periodRange(period: BudgetPeriod, now: LocalDateTime): Pair<LocalDateTime, LocalDateTime> {
        val start = when (period) {
            BudgetPeriod.WEEKLY -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(LocalTime.MIN)
            BudgetPeriod.MONTHLY -> now.withDayOfMonth(1).with(LocalTime.MIN)
            BudgetPeriod.YEARLY -> now.withDayOfYear(1).with(LocalTime.MIN)
        }
        val end = when (period) {
            BudgetPeriod.WEEKLY -> now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(LocalTime.MAX)
            BudgetPeriod.MONTHLY -> now.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX)
            BudgetPeriod.YEARLY -> now.with(TemporalAdjusters.lastDayOfYear()).with(LocalTime.MAX)
        }
        return start to end
    }

    private data class Aggregates(
        val summary: TransactionSummary,
        val balance: Double,
        val monthlyByCategory: Map<String, Double>,
        val weeklyByCategory: Map<String, Double>,
        val dailyByDate: Map<LocalDate, Double>
    )

    private data class Supporting(
        val recent: List<Transaction>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val subscriptions: List<Subscription>,
        val yearlyByCategory: Map<String, Double>
    )

    private companion object {
        const val TAG = "Dashboard"
        const val RECENT_LIMIT = 20
    }
}

/**
 * The homepage chart's range: the last seven days ending today, both bounds
 * inclusive.
 *
 * Not the calendar week: on a calendar week the chart summarised Mon–Sun, so
 * from Monday morning it read "no spending" even with the latest spend hours or
 * days old (the owner's device: last spend on Friday, chart empty on
 * Wednesday). The weekly budget window is a different thing and stays the
 * calendar week.
 */
internal fun chartWindow(now: LocalDateTime): Pair<LocalDateTime, LocalDateTime> =
    now.toLocalDate().minusDays(6).atStartOfDay() to now.toLocalDate().atTime(LocalTime.MAX)
