package com.awbuilds.auraspend.domain.repository

import androidx.paging.PagingData
import com.awbuilds.auraspend.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    /** Bounded load: newest [limit] transactions (Activity list). */
    fun getRecentTransactions(limit: Int): Flow<List<Transaction>>

    /**
     * SQL-backed page stream for the Activity list. Query/type/category
     * filtering happens in Room, so each database page is bounded and mapped
     * to domain rows lazily.
     *
     * Default implementation pages an in-memory list (used by test fakes);
     * production overrides it with Room's `PagingSource`.
     */
    fun pagedTransactions(filter: TransactionFilter): Flow<PagingData<Transaction>> =
        getAllTransactions().map { transactions ->
            PagingData.from(transactions.filter(filter::matches))
        }

    /** One transaction by id (the detail/edit screen reads one row, not the table). */
    suspend fun getTransactionById(id: String): Transaction?

    fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>>
    fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>>
    fun getRecurringTransactions(): Flow<List<Transaction>>

    // SQL aggregates — avoid loading rows into the app for summaries.
    fun observeSummary(start: Long, end: Long): Flow<TransactionSummary>
    fun observeBalance(): Flow<Double>
    fun observeExpenseByCategory(start: Long, end: Long): Flow<List<CategoryTotal>>
    fun observeDailyExpense(start: Long, end: Long): Flow<List<DayTotal>>

    suspend fun saveTransaction(transaction: Transaction)
    suspend fun saveTransactions(transactions: List<Transaction>)
    suspend fun deleteTransaction(id: String)

    fun getAllCategories(): Flow<List<Category>>
    suspend fun getCategoryById(id: String): Category?
    suspend fun saveCategory(category: Category)
    suspend fun saveCategories(categories: List<Category>)
    suspend fun deleteCategory(id: String)

    fun getAllBudgets(): Flow<List<Budget>>
    suspend fun getBudgetByCategory(categoryId: String): Budget?
    suspend fun saveBudget(budget: Budget)
    suspend fun updateBudgetSpent(categoryId: String, spent: Double)
    suspend fun deleteBudget(budgetId: String)

    fun getAllSavingsGoals(): Flow<List<SavingsGoal>>
    suspend fun saveSavingsGoal(goal: SavingsGoal)
    suspend fun deleteSavingsGoal(goalId: String)
    suspend fun updateSavingsGoal(goal: SavingsGoal)

    fun getActiveSubscriptions(): Flow<List<Subscription>>

    /**
     * One row per account or card, from the latest balance a message stated.
     * Empty until a message carries an "Avl Bal" — the app then falls back to
     * stating the month's flow rather than inventing a balance.
     */
    fun observeAccounts(): Flow<List<Account>>
    suspend fun saveSubscription(subscription: Subscription)
    suspend fun deleteSubscription(id: String, active: Boolean)
}
