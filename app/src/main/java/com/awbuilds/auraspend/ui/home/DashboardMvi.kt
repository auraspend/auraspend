package com.awbuilds.auraspend.ui.home

import com.awbuilds.auraspend.ui.core.UiError

import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction

data class DashboardViewState(
    val totalBalance: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val monthlyIncomeCount: Int = 0,
    val monthlyExpenseCount: Int = 0,
    val recentTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    /**
     * Messages the categoriser could not place — the "needs you" rail's second
     * card, and the one screen that can answer them.
     */
    val reviewQueueCount: Int = 0,
    val categoryMonthTotals: List<Pair<String, Double>> = emptyList(),
    /**
     * The last seven days of spending, oldest first with today last. Raw
     * amounts, not normalised — the chart scales them against their own maximum.
     *
     * It exists because Home used to look empty on a day with no transactions:
     * the "Today" list is the only thing below the budgets card, so a quiet day
     * left a hole where the app's job — showing where the money went — should be.
     */
    val dailySpend: List<Double> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiError? = null
)

sealed class DashboardViewIntent {
    object LoadDashboard : DashboardViewIntent()
    data class RefreshTransactions(val force: Boolean) : DashboardViewIntent()
}

data class SpendingChartData(
    val days: List<String>,
    val amounts: List<Double>
)
