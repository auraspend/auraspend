package com.awbuilds.auraspend.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.awbuilds.auraspend.data.local.toDomain
import com.awbuilds.auraspend.data.local.toEntity
import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.data.privacy.SensitiveDataMasker
import com.awbuilds.auraspend.data.local.dao.BudgetDao
import com.awbuilds.auraspend.data.local.dao.CategoryDao
import com.awbuilds.auraspend.data.local.dao.SavingsGoalDao
import com.awbuilds.auraspend.data.local.dao.SubscriptionDao
import com.awbuilds.auraspend.data.local.dao.TransactionDao
import com.awbuilds.auraspend.domain.model.*
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val subscriptionDao: SubscriptionDao,
    private val savingsGoalDao: SavingsGoalDao
) : TransactionRepository {

    override fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions().map { it.map { e -> e.toDomain() } }

    override fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsInRange(start, end).map { it.map { e -> e.toDomain() } }

    override suspend fun getTransactionById(id: String): Transaction? =
        transactionDao.getTransactionById(id)?.toDomain()

    override fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(categoryId).map { it.map { e -> e.toDomain() } }

    override fun getRecurringTransactions(): Flow<List<Transaction>> =
        transactionDao.getRecurringTransactions().map { it.map { e -> e.toDomain() } }

    override fun getRecentTransactions(limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecent(limit).map { it.map { e -> e.toDomain() } }

    /**
     * Room-backed paging for the Activity list. Each [PagingConfig] page reads
     * at most [PAGED_PAGE_SIZE] filtered rows on the Room query executor and is
     * mapped to domain rows as it is loaded.
     */
    override fun pagedTransactions(filter: TransactionFilter): Flow<PagingData<Transaction>> =
        Pager(
            config = PagingConfig(pageSize = PAGED_PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                transactionDao.pagedTransactions(
                    query = filter.query.trim().ifBlank { null },
                    type = filter.type?.name,
                    categoryId = filter.categoryId
                )
            }
        ).flow.map { pagingData ->
            pagingData.map { entity -> entity.toDomain() }
        }

    override fun observeSummary(start: Long, end: Long): Flow<TransactionSummary> =
        transactionDao.observeSummary(start, end).map {
            TransactionSummary(
                income = it.income,
                expense = it.expense,
                incomeCount = it.incomeCount,
                expenseCount = it.expenseCount
            )
        }

    override fun observeBalance(): Flow<Double> = transactionDao.observeBalance()

    override fun observeExpenseByCategory(start: Long, end: Long): Flow<List<CategoryTotal>> =
        transactionDao.observeExpenseByCategory(start, end).map { rows ->
            rows.map { CategoryTotal(categoryId = it.categoryId, amount = it.total) }
        }

    override fun observeDailyExpense(start: Long, end: Long): Flow<List<DayTotal>> =
        transactionDao.observeDailyExpense(start, end).map { rows ->
            rows.mapNotNull { row ->
                val date = boundaryOrNull("TransactionRepository") { java.time.LocalDate.parse(row.day) }
                date?.let { DayTotal(it, row.total) }
            }
        }

    /**
     * PRIVACY: notes and merchant names are passed through [SensitiveDataMasker] before
     * touching the database. This is the single choke-point for every write path —
     * SMS pipeline, Smart Add, manual entry, CSV import and Drive restore all land here.
     */
    private fun Transaction.sanitized(): Transaction = copy(
        note = SensitiveDataMasker.mask(note),
        merchant = merchant?.let { SensitiveDataMasker.mask(it) }
    )

    /**
     * Updates an existing row in place, otherwise inserts it.
     *
     * A plain insert cannot serve both intents: an edit arrives with the row's
     * existing id, and a pipeline save arrives with a fresh id but a
     * `sourceSmsId` another row may already claim. `REPLACE` resolved the second
     * case by deleting the user's row; `INSERT OR IGNORE` alone would resolve the
     * first by dropping the edit. So: update first (0 rows changed means the id is
     * new), then insert-if-absent.
     */
    override suspend fun saveTransaction(transaction: Transaction) {
        val entity = transaction.sanitized().toEntity()
        if (transactionDao.updateTransaction(entity) == 0) {
            transactionDao.insertTransactionIfAbsent(entity)
        }
    }

    /**
     * Batch save (CSV import) with the same update-or-insert semantics as
     * [saveTransaction], in ONE transaction.
     *
     * A per-row loop would make the import non-atomic: a failure halfway
     * persists the rows already written while the caller reports failure, and
     * the user cannot tell which of their file's rows landed. [upsertTransactions]
     * puts the whole batch behind a single Room transaction, so the CSV import
     * is all-or-nothing like the bulk insert it replaced.
     */
    override suspend fun saveTransactions(transactions: List<Transaction>) {
        transactionDao.upsertTransactions(transactions.map { it.sanitized().toEntity() })
    }

    override suspend fun deleteTransaction(id: String) {
        transactionDao.deleteTransactionById(id)
    }

    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { it.map { e -> e.toDomain() } }

    override suspend fun getCategoryById(id: String): Category? =
        categoryDao.getCategoryById(id)?.toDomain()

    override suspend fun saveCategory(category: Category) {
        categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun saveCategories(categories: List<Category>) {
        categoryDao.insertCategories(categories.map { it.toEntity() })
    }

    override suspend fun deleteCategory(id: String) {
        categoryDao.deleteCategoryById(id)
    }

    override fun getAllBudgets(): Flow<List<Budget>> =
        budgetDao.getAllBudgets().map { it.map { e -> e.toDomain() } }

    override suspend fun getBudgetByCategory(categoryId: String): Budget? =
        budgetDao.getBudgetByCategory(categoryId)?.toDomain()

    override suspend fun saveBudget(budget: Budget) {
        budgetDao.insertBudget(budget.toEntity())
    }

    override suspend fun updateBudgetSpent(categoryId: String, spent: Double) {
        budgetDao.updateSpentAmount(categoryId, spent)
    }

    override suspend fun deleteBudget(budgetId: String) {
        budgetDao.deleteBudgetById(budgetId)
    }

    override fun getAllSavingsGoals(): Flow<List<SavingsGoal>> =
        savingsGoalDao.getAllSavingsGoals().map { it.map { e -> e.toDomain() } }

    override suspend fun saveSavingsGoal(goal: SavingsGoal) {
        savingsGoalDao.insertSavingsGoal(goal.toEntity())
    }

    override suspend fun deleteSavingsGoal(goalId: String) {
        savingsGoalDao.deleteSavingsGoalById(goalId)
    }

    override suspend fun updateSavingsGoal(goal: SavingsGoal) {
        savingsGoalDao.updateSavingsGoal(goal.toEntity())
    }

    override fun getActiveSubscriptions(): Flow<List<Subscription>> =
        subscriptionDao.getActiveSubscriptions().map { it.map { e -> e.toDomain() } }

    override suspend fun saveSubscription(subscription: Subscription) {
        subscriptionDao.insertSubscription(subscription.toEntity())
    }

    override suspend fun deleteSubscription(id: String, active: Boolean) {
        subscriptionDao.setSubscriptionActive(id, active)
    }

    private companion object {
        /** Activity list page size (rows read per Room query). */
        const val PAGED_PAGE_SIZE = 50
    }
}
