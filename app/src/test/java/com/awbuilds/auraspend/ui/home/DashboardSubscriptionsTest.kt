package com.awbuilds.auraspend.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.local.AppDatabase
import com.awbuilds.auraspend.data.repository.TransactionRepositoryImpl
import com.awbuilds.auraspend.domain.model.RecurrenceFrequency
import com.awbuilds.auraspend.domain.model.Subscription
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.classification.RealMainDispatcherRule
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Home subscriptions card and the Subscriptions screen must agree.
 *
 * Plan already had this bug: the hub read only the confirmed subscriptions
 * while the screen also showed the classifier's *detected* ones, so the count
 * was right about the data and wrong about the screen it linked to (0065). Home
 * now carries the same card, and it reads the same table the screen reads — so
 * this pins that the card's count and its monthly cost come from the confirmed
 * rows, with an inactive subscription left out and a yearly one normalised to a
 * twelfth of its price.
 *
 * It runs against a real in-memory database rather than a fake repository: the
 * query under test is `WHERE active = 1`, and a fake would only prove that the
 * ViewModel reads whatever the fake returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class DashboardSubscriptionsTest {

    @get:Rule
    val mainDispatcherRule = RealMainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: TransactionRepository

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

    private fun subscription(
        id: String,
        name: String,
        amount: Double,
        cycle: RecurrenceFrequency,
        active: Boolean = true
    ) = Subscription(
        id = id,
        name = name,
        amount = amount,
        categoryId = "cat_subscription",
        billingCycle = cycle,
        nextBillingDate = LocalDateTime.now().plusDays(10),
        active = active
    )

    @Test
    fun `the home card counts the confirmed subscriptions and what they cost a month`() = runBlocking {
        repository.saveSubscription(
            subscription("s1", "Streaming", 649.0, RecurrenceFrequency.MONTHLY)
        )
        repository.saveSubscription(
            subscription("s2", "Cloud storage", 1_200.0, RecurrenceFrequency.YEARLY)
        )
        // A cancelled one is not an active subscription and must not be counted.
        repository.saveSubscription(
            subscription("s3", "Cancelled", 99.0, RecurrenceFrequency.MONTHLY, active = false)
        )

        val viewModel = DashboardViewModel(repository)
        viewModel.handleIntent(DashboardViewIntent.LoadDashboard)

        val state = withTimeout(RealMainDispatcherRule.TIMEOUT_MS) {
            viewModel.state.first { !it.isLoading && it.activeSubscriptions.isNotEmpty() }
        }

        assertEquals(
            "The card must count the confirmed subscriptions, and only those",
            2,
            state.activeSubscriptions.size
        )
        // 649 a month, plus a year of 1,200 counted as 100 a month.
        assertEquals(
            "The monthly cost must be normalised, and must exclude the cancelled one",
            749.0,
            state.totalSubscriptionCost,
            0.001
        )
    }

    @Test
    fun `no subscriptions means no card and no cost`() = runBlocking {
        val viewModel = DashboardViewModel(repository)
        viewModel.handleIntent(DashboardViewIntent.LoadDashboard)

        val state = withTimeout(RealMainDispatcherRule.TIMEOUT_MS) {
            viewModel.state.first { !it.isLoading }
        }

        assertEquals(0, state.activeSubscriptions.size)
        assertEquals(0.0, state.totalSubscriptionCost, 0.001)
    }
}
