package com.awbuilds.auraspend.ui.transaction

import com.awbuilds.auraspend.TestTransactionRepositoryDefaults
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.classification.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class TransactionDetailViewModelTest {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private fun createViewModel(
        id: String,
        repo: TestRepo,
        lookup: com.awbuilds.auraspend.data.local.SmsMessageLookup? = null
    ) = TransactionDetailViewModel(
        transactionId = id,
        repository = repo,
        messageLookup = lookup,
        ioDispatcher = testDispatcher
    )

    private class TestRepo : TestTransactionRepositoryDefaults {
        val transactions = mutableMapOf<String, Transaction>()
        val categories = mutableListOf(
            Category(id = "cat_food", name = "Food", icon = "restaurant", color = 0, isDefault = true),
            Category(id = "cat_shop", name = "Shopping", icon = "shopping", color = 0, isDefault = true)
        )
        var throwOnGet = false
        var throwOnSave = false
        var throwOnDelete = false

        override fun getAllTransactions(): Flow<List<Transaction>> =
            MutableStateFlow(transactions.values.toList())

        override suspend fun getTransactionById(id: String): Transaction? {
            if (throwOnGet) throw RuntimeException("Simulated SQLite error")
            return transactions[id]
        }

        override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(categories)

        override suspend fun saveTransaction(transaction: Transaction) {
            if (throwOnSave) throw RuntimeException("Simulated DB save error")
            transactions[transaction.id] = transaction
        }

        override suspend fun deleteTransaction(id: String) {
            if (throwOnDelete) throw RuntimeException("Simulated DB delete error")
            transactions.remove(id)
        }

        override fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> =
            MutableStateFlow(transactions.values.toList())
        override fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>> =
            MutableStateFlow(transactions.values.filter { it.categoryId == categoryId })
        override fun getRecurringTransactions(): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override suspend fun saveTransactions(transactions: List<Transaction>) {
            transactions.forEach { saveTransaction(it) }
        }
        override suspend fun getCategoryById(id: String): Category? = categories.find { it.id == id }
        override suspend fun saveCategory(category: Category) { categories.add(category) }
        override suspend fun saveCategories(categories: List<Category>) { this.categories.addAll(categories) }
        override suspend fun deleteCategory(id: String) { categories.removeAll { it.id == id } }
        override fun getAllBudgets(): Flow<List<com.awbuilds.auraspend.domain.model.Budget>> = MutableStateFlow(emptyList())
        override suspend fun getBudgetByCategory(categoryId: String): com.awbuilds.auraspend.domain.model.Budget? = null
        override suspend fun saveBudget(budget: com.awbuilds.auraspend.domain.model.Budget) {}
        override suspend fun updateBudgetSpent(categoryId: String, spent: Double) {}
        override suspend fun deleteBudget(budgetId: String) {}
        override fun getAllSavingsGoals(): Flow<List<com.awbuilds.auraspend.domain.model.SavingsGoal>> = MutableStateFlow(emptyList())
        override suspend fun saveSavingsGoal(goal: com.awbuilds.auraspend.domain.model.SavingsGoal) {}
        override suspend fun deleteSavingsGoal(goalId: String) {}
        override suspend fun updateSavingsGoal(goal: com.awbuilds.auraspend.domain.model.SavingsGoal) {}
        override fun getActiveSubscriptions(): Flow<List<com.awbuilds.auraspend.domain.model.Subscription>> = MutableStateFlow(emptyList())
        override suspend fun saveSubscription(subscription: com.awbuilds.auraspend.domain.model.Subscription) {}
        override suspend fun deleteSubscription(id: String, active: Boolean) {}
    }

    private val sampleTx = Transaction(
        id = "tx_123",
        amount = 450.0,
        type = TransactionType.EXPENSE,
        categoryId = "cat_food",
        date = LocalDateTime.of(2026, 11, 6, 12, 0),
        merchant = "Swiggy",
        note = "Lunch",
        bankName = "HDFC Bank"
    )

    @Test
    fun `load transaction transitions to Success when record exists`() = runTest {
        val repo = TestRepo().apply { transactions[sampleTx.id] = sampleTx }
        val vm = createViewModel(sampleTx.id, repo)

        val state = vm.uiState.value
        assertTrue(state is TransactionDetailUiState.Success)
        val success = state as TransactionDetailUiState.Success
        assertEquals("tx_123", success.transaction.id)
        assertEquals("Swiggy", success.transaction.merchant)
        assertEquals(2, success.categories.size)
    }

    @Test
    fun `load transaction transitions to NotFound when record does not exist`() = runTest {
        val repo = TestRepo()
        val vm = createViewModel("unknown_id", repo)

        val state = vm.uiState.value
        assertTrue(state is TransactionDetailUiState.NotFound)
    }

    @Test
    fun `load transaction transitions to Error when repository throws`() = runTest {
        val repo = TestRepo().apply { throwOnGet = true }
        val vm = createViewModel("tx_error", repo)

        val state = vm.uiState.value
        assertTrue(state is TransactionDetailUiState.Error)
    }

    @Test
    fun `save transaction updates repository and state`() = runTest {
        val repo = TestRepo().apply { transactions[sampleTx.id] = sampleTx }
        val vm = createViewModel(sampleTx.id, repo)

        val updated = sampleTx.copy(merchant = "Zomato", amount = 520.0)
        var callbackSuccess = false
        vm.saveTransaction(updated) { success -> callbackSuccess = success }

        assertTrue(callbackSuccess)
        val state = vm.uiState.value as TransactionDetailUiState.Success
        assertEquals("Zomato", state.transaction.merchant)
        assertEquals(520.0, state.transaction.amount, 0.001)
        assertEquals("Zomato", repo.transactions[sampleTx.id]?.merchant)
    }

    @Test
    fun `save transaction reports failure when repository throws`() = runTest {
        val repo = TestRepo().apply { transactions[sampleTx.id] = sampleTx }
        val vm = createViewModel(sampleTx.id, repo)

        repo.throwOnSave = true
        var callbackSuccess = true
        vm.saveTransaction(sampleTx.copy(amount = 999.0)) { success -> callbackSuccess = success }

        assertFalse(callbackSuccess)
    }

    @Test
    fun `delete transaction removes from repository and notifies callback`() = runTest {
        val repo = TestRepo().apply { transactions[sampleTx.id] = sampleTx }
        val vm = createViewModel(sampleTx.id, repo)

        var callbackSuccess = false
        vm.deleteTransaction { success -> callbackSuccess = success }

        assertTrue(callbackSuccess)
        assertFalse(repo.transactions.containsKey(sampleTx.id))
    }

    @Test
    fun `duplicate transaction generates fresh UUID and saves copy`() = runTest {
        val repo = TestRepo().apply { transactions[sampleTx.id] = sampleTx }
        val vm = createViewModel(sampleTx.id, repo)

        var newId: String? = null
        vm.duplicateTransaction { id -> newId = id }

        assertNotNull(newId)
        assertTrue(newId != sampleTx.id)
        val savedDuplicate = repo.transactions[newId]
        assertNotNull(savedDuplicate)
        assertEquals("Swiggy", savedDuplicate?.merchant)
        assertEquals(450.0, savedDuplicate?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `update category changes category and persists`() = runTest {
        val repo = TestRepo().apply { transactions[sampleTx.id] = sampleTx }
        val vm = createViewModel(sampleTx.id, repo)

        val newCat = repo.categories.first { it.id == "cat_shop" }
        var callbackSuccess = false
        vm.updateCategory(newCat) { success -> callbackSuccess = success }

        assertTrue(callbackSuccess)
        val state = vm.uiState.value as TransactionDetailUiState.Success
        assertEquals("cat_shop", state.transaction.categoryId)
        assertEquals("cat_shop", repo.transactions[sampleTx.id]?.categoryId)
    }
}
