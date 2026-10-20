package com.awbuilds.auraspend

import com.awbuilds.auraspend.domain.model.CategoryTotal
import com.awbuilds.auraspend.domain.model.DayTotal
import com.awbuilds.auraspend.domain.model.TransactionSummary
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.domain.model.toEpochRange
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Shared default implementations of the aggregate queries for test fakes.
 *
 * Fakes only need to implement the list-returning methods; the aggregates are
 * derived in memory so tests do not duplicate SQL-equivalent logic.
 */
interface TestTransactionRepositoryDefaults : TransactionRepository {

    override fun getRecentTransactions(limit: Int): Flow<List<com.awbuilds.auraspend.domain.model.Transaction>> =
        getAllTransactions().map { it.take(limit) }

    /** No ledger unless a test gives one: the app falls back to the month's flow. */
    override fun observeAccounts(): Flow<List<com.awbuilds.auraspend.domain.model.Account>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeSummary(start: Long, end: Long): Flow<TransactionSummary> =
        getTransactionsInRange(start, end).map { list ->
            TransactionSummary(
                income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                incomeCount = list.count { it.type == TransactionType.INCOME },
                expenseCount = list.count { it.type == TransactionType.EXPENSE }
            )
        }

    /**
     * The filtered totals, summed in memory so a fake answers the same
     * question the SQL does: what the list under the header would add up to.
     */
    override fun observeSummary(filter: com.awbuilds.auraspend.domain.model.TransactionFilter): Flow<TransactionSummary> {
        val (start, end) = filter.month.toEpochRange(java.time.ZoneId.systemDefault())
        return getTransactionsInRange(start, end).map { list ->
            val matching = list.filter { txn ->
                (filter.type == null || txn.type == filter.type) &&
                    (filter.categoryId == null || txn.categoryId == filter.categoryId) &&
                    (filter.query.isBlank() ||
                        txn.note.contains(filter.query, ignoreCase = true) ||
                        (txn.merchant?.contains(filter.query, ignoreCase = true) ?: false))
            }
            TransactionSummary(
                income = matching.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = matching.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                incomeCount = matching.count { it.type == TransactionType.INCOME },
                expenseCount = matching.count { it.type == TransactionType.EXPENSE }
            )
        }
    }

    override fun observeBalance(): Flow<Double> =
        getAllTransactions().map { list ->
            list.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
        }

    override fun observeExpenseByCategory(start: Long, end: Long): Flow<List<CategoryTotal>> =
        getTransactionsInRange(start, end).map { list ->
            list.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .map { (categoryId, items) -> CategoryTotal(categoryId, items.sumOf { it.amount }) }
        }

    override fun observeDailyExpense(start: Long, end: Long): Flow<List<DayTotal>> =
        getTransactionsInRange(start, end).map { list ->
            list.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.date.toLocalDate() }
                .map { (date, items) -> DayTotal(date, items.sumOf { it.amount }) }
        }
}
