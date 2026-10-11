package com.awbuilds.auraspend.ui.budget

import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.ui.core.UiError

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.domain.model.BudgetSpending
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class BudgetViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetViewState())
    val state: StateFlow<BudgetViewState> = _state.asStateFlow()

    /** The live aggregate collector, restarted by a retry or a delete. */
    private var loadJob: Job? = null

    fun handleIntent(intent: BudgetViewIntent) {
        when (intent) {
            is BudgetViewIntent.LoadBudgets -> loadBudgets()
            is BudgetViewIntent.SelectCategory -> _state.update { it.copy(selectedCategoryId = intent.categoryId) }
            is BudgetViewIntent.AmountChanged -> _state.update { it.copy(limitAmount = intent.amount) }
            is BudgetViewIntent.PeriodChanged -> _state.update { it.copy(selectedPeriod = intent.period) }
            is BudgetViewIntent.SaveBudget -> saveBudget()
            is BudgetViewIntent.DeleteBudget -> deleteBudget(intent.budgetId)
            is BudgetViewIntent.StartAdd -> {
                _state.update { it.copy(editingBudget = null, selectedCategoryId = "", limitAmount = "", selectedPeriod = BudgetPeriod.MONTHLY, isAdding = true) }
            }
            is BudgetViewIntent.CancelEdit -> _state.update { it.copy(editingBudget = null, isAdding = false) }

            is BudgetViewIntent.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    /**
     * Reactive budgets driven by **indexed SQL aggregates**, the same ones the
     * Dashboard reads (ADR 0003).
     *
     * This used to load every budget, every category *and the whole transaction
     * table* on each entry, then recompute spend with `BudgetSpending.withFreshSpent`
     * — the pattern ADR 0003 restricts to small tables and tests. The full-table scan
     * is gone: spend now comes from `observeExpenseByCategory` over the budget's own
     * calendar period (weekly/monthly/yearly), so a budget reflects a new SMS expense
     * without a reload and the three screens that show this number cannot diverge.
     */
    private fun loadBudgets() {
        loadJob?.cancel()
        val now = LocalDateTime.now()
        val weekly = BudgetSpending.periodEpochRange(BudgetPeriod.WEEKLY, now)
        val monthly = BudgetSpending.periodEpochRange(BudgetPeriod.MONTHLY, now)
        val yearly = BudgetSpending.periodEpochRange(BudgetPeriod.YEARLY, now)

        loadJob = viewModelScope.launch {
            combine(
                repository.getAllBudgets(),
                repository.getAllCategories(),
                repository.observeExpenseByCategory(weekly.first, weekly.second),
                repository.observeExpenseByCategory(monthly.first, monthly.second),
                repository.observeExpenseByCategory(yearly.first, yearly.second)
            ) { budgets, categories, weekTotals, monthTotals, yearTotals ->
                BudgetsSnapshot(
                    budgets = budgets,
                    categories = categories,
                    weeklyByCategory = weekTotals.associate { it.categoryId to it.amount },
                    monthlyByCategory = monthTotals.associate { it.categoryId to it.amount },
                    yearlyByCategory = yearTotals.associate { it.categoryId to it.amount }
                )
            }
                // The Flow equivalent of `boundaryOrNull`: a failed aggregate read
                // reports a typed reason instead of reaching the UI as a crash.
                .catch { e ->
                    AuraLog.e(TAG, "Loading budgets failed", e)
                    _state.update { it.copy(error = UiError.LOAD_FAILED) }
                }
                .collect { snapshot ->
                    _state.update {
                        it.copy(
                            budgets = snapshot.budgets.map { budget ->
                                budget.copy(spentAmount = snapshot.spentFor(budget))
                            },
                            categories = snapshot.categories,
                            // A successful read clears a previous load failure. A write
                            // failure is cleared where the write itself succeeds.
                            error = if (it.error == UiError.LOAD_FAILED) null else it.error
                        )
                    }
                }
        }
    }

    private fun saveBudget() {
        viewModelScope.launch {
            val s = _state.value
            val amount = s.limitAmount.toDoubleOrNull()
            if (amount == null || s.selectedCategoryId.isBlank()) {
                // The button is disabled in this state; this is the defensive path,
                // and it reports through the same surface as a failed write.
                _state.update { it.copy(error = UiError.SAVE_FAILED, isSaving = false) }
                return@launch
            }

            val budget = Budget(
                id = s.editingBudget?.id ?: java.util.UUID.randomUUID().toString(),
                categoryId = s.selectedCategoryId,
                limitAmount = amount,
                spentAmount = 0.0, // recomputed on load from the aggregates
                period = s.selectedPeriod
            )

            // The stored `spentAmount` column is still written for the backup, but
            // from the aggregate — not from a full-table scan. If the aggregate
            // cannot be read the save is reported as failed rather than persisting
            // a number that was never read.
            val spent = spentForCategory(budget.categoryId, budget.period)
            if (spent == null) {
                _state.update { it.copy(error = UiError.SAVE_FAILED, isSaving = false) }
                return@launch
            }

            val saved = boundary(TAG, false) {
                repository.saveBudget(budget.copy(spentAmount = spent))
                true
            }
            if (saved) {
                // The sheet closes only now: a failed write keeps the form on screen
                // with the banner explaining why.
                _state.update {
                    it.copy(
                        editingBudget = null,
                        selectedCategoryId = "",
                        limitAmount = "",
                        isAdding = false,
                        isSaving = false,
                        error = null
                    )
                }
                loadBudgets()
            } else {
                _state.update { it.copy(error = UiError.SAVE_FAILED, isSaving = false) }
            }
        }
    }

    private fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            if (_state.value.budgets.none { it.id == budgetId }) return@launch
            val deleted = boundary(TAG, false) {
                repository.deleteBudget(budgetId)
                true
            }
            if (deleted) {
                _state.update { it.copy(error = null) }
                loadBudgets()
            } else {
                _state.update { it.copy(error = UiError.DELETE_FAILED) }
            }
        }
    }

    /**
     * Spend for [categoryId] in its budget's current period.
     *
     * Returns null when the aggregate could not be read, so the caller can refuse
     * the write instead of storing a zero the database never confirmed.
     */
    private suspend fun spentForCategory(categoryId: String, period: BudgetPeriod): Double? {
        val (start, end) = BudgetSpending.periodEpochRange(period)
        val totals = boundaryOrNull(TAG) {
            repository.observeExpenseByCategory(start, end).first()
        } ?: return null
        return totals.firstOrNull { it.categoryId == categoryId }?.amount ?: 0.0
    }

    /** One emission of every flow a budget screen needs. */
    private data class BudgetsSnapshot(
        val budgets: List<Budget>,
        val categories: List<Category>,
        val weeklyByCategory: Map<String, Double>,
        val monthlyByCategory: Map<String, Double>,
        val yearlyByCategory: Map<String, Double>
    ) {
        fun spentFor(budget: Budget): Double {
            val totals = when (budget.period) {
                BudgetPeriod.WEEKLY -> weeklyByCategory
                BudgetPeriod.MONTHLY -> monthlyByCategory
                BudgetPeriod.YEARLY -> yearlyByCategory
            }
            return totals[budget.categoryId] ?: 0.0
        }
    }
}

private const val TAG = "BudgetViewModel"
