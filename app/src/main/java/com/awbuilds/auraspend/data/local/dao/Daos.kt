package com.awbuilds.auraspend.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.awbuilds.auraspend.data.local.entities.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO interfaces for Room database access.
 */

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /**
     * Paged Activity list. Filtering happens in SQL so no filter combination
     * ever loads more than a page of rows; every ORDER BY is newest-first.
     * NULL parameters mean "no constraint" ([query] may also be blank).
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE dateTimestamp BETWEEN :start AND :end
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (
              :query IS NULL OR :query = ''
              OR note LIKE '%' || :query || '%'
              OR merchant LIKE '%' || :query || '%'
          )
        ORDER BY
          CASE WHEN :ascending THEN dateTimestamp END ASC,
          CASE WHEN NOT :ascending THEN dateTimestamp END DESC
        """
    )
    fun pagedTransactions(
        query: String?,
        type: String?,
        categoryId: String?,
        start: Long,
        end: Long,
        ascending: Boolean
    ): PagingSource<Int, TransactionEntity>

    /**
     * Rows the classifier flagged as recurring.
     *
     * Deliberately kept with **zero production callers**: it is the read that
     * "derive subscriptions from transaction history" needs (the review's
     * Direction 2 — `isRecurring` is already populated, the false-positive
     * guardrails are in the suite, and the Plan hub implicitly promises the
     * subscriptions it would surface). It is documented rather than deleted so
     * the next reader can tell a kept hook from forgotten dead code; anything
     * genuinely superseded was removed in the same pass.
     */
    @Query("SELECT * FROM transactions WHERE isRecurring = 1")
    fun getRecurringTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE dateTimestamp BETWEEN :start AND :end ORDER BY dateTimestamp DESC")
    fun getTransactionsInRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId")
    fun getTransactionsByCategory(categoryId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY dateTimestamp DESC")
    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>>

    @Query(
        "SELECT SUM(amount) FROM transactions " +
            "WHERE type = :type AND dateTimestamp BETWEEN :start AND :end " +
            "AND categoryId != 'cat_transfer'"
    )
    fun getTotalByTypeInRange(type: String, start: Long, end: Long): Flow<Double?>

    // ─── SQL aggregates (dashboard / activity summaries) ─────────────────────

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense,
               SUM(CASE WHEN type = 'INCOME' THEN 1 ELSE 0 END) AS incomeCount,
               SUM(CASE WHEN type = 'EXPENSE' THEN 1 ELSE 0 END) AS expenseCount
        FROM transactions
        WHERE dateTimestamp BETWEEN :start AND :end
          AND categoryId != 'cat_transfer'
        """
    )
    fun observeSummary(start: Long, end: Long): Flow<TransactionSummaryRow>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) " +
            "FROM transactions WHERE categoryId != 'cat_transfer'"
    )
    fun observeBalance(): Flow<Double>

    @Query(
        """
        SELECT categoryId, SUM(amount) AS total
        FROM transactions
        WHERE type = 'EXPENSE' AND dateTimestamp BETWEEN :start AND :end
          AND categoryId != 'cat_transfer'
        GROUP BY categoryId
        """
    )
    fun observeExpenseByCategory(start: Long, end: Long): Flow<List<CategoryTotalRow>>

    @Query(
        """
        SELECT date(dateTimestamp / 1000, 'unixepoch', 'localtime') AS day, SUM(amount) AS total
        FROM transactions
        WHERE type = 'EXPENSE' AND dateTimestamp BETWEEN :start AND :end
          AND categoryId != 'cat_transfer'
        GROUP BY day
        """
    )
    fun observeDailyExpense(start: Long, end: Long): Flow<List<DayTotalRow>>

    /**
     * Inserts a new transaction, ignoring it when a *different* row already owns
     * the same unique `sourceSmsId`.
     *
     * Deliberately not [OnConflictStrategy.REPLACE]: SQLite implements REPLACE as
     * "delete the conflicting row, then insert", and `sourceSmsId` is unique — so
     * a second save of the same SMS-linked transaction (a worker/triage race, a
     * CSV re-import, a re-processed queue row) deleted the row the user had
     * already edited and re-created it from the pipeline's parse. A duplicate is
     * a no-op, not an overwrite.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionIfAbsent(transaction: TransactionEntity): Long

    /**
     * Updates an existing row in place and reports how many rows changed, so a
     * caller can express "update if present, otherwise insert" without REPLACE's
     * delete-then-insert behaviour.
     */
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity): Int

    /**
     * Bulk insert (CSV import, atomic restore). Rows that already exist are left
     * untouched rather than replaced — restore clears the table first, so it
     * never relies on the conflict strategy.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    /**
     * Update-if-present, else insert-if-absent, for a whole batch in ONE
     * transaction (CSV import).
     *
     * The per-row [saveTransaction] path cannot serve a batch: it opens its own
     * transaction per row, so an import that fails halfway would persist the
     * rows already written while the user is told the import failed. Room wraps
     * a `@Transaction` default method in a single transaction, so either the
     * whole file lands or nothing does.
     */
    @Transaction
    suspend fun upsertTransactions(transactions: List<TransactionEntity>) {
        transactions.forEach { entity ->
            if (updateTransaction(entity) == 0) {
                insertTransactionIfAbsent(entity)
            }
        }
    }

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    /**
     * The transaction created from a bank SMS, if any. Undo for "swipe right to
     * save" in the triage inbox needs to find the row it just created, and the
     * only durable link between the two is `sourceSmsId` — the SMS provider's
     * `_id`, which the classifier already stamps onto the transaction.
     */
    @Query("SELECT * FROM transactions WHERE sourceSmsId = :smsId LIMIT 1")
    suspend fun getTransactionBySourceSmsId(smsId: String): TransactionEntity?

    /**
     * One row by primary key. The detail/edit screen reads a single transaction,
     * not the whole table: `id` is the primary key, so this is an indexed lookup
     * rather than the full scan it used to do to find one row.
     */
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    /**
     * One-shot snapshot for the Drive backup. The Flow above feeds the UI; a
     * backup needs one consistent read rather than an observation.
     */
    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC")
    suspend fun getAllOnce(): List<TransactionEntity>

    /** Used only by the atomic Drive restore, which replaces the table wholesale. */
    @Query("DELETE FROM transactions")
    suspend fun clear()
}

/** Aggregate row for a date range: totals plus counts per type. */
data class TransactionSummaryRow(
    val income: Double,
    val expense: Double,
    val incomeCount: Int,
    val expenseCount: Int
)

/** Aggregate row: total expense for one category. */
data class CategoryTotalRow(
    val categoryId: String,
    val total: Double
)

/** Aggregate row: total expense for one local calendar day ("YYYY-MM-DD"). */
data class DayTotalRow(
    val day: String,
    val total: Double
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /** One-shot snapshot for the Drive backup. */
    @Query("SELECT * FROM categories")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: String)

    /** Used only by the atomic Drive restore, which replaces the table wholesale. */
    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    /** One-shot snapshot for the Drive backup. */
    @Query("SELECT * FROM budgets")
    suspend fun getAllOnce(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun getBudgetByCategory(categoryId: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun deleteBudgetById(budgetId: String)

    @Query("UPDATE budgets SET spentAmount = :spent WHERE categoryId = :categoryId")
    suspend fun updateSpentAmount(categoryId: String, spent: Double)

    /** Used only by the atomic Drive restore, which replaces the table wholesale. */
    @Query("DELETE FROM budgets")
    suspend fun clear()
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals")
    fun getAllSavingsGoals(): Flow<List<SavingsGoalEntity>>

    /** One-shot snapshot for the Drive backup. */
    @Query("SELECT * FROM savings_goals")
    suspend fun getAllOnce(): List<SavingsGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity)

    @Delete
    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :goalId")
    suspend fun deleteSavingsGoalById(goalId: String)

    @Update
    suspend fun updateSavingsGoal(goal: SavingsGoalEntity)

    /** Used only by the atomic Drive restore, which replaces the table wholesale. */
    @Query("DELETE FROM savings_goals")
    suspend fun clear()
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE active = 1")
    fun getActiveSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY nextBillingDateTimestamp ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    /** One-shot snapshot for the Drive backup; includes inactive subscriptions. */
    @Query("SELECT * FROM subscriptions ORDER BY nextBillingDateTimestamp ASC")
    suspend fun getAllOnce(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET active = :active WHERE id = :id")
    suspend fun setSubscriptionActive(id: String, active: Boolean)

    /** Used only by the atomic Drive restore, which replaces the table wholesale. */
    @Query("DELETE FROM subscriptions")
    suspend fun clear()
}

/**
 * Bank messages the app could not turn into a transaction.
 *
 * Written only when a message carries a movement verb and an amount marker but
 * no parser could read it — i.e. the message plausibly is a real transaction.
 * Pure OTPs and promotions are filtered earlier and never reach this table.
 */
@Dao
interface UnrecognizedSmsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<UnrecognizedSmsEntity>)

    @Query("SELECT * FROM unrecognized_sms ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<UnrecognizedSmsEntity>>

    @Query("SELECT COALESCE(COUNT(*), 0) FROM unrecognized_sms")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM unrecognized_sms WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM unrecognized_sms")
    suspend fun clear()

    /** Retention sweep: rows older than [cutoff] are not worth surfacing. */
    @Query("DELETE FROM unrecognized_sms WHERE createdAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("SELECT * FROM unrecognized_sms")
    suspend fun getAll(): List<UnrecognizedSmsEntity>
}

@Dao
interface SmsMessageDao {

    /** Idempotent ingest: a message already in the queue is ignored, never duplicated. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<SmsMessageEntity>)

    @Query("SELECT * FROM sms_messages WHERE status = :status ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun getPending(status: String, limit: Int): List<SmsMessageEntity>

    /**
     * How many rows are still waiting on the classifier.
     *
     * Gates the history backfill in `SmsIngestor`: older messages are pulled in
     * one page at a time, and only when there is nothing live left to classify,
     * so a backfill can never grow the queue faster than it drains.
     */
    @Query("SELECT COALESCE(COUNT(*), 0) FROM sms_messages WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    /** Total queue depth — the denominator of the "X of Y" progress readout. */
    @Query("SELECT COALESCE(COUNT(*), 0) FROM sms_messages")
    suspend fun countAll(): Int

    /**
     * Crash recovery for the in-flight marker.
     *
     * A pass that is killed mid-row leaves the row [SmsMessageStatus.CLASSIFYING], and
     * `getPending(NEW, …)` never returns it again — the message would be stranded forever.
     * Every pass sweeps those rows back to [SmsMessageStatus.NEW] before claiming the next one.
     *
     * [SmsMessageStatus.PROCESSED] is deliberately excluded: it is where the undo paths park a
     * row the user just dismissed or un-saved, and re-processing it would silently resurrect a
     * decision the user reversed.
     */
    @Query(
        "UPDATE sms_messages SET status = 'NEW', updatedAt = :now " +
            "WHERE status = 'CLASSIFYING'"
    )
    suspend fun sweepInFlight(now: Long)

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: String): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<SmsMessageEntity>>

    /** One-shot snapshot for the Drive backup. */
    @Query("SELECT * FROM sms_messages ORDER BY receivedAt DESC")
    suspend fun getAllOnce(): List<SmsMessageEntity>

    @Update
    suspend fun update(message: SmsMessageEntity)

    @Query("SELECT COALESCE(MAX(receivedAt), 0) FROM sms_messages")
    suspend fun getMaxReceivedAt(): Long

    /**
     * Used only by the atomic Drive restore. The queue is cleared and re-inserted
     * so a restored row keeps the status the backup recorded — restoring with
     * INSERT OR IGNORE would leave locally-newer rows untouched and never retry
     * messages the backup still had pending.
     */
    @Query("DELETE FROM sms_messages")
    suspend fun clear()
}
