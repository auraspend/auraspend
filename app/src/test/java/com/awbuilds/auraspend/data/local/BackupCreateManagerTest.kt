package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.local.entities.BudgetEntity
import com.awbuilds.auraspend.data.local.entities.CategoryEntity
import com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity
import com.awbuilds.auraspend.data.local.entities.SavingsGoalEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SubscriptionEntity
import com.awbuilds.auraspend.data.local.entities.TransactionEntity
import com.awbuilds.auraspend.data.local.entities.UnrecognizedSmsEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup must carry every user-owned table.
 *
 * Restore replaces each table wholesale, so a section this manager forgets to
 * read is not "left as it was" on a later restore — it is deleted. The test
 * therefore seeds one row per table and requires every section to come back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class BackupCreateManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: BackupCreateManager

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        manager = BackupCreateManager(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `backup carries a row from every user-owned table`() = runBlocking {
        db.transactionDao().insertTransactionIfAbsent(
            TransactionEntity(
                id = "tx-1",
                amount = 149.0,
                categoryId = "cat-1",
                note = "Swiggy order",
                dateTimestamp = 1_780_000_000_000L,
                type = "EXPENSE",
                sourceSmsId = "sms-1"
            )
        )
        db.categoryDao().insertCategory(
            CategoryEntity(id = "cat-1", name = "Food", icon = "Restaurant", color = 0xFFB56A00.toInt(), isDefault = false)
        )
        db.budgetDao().insertBudget(
            BudgetEntity(id = "budget-1", categoryId = "cat-1", limitAmount = 5_000.0, period = "MONTHLY")
        )
        db.subscriptionDao().insertSubscription(
            SubscriptionEntity(
                id = "sub-1",
                name = "Music",
                amount = 99.0,
                categoryId = "cat-1",
                billingCycle = "MONTHLY",
                nextBillingDateTimestamp = 1_790_000_000_000L
            )
        )
        db.savingsGoalDao().insertSavingsGoal(
            SavingsGoalEntity(id = "goal-1", name = "Laptop", targetAmount = 60_000.0)
        )
        db.smsMessageDao().insertAll(
            listOf(
                SmsMessageEntity(
                    id = "sms-1",
                    address = "HDFCBank",
                    body = "INR 149 debited at Swiggy",
                    receivedAt = 1_780_000_000_000L
                )
            )
        )
        db.classificationMemoryDao().upsert(
            ClassificationMemoryEntity(
                memoryKey = "swiggy",
                categoryId = "cat-1",
                source = "USER_SAVE",
                hits = 2,
                updatedAt = 1_780_000_000_000L
            )
        )
        db.unrecognizedSmsDao().insertAll(
            listOf(
                UnrecognizedSmsEntity(
                    id = "unrec-1",
                    sender = "AXISBK",
                    body = "Your a/c was debited INR 500 on 01-10",
                    receivedAt = 1_780_000_000_000L,
                    createdAt = 1_780_000_000_000L
                )
            )
        )

        val json = requireNotNull(manager.createBackupJson())
        val data = BackupSerializer.deserialize(json)

        assertEquals(1, data.transactions.size)
        assertEquals("Swiggy order", data.transactions.first().note)
        assertEquals("sms-1", data.transactions.first().sourceSmsId)
        assertEquals(1, data.categories.size)
        assertEquals(1, data.budgets.size)
        assertEquals(1, data.subscriptions.size)
        assertEquals(1, data.savingsGoals.size)
        assertEquals(1, data.smsMessages.size)
        assertEquals(1, data.classificationMemory.size)
        assertEquals(1, data.unrecognizedSms.size)
    }

    @Test
    fun `empty database produces a readable empty backup`() = runBlocking {
        val json = requireNotNull(manager.createBackupJson())
        val data = BackupSerializer.deserialize(json)

        assertEquals(0, data.transactions.size)
        assertEquals(0, data.categories.size)
        assertEquals(0, data.budgets.size)
        assertEquals(0, data.subscriptions.size)
        assertEquals(0, data.savingsGoals.size)
        assertEquals(0, data.smsMessages.size)
        assertEquals(0, data.classificationMemory.size)
        assertEquals(0, data.unrecognizedSms.size)
    }
}
