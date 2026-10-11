package com.awbuilds.auraspend.ui.classification

import com.awbuilds.auraspend.TestTransactionRepositoryDefaults
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.domain.usecase.ClassifyMessageUseCase
import com.awbuilds.auraspend.domain.usecase.SaveTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owns `Dispatchers.Main` for the duration of a test.
 *
 * Without this there is no way to test anything that launches into
 * `viewModelScope`, which is why the suite had no ViewModel coverage at all —
 * not because the logic is untestable, but because the main dispatcher was not
 * available under a JVM test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher()
) : org.junit.rules.TestWatcher() {

    override fun starting(description: org.junit.runner.Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: org.junit.runner.Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Intent-level tests for the classification ViewModel.
 *
 * The screen had 500+ lines of state logic that nothing exercised: the review's
 * finding 25 records that no test referenced a ViewModel. These pin the two
 * properties that decide whether the user's money is entered correctly — a save
 * either reports success or reports a typed failure, and it never does both —
 * plus the state transitions the screen reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class ClassificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class RecordingRepository : TestTransactionRepositoryDefaults {
        val saved = mutableListOf<Transaction>()
        var failWith: Exception? = null

        override fun getAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(saved)
        override suspend fun getTransactionById(id: String): Transaction? = saved.firstOrNull { it.id == id }
        override fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> =
            MutableStateFlow(saved)
        override fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>> =
            MutableStateFlow(saved)
        override fun getRecurringTransactions(): Flow<List<Transaction>> = MutableStateFlow(saved)
        override suspend fun saveTransaction(transaction: Transaction) {
            failWith?.let { throw it }
            saved.add(transaction)
        }

        override suspend fun saveTransactions(transactions: List<Transaction>) {
            transactions.forEach { saveTransaction(it) }
        }

        override suspend fun deleteTransaction(id: String) = Unit
        override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(categories)
        override suspend fun getCategoryById(id: String): Category? = null
        override suspend fun saveCategory(category: Category) = Unit
        override suspend fun saveCategories(categories: List<Category>) = Unit
        override suspend fun deleteCategory(id: String) = Unit
        override fun getAllBudgets() = MutableStateFlow(emptyList<com.awbuilds.auraspend.domain.model.Budget>())
        override suspend fun getBudgetByCategory(categoryId: String) = null
        override suspend fun saveBudget(budget: com.awbuilds.auraspend.domain.model.Budget) = Unit
        override suspend fun updateBudgetSpent(categoryId: String, spent: Double) = Unit
        override suspend fun deleteBudget(budgetId: String) = Unit
        override fun getAllSavingsGoals() =
            MutableStateFlow(emptyList<com.awbuilds.auraspend.domain.model.SavingsGoal>())
        override suspend fun saveSavingsGoal(goal: com.awbuilds.auraspend.domain.model.SavingsGoal) = Unit
        override suspend fun deleteSavingsGoal(goalId: String) = Unit
        override suspend fun updateSavingsGoal(goal: com.awbuilds.auraspend.domain.model.SavingsGoal) = Unit
        override fun getActiveSubscriptions() =
            MutableStateFlow(emptyList<com.awbuilds.auraspend.domain.model.Subscription>())
        override suspend fun saveSubscription(subscription: com.awbuilds.auraspend.domain.model.Subscription) = Unit
        override suspend fun deleteSubscription(id: String, active: Boolean) = Unit
        override fun pagedTransactions(filter: com.awbuilds.auraspend.domain.model.TransactionFilter) =
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty<Transaction>())

        companion object {
            private val categories = listOf(
                Category(id = "cat_food", name = "Food", icon = "restaurant", color = 0, isDefault = true),
                Category(id = "cat_other", name = "Other", icon = "category", color = 0, isDefault = true)
            )
        }
    }

    private lateinit var repository: RecordingRepository

    @Before
    fun setUp() {
        repository = RecordingRepository()
    }

    @After
    fun tearDown() = Unit

    private fun viewModel() = ClassificationViewModel(
        classifyMessageUseCase = ClassifyMessageUseCase(),
        saveTransactionUseCase = SaveTransactionUseCase(repository),
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
    )

    @Test
    fun `a typed message lands in state and clears any previous sender`() {
        val vm = viewModel()

        vm.handleIntent(ClassificationViewIntent.MessageChanged("INR 250 debited at SWIGGY"))

        assertEquals("INR 250 debited at SWIGGY", vm.state.value.rawMessage)
        assertNull(vm.state.value.rawMessageSender)
    }

    @Test
    fun `an error can be cleared by the screen`() {
        val vm = viewModel()

        vm.handleIntent(ClassificationViewIntent.ClearError)

        assertNull(vm.state.value.error)
    }

    @Test
    fun `a manual save reports success once the transaction is stored`() = runTest {
        val vm = viewModel()
        vm.handleIntent(ClassificationViewIntent.ToggleManualEntry)
        vm.handleIntent(ClassificationViewIntent.AmountChanged("499.50"))
        vm.handleIntent(ClassificationViewIntent.NoteChanged("Lunch"))

        vm.handleIntent(ClassificationViewIntent.SaveTransaction)

        assertTrue("the save must be reported as done", vm.state.value.saveSuccess)
        assertFalse(vm.state.value.isSaving)
        assertNull("and not as a failure", vm.state.value.error)
        assertEquals(1, repository.saved.size)
        assertEquals(499.50, repository.saved.first().amount, 0.001)
        assertEquals(TransactionType.EXPENSE, repository.saved.first().type)
    }

    @Test
    fun `a save with no amount is a typed failure, never a success or a crash`() = runTest {
        // The manual-entry path with a blank amount *and* nothing parsed. The
        // ViewModel throws internally and catches it; what must reach the screen is
        // `SAVE_FAILED` and definitely not `saveSuccess`, or the editor would close
        // as if the transaction existed.
        val vm = viewModel()
        vm.handleIntent(ClassificationViewIntent.ToggleManualEntry)

        vm.handleIntent(ClassificationViewIntent.SaveTransaction)

        assertEquals(com.awbuilds.auraspend.ui.core.UiError.SAVE_FAILED, vm.state.value.error)
        assertFalse("nothing was saved, so nothing may claim success", vm.state.value.saveSuccess)
        assertEquals(0, repository.saved.size)
    }

    @Test
    fun `a failing write is reported as a typed failure and the editor stays open`() = runTest {
        repository.failWith = IllegalStateException("disk full")
        val vm = viewModel()
        vm.handleIntent(ClassificationViewIntent.ToggleManualEntry)
        vm.handleIntent(ClassificationViewIntent.AmountChanged("120"))

        vm.handleIntent(ClassificationViewIntent.SaveTransaction)

        assertEquals(com.awbuilds.auraspend.ui.core.UiError.SAVE_FAILED, vm.state.value.error)
        assertFalse(vm.state.value.saveSuccess)
        assertFalse("the screen must not be left in a saving state", vm.state.value.isSaving)
    }

    @Test
    fun `a pasted message is classified from the body alone`() {
        val vm = viewModel()

        vm.handleIntent(ClassificationViewIntent.MessageChanged("INR 250 debited at SWIGGY"))
        vm.handleIntent(ClassificationViewIntent.ClassifyMessage)

        val parsed = vm.state.value.parsedMessage
        assertEquals(250.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("cat_food", vm.state.value.selectedCategoryId)
    }

    @Test
    fun `the category selector drives the saved category`() = runTest {
        val vm = viewModel()
        vm.handleIntent(ClassificationViewIntent.ToggleManualEntry)
        vm.handleIntent(ClassificationViewIntent.AmountChanged("120"))
        vm.handleIntent(ClassificationViewIntent.SelectCategory("cat_other"))

        vm.handleIntent(ClassificationViewIntent.SaveTransaction)

        assertEquals("cat_other", repository.saved.first().categoryId)
    }
}
