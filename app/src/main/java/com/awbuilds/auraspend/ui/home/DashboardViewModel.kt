package com.awbuilds.auraspend.ui.home

import com.awbuilds.auraspend.ui.core.UiError

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.data.local.UnrecognizedSmsRepository
import com.awbuilds.auraspend.domain.model.Account
import com.awbuilds.auraspend.domain.model.Budget

import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.DayTotal
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
    private val repository: TransactionRepository,
    /**
     * The review queue, for the "needs you" rail. Optional so tests and previews
     * can build the ViewModel without one — the rail simply stays hidden.
     */
    private val unrecognizedSms: UnrecognizedSmsRepository? = null
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardViewState())
    val state: StateFlow<DashboardViewState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        observeReviewQueue()
    }

    fun handleIntent(intent: DashboardViewIntent) {
        when (intent) {
            // `LoadDashboard` is fired from a `LaunchedEffect(Unit)` on the screen,
            // so it runs again every time the composition is recreated — i.e. on
            // every return to Home from a detail screen. The ViewModel outlives
            // that (it is scoped to the nav entry), and [loadDashboard]'s job is a
            // live Room subscription, so a second load cancels a perfectly good
            // stream and re-runs four aggregate queries *during* the transition.
            // Only an explicit refresh restarts it.
            is DashboardViewIntent.LoadDashboard -> loadDashboard(force = false)
            is DashboardViewIntent.RefreshTransactions -> loadDashboard(force = true)
        }
    }

    /**
     * The count of messages the categoriser could not place. A failure leaves
     * the count at zero — a hidden card, not a crashed dashboard; the queue
     * screen itself reports its own errors.
     */
    private fun observeReviewQueue() {
        val source = unrecognizedSms ?: return
        viewModelScope.launch {
            source.observeCount()
                .catch { e -> AuraLog.e(TAG, "Review queue count failed", e) }
                .collect { count -> _state.update { it.copy(reviewQueueCount = count) } }
        }
    }

    /**
     * Reactive dashboard driven by **SQL aggregates**.
     *
     * Nothing loads the full transaction table: summaries and category totals
     * come from indexed `SUM/GROUP BY` queries that Room re-runs on invalidation,
     * and recent activity is a bounded `LIMIT 20` query. Budget spend is derived
     * from the same category totals per period.
     */
    private fun loadDashboard(force: Boolean) {
        // A running collection is already observing every table this screen reads,
        // so re-subscribing changes nothing except the cost.
        if (!force && loadJob?.isActive == true) return
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true) }

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        fun epoch(dateTime: LocalDateTime): Long =
            dateTime.atZone(zone).toInstant().toEpochMilli()

        val (monthStart, monthEnd) = periodRange(BudgetPeriod.MONTHLY, now)
        val (weekStart, weekEnd) = periodRange(BudgetPeriod.WEEKLY, now)
        val (yearStart, yearEnd) = periodRange(BudgetPeriod.YEARLY, now)
        // Seven days ending today, not a calendar week: a Monday-to-Sunday window
        // reads empty every Monday (handoff 0043).
        val week7Start = now.toLocalDate().minusDays(6).atStartOfDay()
        val week7End = now.toLocalDate().plusDays(1).atStartOfDay()

        val aggregates = combine(
            repository.observeSummary(epoch(monthStart), epoch(monthEnd)),
            repository.observeBalance(),
            repository.observeExpenseByCategory(epoch(monthStart), epoch(monthEnd)),
            repository.observeExpenseByCategory(epoch(weekStart), epoch(weekEnd)),
            repository.observeDailyExpense(epoch(week7Start), epoch(week7End))
        ) { summary, balance, monthCats, weekCats, dailyWeek ->
            Aggregates(
                summary = summary,
                balance = balance,
                monthlyByCategory = monthCats.associate { it.categoryId to it.amount },
                weeklyByCategory = weekCats.associate { it.categoryId to it.amount },
                dailyWeek = dailyWeek
            )
        }

        val supporting = combine(
            repository.getRecentTransactions(RECENT_LIMIT),
            repository.getAllCategories(),
            repository.getAllBudgets(),
            repository.observeExpenseByCategory(epoch(yearStart), epoch(yearEnd)),
            repository.observeAccounts()
        ) { recent, categories, budgets, yearCats, accounts ->
            Supporting(
                recent = recent,
                categories = categories,
                budgets = budgets,
                yearlyByCategory = yearCats.associate { it.categoryId to it.amount },
                accounts = accounts
            )
        }

        loadJob = viewModelScope.launch {
            combine(aggregates, supporting) { agg, sup -> agg to sup }
                .catch { e ->
                    AuraLog.e(TAG, "Dashboard aggregation failed", e)
                    // Report a reason, not e.message: the raw message reaches the user.
                    _state.update { it.copy(isLoading = false, error = UiError.LOAD_FAILED) }
                }
                .collect { (agg, sup) -> publish(agg, sup) }
        }
    }

    private fun publish(
        agg: Aggregates,
        sup: Supporting
    ) {
        val budgets = sup.budgets.map { budget ->
            val totals = when (budget.period) {
                BudgetPeriod.WEEKLY -> agg.weeklyByCategory
                BudgetPeriod.MONTHLY -> agg.monthlyByCategory
                BudgetPeriod.YEARLY -> sup.yearlyByCategory
            }
            budget.copy(spentAmount = totals[budget.categoryId] ?: 0.0)
        }

        // Oldest first, today last, with the quiet days present as zeroes so the
        // strip keeps its seven columns.
        val today = LocalDate.now()
        val byDay = agg.dailyWeek.associate { it.date to it.amount }
        val dailySpend = (6 downTo 0).map { back -> byDay[today.minusDays(back.toLong())] ?: 0.0 }

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
                categoryMonthTotals = categoryTotals,
                dailySpend = dailySpend,
                accounts = sup.accounts,
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
        val dailyWeek: List<DayTotal>
    )

    private data class Supporting(
        val recent: List<Transaction>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val yearlyByCategory: Map<String, Double>,
        val accounts: List<Account>
    )

    private companion object {
        const val TAG = "Dashboard"
        const val RECENT_LIMIT = 20
    }
}
