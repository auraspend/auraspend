package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.local.dao.SmsMessageDao
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.SavingsGoal
import com.awbuilds.auraspend.domain.model.Subscription
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The production audit found the same real payment saved multiple times from
 * DIFFERENT SMS (unique sourceSmsId could not catch those). These tests lock in
 * the semantic-duplicate guard: re-delivered content is skipped, distinct
 * payments still save.
 */
class SemanticDuplicateGuardTest {

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
        override suspend fun update(message: SmsMessageEntity) { rows[message.id] = message }
        override suspend fun getMaxReceivedAt(): Long = rows.values.maxOfOrNull { it.receivedAt } ?: 0L

        override suspend fun countByStatus(status: String): Int =
            rows.values.count { it.status == status }

        override suspend fun countAll(): Int = rows.size

        // Crash recovery for the in-flight marker; unused by these tests.
        override suspend fun sweepInFlight(now: Long) {}

        // Added for the atomic Drive restore path; unused by these tests.
        override suspend fun clear() = rows.clear()

        // Added for the Drive backup path; unused by these tests.
        override suspend fun getAllOnce(): List<SmsMessageEntity> = rows.values.toList()
    }

    private class FakeTransactionRepository : com.awbuilds.auraspend.TestTransactionRepositoryDefaults {
        val saved = mutableListOf<Transaction>()
        val existing = mutableListOf<Transaction>()
        private val categories = listOf(
            Category(id = "cat_food", name = "Food & Dining", icon = "restaurant", color = 0, isDefault = true),
            Category(id = "cat_other", name = "Other", icon = "category", color = 0, isDefault = true)
        )
        override fun getAllTransactions(): Flow<List<Transaction>> =
            MutableStateFlow(existing + saved)
        override suspend fun getTransactionById(id: String): Transaction? =
            (existing + saved).firstOrNull { it.id == id }
        override fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> =
            MutableStateFlow((existing + saved).filter {
                val ts = it.date.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                ts in start..end
            })
        override fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>> = getAllTransactions()
        override fun getRecurringTransactions(): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override suspend fun saveTransaction(transaction: Transaction) { saved.add(transaction) }
        override suspend fun saveTransactions(transactions: List<Transaction>) { saved.addAll(transactions) }
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

    private fun message(id: String, body: String, status: SmsMessageStatus = SmsMessageStatus.NEW) =
        SmsMessageEntity(
            id = id,
            address = "HDFCBank",
            body = body,
            receivedAt = System.currentTimeMillis(),
            status = status.name
        )

    @Test
    fun `re-delivered identical body is skipped not double-saved`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        val originalBody = "Rs 999.95 SIP Purchase in Folio 30917265 Commodities Fund debited today"
        dao.insertAll(listOf(message("first", originalBody)))
        val first = SmsPipelineProcessor(dao, repo)
        assertEquals(1, first.processPending(10).saved)

        // Provider retries the same SMS minutes later under a NEW message id.
        dao.insertAll(listOf(message("retry", "  $originalBody  ")))
        val second = SmsPipelineProcessor(dao, repo)
        val result = second.processPending(10)

        assertEquals(0, result.saved)
        assertEquals(1, result.skipped)
        assertEquals(SmsMessageStatus.SKIPPED.name, dao.getById("retry")!!.status)
    }

    @Test
    fun `near-identical transaction within an hour is skipped`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        repo.existing.add(
            Transaction(
                amount = 10000.0,
                categoryId = "cat_other",
                note = "report cyber fraud- Canara Bank",
                merchant = null,
                date = LocalDateTime.now().minusMinutes(10),
                type = com.awbuilds.auraspend.domain.model.TransactionType.EXPENSE
            )
        )
        dao.insertAll(listOf(message("dup2", "INR 10000 debited report cyber fraud- Canara Bank immediately")))

        val result = SmsPipelineProcessor(dao, repo).processPending(10)

        assertEquals(0, result.saved)
        assertEquals(1, result.skipped)
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `a genuinely different payment still saves`() = runBlocking {
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        repo.existing.add(
            Transaction(
                amount = 350.0,
                categoryId = "cat_food",
                note = "",
                merchant = "Starbucks",
                date = LocalDateTime.now().minusHours(8),
                type = com.awbuilds.auraspend.domain.model.TransactionType.EXPENSE
            )
        )
        dao.insertAll(listOf(message("coffee2", "Spent INR 350 Axis Bank Card no. XX4821 IST Starbucks")))

        val result = SmsPipelineProcessor(dao, repo).processPending(10)

        assertEquals("same-day repeat purchase must NOT be dropped", 1, result.saved)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `twin credits from bank and wallet are fingerprint-skipped`() = runBlocking {
        // Production case: the SAME ₹2,236.67 credit announced by two Canara senders
        // with completely different wording, minutes apart.
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        repo.existing.add(
            Transaction(
                amount = 2236.67,
                categoryId = "cat_other",
                note = "Your a/c no. XX0007 has been credited",
                // Bank SMS arrived 10:15; the twin parses to noon (no time in its body).
                date = LocalDateTime.of(2026, 5, 7, 10, 15),
                type = com.awbuilds.auraspend.domain.model.TransactionType.INCOME
            )
        )
        dao.insertAll(
            listOf(
                message(
                    "twin",
                    "An amount of INR 2,236.67 has been CREDITED to your account XXXX6021 on 07/05/2026.Total Avail.bal INR 24,499.67.- Canara Bank"
                )
            )
        )

        val result = SmsPipelineProcessor(dao, repo).processPending(10)

        assertEquals("same amount+type within minutes must not double-save", 0, result.saved)
        assertEquals(SmsMessageStatus.SKIPPED.name, dao.getById("twin")!!.status)
    }

    @Test
    fun `merchantless credits minutes apart are not duplicates when the amounts differ`() = runBlocking {
        // Measured false positive (owner's device, 2026-10-13): ₹840.76 vs an
        // unrelated ₹892 credit 37 minutes earlier, both merchantless, scored
        // ~0.98 under the old detector call and the payment was skipped. A 6%
        // amount gap is a different payment.
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        repo.existing.add(
            Transaction(
                amount = 892.0,
                categoryId = "cat_other",
                note = "We have received payment of Rs.892.00 via BBPS",
                merchant = null,
                date = LocalDateTime.now().minusMinutes(37),
                type = com.awbuilds.auraspend.domain.model.TransactionType.INCOME
            )
        )
        // No date in the body: the transaction falls back to now(), so the duplicate
        // window covers the ₹892 credit — the amount gate must still save it.
        dao.insertAll(
            listOf(
                message(
                    "cardpayment",
                    "Payment of Rs 840.76 has been received on your ICICI Bank Credit Card XX8000 through Bharat Bill Payment System"
                )
            )
        )

        val result = SmsPipelineProcessor(dao, repo).processPending(10)

        assertEquals("a 6% amount gap is a different payment, not a duplicate", 1, result.saved)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `income and expense of same amount same minute are BOTH real`() = runBlocking {
        // Production case: ₹7,000 salary credit at 19:32 vs ₹7,000 debit at 19:29.
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        repo.existing.add(
            Transaction(
                amount = 7000.0,
                categoryId = "cat_salary",
                note = "Payment received",
                date = LocalDateTime.now().minusMinutes(3),
                type = com.awbuilds.auraspend.domain.model.TransactionType.INCOME
            )
        )
        dao.insertAll(listOf(message("debit7k", "An amount of INR 7,000.00 has been DEBITED to your account")))

        val result = SmsPipelineProcessor(dao, repo).processPending(10)

        assertEquals("type mismatch means never a duplicate", 1, result.saved)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `historical duplicate is caught even when processed weeks later`() = runBlocking {
        // Production flaw: window was anchored to 'now', so May messages processed in
        // August could never see each other. The window must centre on the txn date.
        val mayDate = LocalDateTime.of(2026, 5, 2, 6, 17)
        val dao = FakeSmsMessageDao()
        val repo = FakeTransactionRepository()
        repo.existing.add(
            Transaction(
                amount = 3168.0,
                categoryId = "cat_transfer",
                note = "BAJAJ FINANCE L",
                merchant = "Bajaj Finance L",
                date = mayDate,
                type = com.awbuilds.auraspend.domain.model.TransactionType.EXPENSE
            )
        )
        dao.insertAll(
            listOf(message("emi2", "An amount of INR 3,168.00 has been DEBITED to your account XXXX6021 on 02/05/2026 06:15 towards BAJAJ FINANCE"))
        )

        val result = SmsPipelineProcessor(dao, repo).processPending(10)

        assertEquals("date-centred window must catch historical twins", 0, result.saved)
        assertEquals(1, result.skipped)
    }
}
