package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.repository.TransactionRepositoryImpl
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionFilter
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Activity header must describe the rows under it.
 *
 * The header used the month's **unfiltered** total, so a list filtered to one
 * category or one direction sat under a number describing a different set of
 * rows, and an empty filtered list sat under a large one — which reads as a
 * bug rather than as a filter. This was visible on a real phone: "October ·
 * ₹74,855 out" above "No matching transactions".
 *
 * The aggregate is now narrowed by the same constraints the paged list uses, so
 * the two describe the same rows. This test pins that against a real in-memory
 * database rather than a fake, because the constraint list is the thing under
 * test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class FilteredSummaryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TransactionRepository

    private val month: YearMonth = YearMonth.of(2026, 10)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = TransactionRepositoryImpl(
            transactionDao = db.transactionDao(),
            categoryDao = db.categoryDao(),
            budgetDao = db.budgetDao(),
            subscriptionDao = db.subscriptionDao(),
            savingsGoalDao = db.savingsGoalDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun txn(
        id: String,
        amount: Double,
        categoryId: String,
        note: String,
        merchant: String?,
        type: TransactionType = TransactionType.EXPENSE,
        day: Int = 10
    ) = Transaction(
        id = id,
        amount = amount,
        categoryId = categoryId,
        note = note,
        merchant = merchant,
        date = LocalDateTime.of(2026, 10, day, 12, 0),
        type = type
    )

    @Before
    fun seed() = runBlocking {
        repository.saveTransactions(
            listOf(
                txn("food", 640.0, "cat_food", "Dinner", "Swiggy", day = 10),
                txn("food2", 360.0, "cat_food", "Lunch", "Cafe", day = 12),
                txn("taxi", 245.0, "cat_transport", "Ride", "Uber", day = 11),
                txn("salary", 50_000.0, "cat_salary", "Salary", "ACME", TransactionType.INCOME, day = 1)
            )
        )
    }

    private fun summary(filter: TransactionFilter) = runBlocking {
        repository.observeSummary(filter.copy(month = month)).first()
    }

    @Test
    fun `no filter totals the whole month`() {
        val s = summary(TransactionFilter(month = month))
        assertEquals(50_000.0, s.income, 0.001)
        assertEquals(1_245.0, s.expense, 0.001)
    }

    @Test
    fun `a category filter totals only that category`() {
        val s = summary(TransactionFilter(month = month, categoryId = "cat_food"))
        assertEquals(0.0, s.income, 0.001)
        assertEquals(1_000.0, s.expense, 0.001)
        assertEquals(2, s.expenseCount)
    }

    @Test
    fun `a direction filter totals only that direction`() {
        val incoming = summary(TransactionFilter(month = month, type = TransactionType.INCOME))
        assertEquals(50_000.0, incoming.income, 0.001)
        assertEquals(0.0, incoming.expense, 0.001)

        val outgoing = summary(TransactionFilter(month = month, type = TransactionType.EXPENSE))
        assertEquals(0.0, outgoing.income, 0.001)
        assertEquals(1_245.0, outgoing.expense, 0.001)
    }

    @Test
    fun `a search totals only the rows the search matches`() {
        val s = summary(TransactionFilter(month = month, query = "swiggy"))
        assertEquals(640.0, s.expense, 0.001)
        assertEquals(1, s.expenseCount)
    }

    @Test
    fun `a filter that matches nothing totals nothing`() {
        // The case the phone showed: an empty list must not sit under the
        // month's total.
        val s = summary(TransactionFilter(month = month, query = "nothing matches this"))
        assertEquals(0.0, s.income, 0.001)
        assertEquals(0.0, s.expense, 0.001)
        assertEquals(0, s.expenseCount)
    }
}
