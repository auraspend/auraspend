package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.repository.TransactionRepositoryImpl
import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.domain.model.BudgetSpending
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Budget spend is shown by three screens — Budget, Plan and the Dashboard — and
 * review #22 found three divergent implementations of it, two of which loaded the
 * whole transaction table. ADR 0003 makes the indexed `observeExpenseByCategory`
 * aggregate the production path and restricts `BudgetSpending.withFreshSpent` to
 * small tables and tests.
 *
 * This test pins the two together against a real in-memory Room database: for the
 * same rows and the same `BudgetSpending.periodEpochRange`, the aggregate the three
 * screens now consume must report exactly what the full-scan definition reports —
 * per category, for every budget period, including the range boundaries. It also
 * replays each screen's own mapping (copied from the source, with the file it comes
 * from) so the three are compared as the screens actually compute them.
 *
 * A wrong GROUP BY, a boundary that is exclusive where `spentFor` is inclusive, or a
 * period whose epoch range does not cover its calendar period fails here rather than
 * shipping as "Plan says ₹0 while the Dashboard says ₹4,200" — the 0015 bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class BudgetSpendAggregateTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TransactionRepository

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * A fixed "now": Sunday 23 August 2026, so the weekly period runs Mon 17 Aug –
     * Sun 23 Aug, the monthly one the whole of August, and the yearly one 2026.
     * `BudgetSpendingTest` documents the same week.
     */
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 23, 15, 0)

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

    // ─── Fixtures and helpers ─────────────────────────────────────────────────

    private fun txn(
        id: String,
        amount: Double,
        categoryId: String,
        date: LocalDateTime,
        type: TransactionType = TransactionType.EXPENSE
    ) = Transaction(
        id = id,
        amount = amount,
        categoryId = categoryId,
        note = "note $id",
        date = date,
        type = type
    )

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
        second: Int = 0
    ): LocalDateTime = LocalDateTime.of(year, month, day, hour, minute, second)

    /**
     * The production path: the indexed aggregate a screen consumes.
     *
     * This is the expression `BudgetViewModel.loadBudgets` and `PlanHubScreen` use
     * (`repository.observeExpenseByCategory(range).associate { it.categoryId to it.amount }`),
     * and the same query `DashboardViewModel.loadDashboard` combines.
     */
    private suspend fun aggregateByCategory(period: BudgetPeriod): Map<String, Double> {
        val (start, end) = BudgetSpending.periodEpochRange(period, now)
        return repository.observeExpenseByCategory(start, end).first()
            .associate { it.categoryId to it.amount }
    }

    /** The definition: what a full scan of the same rows says. */
    private suspend fun scanFor(period: BudgetPeriod, categoryId: String): Double =
        BudgetSpending.spentFor(
            Budget(categoryId = categoryId, limitAmount = 1_000.0, period = period),
            repository.getAllTransactions().first(),
            now
        )

    /** The period's own bounds, as epoch millis. */
    private fun epochRange(period: BudgetPeriod): Pair<Long, Long> =
        BudgetSpending.periodEpochRange(period, now)

    /**
     * BudgetViewModel's mapping, copied from `ui/budget/BudgetViewModel.kt`
     * (`BudgetsSnapshot.spentFor`) — the aggregate is keyed by category and a
     * missing key is zero.
     */
    private fun budgetScreenSpend(budget: Budget, totals: Map<String, Double>): Double =
        budget.copy(spentAmount = totals[budget.categoryId] ?: 0.0).spentAmount

    /**
     * PlanHubScreen's mapping, copied from `ui/plan/PlanHubScreen.kt` — the same
     * `totals[budget.categoryId] ?: 0.0`, per budget period.
     */
    private fun planScreenSpend(budget: Budget, totals: Map<String, Double>): Double =
        budget.copy(spentAmount = totals[budget.categoryId] ?: 0.0).spentAmount

    /**
     * DashboardViewModel's mapping, copied from `ui/home/DashboardViewModel.kt`
     * (`publish`), where the totals map is chosen from the budget's own period.
     */
    private fun dashboardSpend(budget: Budget, totals: Map<String, Double>): Double =
        budget.copy(spentAmount = totals[budget.categoryId] ?: 0.0).spentAmount

    // ─── The contract: aggregate == full scan ─────────────────────────────────

    @Test
    fun `the aggregate reports the same spend as a full scan for every budget period`() = runBlocking {
        // Food: in range for week, month and year. Transport: this month only.
        // Health: last month, so out of every period below. Income in a category
        // must never count, and a row outside a period must be ignored.
        repository.saveTransactions(
            listOf(
                txn("food-week", 250.0, "cat_food", at(2026, 8, 20, 10, 30)),
                txn("food-week-2", 125.50, "cat_food", at(2026, 8, 23, 23, 59, 59)),
                txn("food-earlier-month", 400.0, "cat_food", at(2026, 8, 2, 9, 0)),
                txn("food-income", 5_000.0, "cat_food", at(2026, 8, 12, 9, 0), TransactionType.INCOME),
                txn("food-july", 900.0, "cat_food", at(2026, 7, 28, 9, 0)),
                txn("transport-week", 80.0, "cat_transport", at(2026, 8, 21, 8, 0)),
                txn("transport-july", 300.0, "cat_transport", at(2026, 7, 15, 8, 0)),
                txn("health-july", 700.0, "cat_health", at(2026, 7, 10, 8, 0))
            )
        )

        val categories = listOf("cat_food", "cat_transport", "cat_health", "cat_never_used")

        BudgetPeriod.entries.forEach { period ->
            val totals = aggregateByCategory(period)
            categories.forEach { categoryId ->
                assertEquals(
                    "$period: aggregate and full scan disagree for $categoryId",
                    scanFor(period, categoryId),
                    totals[categoryId] ?: 0.0,
                    0.001
                )
            }
        }

        // Sanity: the numbers are not all zero, or the loop above would be vacuous.
        val weekly = aggregateByCategory(BudgetPeriod.WEEKLY)
        assertEquals(375.50, weekly["cat_food"] ?: 0.0, 0.001)
        assertEquals(80.0, weekly["cat_transport"] ?: 0.0, 0.001)
        assertFalse("last month's food spend must not leak into the week", weekly.containsKey("cat_health"))

        val monthly = aggregateByCategory(BudgetPeriod.MONTHLY)
        assertEquals(775.50, monthly["cat_food"] ?: 0.0, 0.001)
        assertEquals(80.0, monthly["cat_transport"] ?: 0.0, 0.001)
        assertFalse("last month's health spend must not appear", monthly.containsKey("cat_health"))
        assertFalse("a category with no expenses has no aggregate row", monthly.containsKey("cat_never_used"))

        val yearly = aggregateByCategory(BudgetPeriod.YEARLY)
        assertEquals(1_675.50, yearly["cat_food"] ?: 0.0, 0.001)
        assertEquals(380.0, yearly["cat_transport"] ?: 0.0, 0.001)
        assertEquals(700.0, yearly["cat_health"] ?: 0.0, 0.001)
    }

    @Test
    fun `Budget, Plan and Dashboard all read the same number from the aggregate`() = runBlocking {
        repository.saveTransactions(
            listOf(
                txn("food-week", 250.0, "cat_food", at(2026, 8, 20, 10, 30)),
                txn("food-month", 400.0, "cat_food", at(2026, 8, 2, 9, 0)),
                txn("food-year", 1_100.0, "cat_food", at(2026, 3, 3, 9, 0)),
                txn("transport-year", 640.0, "cat_transport", at(2026, 5, 5, 9, 0))
            )
        )
        val stored = listOf(
            Budget(id = "b1", categoryId = "cat_food", limitAmount = 2_000.0, period = BudgetPeriod.WEEKLY),
            Budget(id = "b2", categoryId = "cat_food", limitAmount = 5_000.0, period = BudgetPeriod.MONTHLY),
            Budget(id = "b3", categoryId = "cat_food", limitAmount = 20_000.0, period = BudgetPeriod.YEARLY),
            Budget(id = "b4", categoryId = "cat_transport", limitAmount = 8_000.0, period = BudgetPeriod.YEARLY)
        )

        // What the full-scan definition says, for the same rows and the same now.
        val rows = repository.getAllTransactions().first()
        val expected = stored.sumOf { BudgetSpending.spentFor(it, rows, now) }

        val totalsByPeriod = BudgetPeriod.entries.associateWith { aggregateByCategory(it) }
        fun totalsFor(budget: Budget) = totalsByPeriod.getValue(budget.period)

        val budgetTotal = stored.sumOf { budgetScreenSpend(it, totalsFor(it)) }
        val planTotal = stored.sumOf { planScreenSpend(it, totalsFor(it)) }
        // DashboardViewModel combines exactly the same three maps, keyed by period.
        val dashboardTotal = stored.sumOf { dashboardSpend(it, totalsFor(it)) }

        // 250 + (250 + 400) + (250 + 400 + 1100) + 640 = 3,290
        assertEquals(3_290.0, expected, 0.001)
        assertEquals(expected, budgetTotal, 0.001)
        assertEquals(expected, planTotal, 0.001)
        assertEquals(expected, dashboardTotal, 0.001)
    }

    @Test
    fun `period boundaries are inclusive on both ends for the aggregate and the scan`() = runBlocking {
        // One category per period, so a weekly row sitting inside the month (which it
        // does) cannot contaminate the monthly assertion.
        BudgetPeriod.entries.forEach { period ->
            val (start, end) = BudgetSpending.periodRange(period, now)
            val (startEpoch, endEpoch) = epochRange(period)
            val category = "cat_${period.name.lowercase()}"

            repository.saveTransactions(
                listOf(
                    // Exactly at the first and last millisecond of the period…
                    txn("start-${period.name}", 1.0, category, start),
                    txn("end-${period.name}", 2.0, category, end),
                    // …and just past the end, which must not count. A whole second
                    // later, not a millisecond: `end` is 23:59:59.999, and a local
                    // time one millisecond on can be ambiguous in a zone whose
                    // clocks fall back at midnight, which would make this assertion
                    // depend on the test machine's zone.
                    txn("after-${period.name}", 4.0, category, end.plusSeconds(1))
                )
            )

            assertEquals(
                "$period: the calendar period must start where the epoch range starts",
                start.atZone(zone).toInstant().toEpochMilli(),
                startEpoch
            )
            assertEquals(
                "$period: the calendar period must end where the epoch range ends",
                end.atZone(zone).toInstant().toEpochMilli(),
                endEpoch
            )
            assertEquals(
                "$period: the aggregate must include both boundary rows and exclude the one just past the end",
                3.0,
                aggregateByCategory(period)[category] ?: 0.0,
                0.001
            )
            assertEquals(
                "$period: full scan disagrees on the boundaries",
                3.0,
                scanFor(period, category),
                0.001
            )
        }
    }

    @Test
    fun `a row after the end of the period is counted by neither path`() = runBlocking {
        BudgetPeriod.entries.forEach { period ->
            val category = "cat_past_${period.name.lowercase()}"
            val dayAfter = BudgetSpending.periodRange(period, now).second
                .toLocalDate().plusDays(1).atStartOfDay()
            repository.saveTransactions(
                listOf(txn("past-${period.name}", 99.0, category, dayAfter))
            )

            assertEquals(
                "$period: the aggregate counted a row outside the period",
                0.0,
                aggregateByCategory(period)[category] ?: 0.0,
                0.001
            )
            assertEquals(
                "$period: the full scan counted a row outside the period",
                0.0,
                scanFor(period, category),
                0.001
            )
            assertTrue("epoch range must be ordered", epochRange(period).first < epochRange(period).second)
        }
    }
}
