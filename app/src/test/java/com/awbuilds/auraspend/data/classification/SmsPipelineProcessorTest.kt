package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.local.dao.SmsMessageDao
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.SavingsGoal
import com.awbuilds.auraspend.domain.model.Subscription
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the queue pipeline contract: messages advance one-at-a-time to a terminal state,
 * OTP/alert and unparsable messages are skipped (never saved), duplicates via the unique
 * sourceSmsId index become a terminal skip, and transient failures retry up to maxAttempts.
 */
class SmsPipelineProcessorTest {

    private inner class FakeSmsMessageDao : SmsMessageDao {
        val rows = mutableMapOf<String, SmsMessageEntity>()

        override suspend fun insertAll(messages: List<SmsMessageEntity>) {
            messages.forEach { if (!rows.containsKey(it.id)) rows[it.id] = it }
        }

        override suspend fun getPending(status: String, limit: Int): List<SmsMessageEntity> =
            rows.values.filter { it.status == status }.take(limit)

        override suspend fun getById(id: String): SmsMessageEntity? = rows[id]

        override fun observeAll(): Flow<List<SmsMessageEntity>> =
            MutableStateFlow(rows.values.toList())

        override suspend fun update(message: SmsMessageEntity) {
            rows[message.id] = message
        }

        override suspend fun getMaxReceivedAt(): Long = rows.values.maxOfOrNull { it.receivedAt } ?: 0L

        override suspend fun countByStatus(status: String): Int =
            rows.values.count { it.status == status }

        override suspend fun countAll(): Int = rows.size

        // Crash recovery: rows a killed pass left mid-flight become processable again.
        override suspend fun sweepInFlight(now: Long) {
            rows.replaceAll { _, row ->
                if (row.status == SmsMessageStatus.CLASSIFYING.name) {
                    row.copy(status = SmsMessageStatus.NEW.name, updatedAt = now)
                } else {
                    row
                }
            }
        }

        // Added for the atomic Drive restore path; unused by these tests.
        override suspend fun clear() = rows.clear()

        // Added for the Drive backup path; unused by these tests.
        override suspend fun getAllOnce(): List<SmsMessageEntity> = rows.values.toList()
    }

    private class FakeTransactionRepository : com.awbuilds.auraspend.TestTransactionRepositoryDefaults {
        val saved = mutableListOf<Transaction>()
        var failWith: Exception? = null

        private val categories = listOf(
            Category(id = "cat_food", name = "Food & Dining", icon = "restaurant", color = 0, isDefault = true),
            Category(id = "cat_other", name = "Other", icon = "category", color = 0, isDefault = true)
        )

        override fun getAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(saved)
        override suspend fun getTransactionById(id: String): Transaction? = saved.firstOrNull { it.id == id }
        override fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> =
            MutableStateFlow(saved)
        override fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>> =
            MutableStateFlow(saved)
        override fun getRecurringTransactions(): Flow<List<Transaction>> =
            MutableStateFlow(saved)
        override suspend fun saveTransaction(transaction: Transaction) {
            failWith?.let { throw it }
            saved.add(transaction)
        }
        override suspend fun saveTransactions(transactions: List<Transaction>) {
            failWith?.let { throw it }
            saved.addAll(transactions)
        }
        override suspend fun deleteTransaction(id: String) {}
        override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(categories)
        override suspend fun getCategoryById(id: String): Category? = categories.find { it.id == id }
        override suspend fun saveCategory(category: Category) {}
        override suspend fun saveCategories(categories: List<Category>) {}
        override suspend fun deleteCategory(id: String) {}
        override fun getAllBudgets(): Flow<List<Budget>> = MutableStateFlow(emptyList())
        override suspend fun getBudgetByCategory(categoryId: String): Budget? = null
        override suspend fun saveBudget(budget: Budget) {}
        override suspend fun updateBudgetSpent(categoryId: String, spent: Double) {}
        override suspend fun deleteBudget(budgetId: String) {}
        override fun getAllSavingsGoals(): Flow<List<SavingsGoal>> = MutableStateFlow(emptyList())
        override suspend fun saveSavingsGoal(goal: SavingsGoal) {}
        override suspend fun deleteSavingsGoal(goalId: String) {}
        override suspend fun updateSavingsGoal(goal: SavingsGoal) {}
        override fun getActiveSubscriptions(): Flow<List<Subscription>> = MutableStateFlow(emptyList())
        override suspend fun saveSubscription(subscription: Subscription) {}
        override suspend fun deleteSubscription(id: String, active: Boolean) {}
    }

    private fun message(
        id: String,
        body: String,
        receivedAt: Long = 1000L
    ) = SmsMessageEntity(
        id = id,
        address = "HDFCBank",
        body = body,
        receivedAt = receivedAt
    )

    private fun body(amount: Double, type: TransactionType, merchant: String? = null): String =
        buildString {
            append("INR $amount ")
            append(if (type == TransactionType.EXPENSE) "debited" else "credited")
            merchant?.let { append(" at $it") }
            append(" on 15/08/2026")
        }

    private suspend fun status(dao: FakeSmsMessageDao, id: String): String =
        dao.getById(id)!!.status

    @Test
    fun `parseable messages are saved and marked SAVED`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        dao.insertAll(listOf(message("s1", body(250.0, TransactionType.EXPENSE, "swiggy"))))

        val result = SmsPipelineProcessor(dao, repo).processPending(maxMessages = 10)

        assertEquals(1, result.saved)
        assertEquals(SmsMessageStatus.SAVED.name, status(dao, "s1"))
        assertEquals("s1", repo.saved.single().sourceSmsId)
    }

    @Test
    fun `otp and alert messages are skipped without saving`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        dao.insertAll(
            listOf(
                message("otp", "Your OTP is 482913"),
                message("balance", "Your available balance is Rs. 5000")
            )
        )

        val result = SmsPipelineProcessor(dao, repo).processPending(maxMessages = 10)

        assertEquals(0, result.saved)
        assertEquals(2, result.skipped)
        assertTrue(repo.saved.isEmpty())
        assertEquals(SmsMessageStatus.SKIPPED.name, status(dao, "otp"))
        assertEquals(SmsMessageStatus.SKIPPED.name, status(dao, "balance"))
    }

    @Test
    fun `unparsable messages are skipped and kept in the queue`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        dao.insertAll(listOf(message("nope", "Happy birthday! See you soon.")))

        val result = SmsPipelineProcessor(dao, repo).processPending(maxMessages = 10)

        assertEquals(0, result.saved)
        assertEquals(1, result.skipped)
        assertTrue(repo.saved.isEmpty())
        assertEquals(SmsMessageStatus.SKIPPED.name, status(dao, "nope"))
    }

    @Test
    fun `a constraint error follows the ordinary retry path`() = runBlocking {
        // The branch that used to swallow `UNIQUE constraint failed` as a terminal
        // SKIPPED is gone. A duplicate `sourceSmsId` is resolved inside the DAO by
        // `OnConflictStrategy.IGNORE`, so a constraint error that actually reaches
        // the pipeline is a real failure. The old branch existed to paper over
        // `REPLACE` — and it hid every other constraint error behind a "done".
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository().apply {
            failWith = Exception("android.database.sqlite.SQLiteConstraintException: UNIQUE constraint failed: transactions.sourceSmsId")
        }
        dao.insertAll(listOf(message("s1", body(250.0, TransactionType.EXPENSE))))

        val result = SmsPipelineProcessor(dao, repo, maxAttempts = 3).processPending(maxMessages = 10)

        assertEquals(0, result.saved)
        assertEquals(0, result.skipped)
        assertEquals(1, result.failed)
        assertEquals(SmsMessageStatus.FAILED.name, status(dao, "s1"))
    }

    @Test
    fun `transient failures retry up to maxAttempts then FAILED`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository().apply {
            failWith = Exception("disk io error")
        }
        dao.insertAll(listOf(message("s1", body(250.0, TransactionType.EXPENSE))))

        val first = SmsPipelineProcessor(dao, repo, maxAttempts = 3).processPending(maxMessages = 10)
        assertEquals(1, first.failed)
        assertEquals(SmsMessageStatus.FAILED.name, status(dao, "s1"))
        assertEquals(3, dao.getById("s1")!!.attempts)
    }

    @Test
    fun `only NEW messages are processed`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        dao.insertAll(
            listOf(
                message("saved", body(10.0, TransactionType.EXPENSE)).copy(
                    status = SmsMessageStatus.SAVED.name
                ),
                message("new", body(20.0, TransactionType.EXPENSE))
            )
        )

        val result = SmsPipelineProcessor(dao, repo).processPending(maxMessages = 10)

        assertEquals(1, result.saved)
        assertEquals("new", repo.saved.single().sourceSmsId)
        assertEquals(SmsMessageStatus.SAVED.name, status(dao, "new"))
        // A second run has nothing left to do.
        val second = SmsPipelineProcessor(dao, repo).processPending(maxMessages = 10)
        assertEquals(0, second.attempted)
    }

    @Test
    fun `a row is marked CLASSIFYING while it is being classified`() = runBlocking {
        // Live status is the queue itself: the user watches each message move
        // NEW -> CLASSIFYING -> SAVED/SKIPPED/FAILED, which is only possible if the in-flight
        // state is written before the expensive work rather than invented afterwards.
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        dao.insertAll(listOf(message("s1", body(250.0, TransactionType.EXPENSE, "swiggy"))))

        var statusMidFlight: String? = null
        val processor = SmsPipelineProcessor(
            dao = dao,
            transactionRepository = repo,
            enrich = { classified, _ ->
                statusMidFlight = dao.getById("s1")?.status
                classified
            }
        )

        processor.processPending(maxMessages = 10)

        assertEquals(SmsMessageStatus.CLASSIFYING.name, statusMidFlight)
        assertEquals(SmsMessageStatus.SAVED.name, status(dao, "s1"))
    }

    @Test
    fun `a row a killed pass left CLASSIFYING is swept back to NEW and retried`() = runBlocking {
        // Without the sweep, `getPending(NEW, 1)` never returns that row again and the message
        // is stranded forever: invisible to the classifier, invisible to the user.
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        dao.insertAll(
            listOf(
                message("stale", body(250.0, TransactionType.EXPENSE)).copy(
                    status = SmsMessageStatus.CLASSIFYING.name,
                    updatedAt = 1L
                )
            )
        )

        val result = SmsPipelineProcessor(dao, repo).processPending(maxMessages = 10)

        assertEquals(1, result.saved)
        assertEquals(SmsMessageStatus.SAVED.name, status(dao, "stale"))
        // swept (updatedAt = now), then re-claimed for the retry (updatedAt = now again).
        assertTrue(
            "the sweep must have refreshed the row",
            dao.getById("stale")!!.updatedAt > 1L
        )
    }

    @Test
    fun `an undo-parked PROCESSED row is neither swept nor re-saved`() = runBlocking {
        // The dangerous one. Both undo paths park a row in PROCESSED: the user just dismissed
        // it, or just un-saved the transaction it created. Sweeping or re-processing it would
        // silently resurrect the decision they reversed, so the sweep must leave it alone.
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        dao.insertAll(
            listOf(
                message("parked", body(250.0, TransactionType.EXPENSE)).copy(
                    status = SmsMessageStatus.PROCESSED.name,
                    updatedAt = 7L
                )
            )
        )

        val result = SmsPipelineProcessor(dao, repo).processPending(maxMessages = 10)

        assertEquals(0, result.attempted)
        assertTrue("the undo must not be undone", repo.saved.isEmpty())
        assertEquals(SmsMessageStatus.PROCESSED.name, status(dao, "parked"))
        assertEquals("the sweep must not touch a parked row", 7L, dao.getById("parked")!!.updatedAt)
    }

    @Test
    fun `a new pass starts by sweeping whatever the previous pass left in flight`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        dao.insertAll(
            listOf(
                message("a", body(10.0, TransactionType.EXPENSE)).copy(
                    status = SmsMessageStatus.CLASSIFYING.name
                ),
                message("b", body(20.0, TransactionType.EXPENSE))
            )
        )

        val result = SmsPipelineProcessor(dao, repo).processPending(maxMessages = 10)

        assertEquals(2, result.saved)
        assertEquals(SmsMessageStatus.SAVED.name, status(dao, "a"))
        assertEquals(SmsMessageStatus.SAVED.name, status(dao, "b"))
    }
}
