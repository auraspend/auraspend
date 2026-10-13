package com.awbuilds.auraspend.ui.classification

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.RobolectricWorkManagerApp
import com.awbuilds.auraspend.TestTransactionRepositoryDefaults
import com.awbuilds.auraspend.data.classification.AutoDetect
import com.awbuilds.auraspend.data.local.dao.SmsMessageDao
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.usecase.ClassifyMessageUseCase
import com.awbuilds.auraspend.domain.usecase.SaveTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * Owns `Dispatchers.Main` with a REAL single thread for the duration of a test.
 *
 * The queue tests wait on a live Room `Flow`, whose emissions arrive on Room's executor and are
 * handed back to the main dispatcher. With a *test* dispatcher that transfer only happens when
 * somebody advances the scheduler, so these tests would assert against a state that has not
 * been emitted yet; with a real thread the assertion is simply preceded by a wait for the state
 * it is about to read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealMainDispatcherRule : TestWatcher() {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "classification-test-main")
    }

    // Typed as ExecutorCoroutineDispatcher so `close()` is reachable: the dispatcher wrapping
    // an executor is Closeable, the base CoroutineDispatcher is not.
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        dispatcher.close()
        executor.shutdownNow()
    }

    companion object {
        /** How long a test waits for the queue observer to catch up with the database. */
        const val TIMEOUT_MS = 10_000L
    }
}

/**
 * The queue→screen wiring: live progress, the in-flight chip, and which messages are offered.
 *
 * Runs against a real Room database via the real application, because the point of these tests
 * is that the screen's numbers come from the same table the background pipeline writes.
 * `AutoDetect` is off, so neither the coordinator nor the worker can mutate the queue
 * mid-assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricWorkManagerApp::class)
class ClassificationQueueStateTest {

    @get:Rule
    val mainDispatcherRule = RealMainDispatcherRule()

    private class RecordingRepository : TestTransactionRepositoryDefaults {
        val saved = mutableListOf<Transaction>()
        override fun getAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(saved)
        override suspend fun getTransactionById(id: String): Transaction? =
            saved.firstOrNull { it.id == id }
        override fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> =
            MutableStateFlow(saved)
        override fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>> =
            MutableStateFlow(saved)
        override fun getRecurringTransactions(): Flow<List<Transaction>> = MutableStateFlow(saved)
        override suspend fun saveTransaction(transaction: Transaction) {
            saved.add(transaction)
        }
        override suspend fun saveTransactions(transactions: List<Transaction>) {
            saved.addAll(transactions)
        }
        override suspend fun deleteTransaction(id: String) = Unit
        override fun getAllCategories(): Flow<List<Category>> =
            MutableStateFlow(
                listOf(
                    Category(id = "cat_food", name = "Food & Dining", icon = "restaurant", color = 0, isDefault = true),
                    Category(id = "cat_other", name = "Other", icon = "category", color = 0, isDefault = true)
                )
            )
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
    }

    private lateinit var repository: RecordingRepository

    @Before
    fun setUp() {
        repository = RecordingRepository()
        val app = ApplicationProvider.getApplicationContext<RobolectricWorkManagerApp>()
        // Keep the background pipeline out of the test: the coordinator and the worker are
        // both driven by this flag, and neither must mutate the queue mid-assertion.
        AutoDetect.setEnabled(app, false)
        shadowOf(app).grantPermissions(Manifest.permission.READ_SMS)
        // The application (and therefore its database) is a process-wide singleton, so rows
        // seeded by an earlier test are still there. Every test seeds its own queue.
        kotlinx.coroutines.runBlocking { dao().clear() }
    }

    @After
    fun tearDown() {
        AutoDetect.setEnabled(ApplicationProvider.getApplicationContext(), false)
    }

    private fun viewModel(): ClassificationViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ClassificationViewModel(
            classifyMessageUseCase = ClassifyMessageUseCase(),
            saveTransactionUseCase = SaveTransactionUseCase(repository),
            context = context
        )
    }

    private fun dao(): SmsMessageDao =
        ApplicationProvider.getApplicationContext<RobolectricWorkManagerApp>().database.smsMessageDao()

    private fun row(
        id: String,
        status: SmsMessageStatus,
        body: String = "INR 250.00 debited at swiggy on 15/08/2026"
    ) = SmsMessageEntity(
        id = id,
        address = "HDFCBANK",
        body = body,
        receivedAt = 1_700_000_000_000L + id.hashCode(),
        status = status.name
    )

    private fun seed(vararg rows: SmsMessageEntity) {
        kotlinx.coroutines.runBlocking {
            rows.forEach { dao().insertAll(listOf(it)) }
        }
    }

    /** Waits until the screen state has caught up with the database. */
    private fun awaitState(
        viewModel: ClassificationViewModel,
        predicate: (ClassificationViewState) -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + RealMainDispatcherRule.TIMEOUT_MS
        while (!predicate(viewModel.state.value)) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("state never settled: ${viewModel.state.value}")
            }
            Thread.sleep(10)
        }
    }

    @Test
    fun `progress state mirrors the queue as its rows advance`() {
        seed(
            row("new", SmsMessageStatus.NEW),
            row("classifying", SmsMessageStatus.CLASSIFYING),
            row("saved", SmsMessageStatus.SAVED),
            row("skipped", SmsMessageStatus.SKIPPED)
        )

        val vm = viewModel()
        vm.handleIntent(ClassificationViewIntent.LoadSmsMessages)

        // The header reads from the queue, not from a worker's report, so it is live.
        awaitState(vm) { it.progressTotal > 0 }

        val state = vm.state.value
        assertEquals("the denominator is every row", 4, state.progressTotal)
        assertEquals("terminal rows are the processed ones", 2, state.progressCurrent)
        assertTrue("a row is in flight", state.isCategorising)
        assertEquals("the rest wait", 1, state.queuedCount)
        assertEquals(1, state.savedCount)
        assertEquals(1, state.skippedCount)
        assertFalse("the scan itself finished", state.isScanningQueue)
    }

    @Test
    fun `the inbox holds rows awaiting a decision and the candidate list drops skipped ones`() {
        seed(
            row("new", SmsMessageStatus.NEW),
            row("classifying", SmsMessageStatus.CLASSIFYING),
            row("saved", SmsMessageStatus.SAVED),
            row("skipped", SmsMessageStatus.SKIPPED)
        )

        val vm = viewModel()
        vm.handleIntent(ClassificationViewIntent.LoadSmsMessages)
        awaitState(vm) { it.progressTotal > 0 }

        // A row being classified stays visible: the user asked to watch each message.
        // A row the pipeline skipped (OTP, alert, duplicate) is not a candidate.
        assertEquals(
            listOf("classifying", "new"),
            vm.state.value.pendingSmsList.map { it.sms.id }.sorted()
        )
        assertEquals(
            listOf("classifying", "new", "saved"),
            vm.state.value.smsMessages.map { it.id }.sorted()
        )
    }

    @Test
    fun `save all persists rows awaiting a decision and leaves handled rows alone`() {
        seed(
            row("parseable", SmsMessageStatus.NEW),
            row("okWhileClassifying", SmsMessageStatus.CLASSIFYING),
            row("alreadySaved", SmsMessageStatus.SAVED),
            row("dismissed", SmsMessageStatus.SKIPPED),
            row("noAmount", SmsMessageStatus.NEW, body = "Happy birthday, see you soon!")
        )

        val vm = viewModel()
        vm.handleIntent(ClassificationViewIntent.LoadSmsMessages)
        awaitState(vm) { it.progressTotal > 0 }
        vm.handleIntent(ClassificationViewIntent.SaveAllClassified)
        awaitState(vm) { it.savedCount >= 3 }

        val savedIds = repository.saved.map { it.sourceSmsId ?: "" }.sorted()
        assertEquals(
            // Only the two awaiting rows with a resolved parse. The already-saved and
            // dismissed rows belong to the undo paths, and a message with no amount is not
            // a transaction.
            listOf("okWhileClassifying", "parseable"),
            savedIds
        )

        kotlinx.coroutines.runBlocking {
            val dao = dao()
            assertEquals(SmsMessageStatus.SAVED.name, dao.getById("parseable")!!.status)
            assertEquals(SmsMessageStatus.SAVED.name, dao.getById("okWhileClassifying")!!.status)
            assertEquals(
                "a previously saved row was not touched",
                SmsMessageStatus.SAVED.name,
                dao.getById("alreadySaved")!!.status
            )
            assertEquals(
                "a dismissed row was not resurrected",
                SmsMessageStatus.SKIPPED.name,
                dao.getById("dismissed")!!.status
            )
        }
    }
}
