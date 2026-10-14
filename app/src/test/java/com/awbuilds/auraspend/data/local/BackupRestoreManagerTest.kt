package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.local.entities.BudgetEntity
import com.awbuilds.auraspend.data.local.entities.CategoryEntity
import com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity
import com.awbuilds.auraspend.data.local.entities.SavingsGoalEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.data.local.entities.SubscriptionEntity
import com.awbuilds.auraspend.data.local.entities.TransactionEntity
import com.awbuilds.auraspend.data.local.entities.UnrecognizedSmsEntity
import com.awbuilds.auraspend.data.privacy.SensitiveDataMasker
import com.awbuilds.auraspend.domain.model.Budget
import com.awbuilds.auraspend.domain.model.BudgetPeriod
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.RecurrenceFrequency
import com.awbuilds.auraspend.domain.model.SavingsGoal
import com.awbuilds.auraspend.domain.model.Subscription
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restore is the only operation that can delete every row the user owns, and it
 * was enforced by code review alone — `BackupCreateManagerTest` covers the
 * snapshot direction only.
 *
 * These tests run against a real in-memory Room database (the pattern
 * [BackupCreateManagerTest] established) so the properties that matter are
 * properties of the database, not of a fake:
 *
 * 1. a restore is a *replace*: every table it touches ends up with the backup's
 *    rows and none of the local ones;
 * 2. it is atomic: a failure part-way through rolls back the clears as well, so
 *    the user's data survives a bad payload;
 * 3. it re-masks on the way in, so restoring a pre-masker (v1–v3) backup cannot
 *    write raw bank message bodies back into the database;
 * 4. the SMS queue keeps the status the backup recorded, terminal rows included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class BackupRestoreManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: BackupRestoreManager

    private val date: LocalDateTime = LocalDateTime.of(2026, 8, 1, 12, 0)
    private val timestamp = 1_780_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        manager = BackupRestoreManager(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    /** One row per user-owned table, all with `local-` ids. */
    private suspend fun seedLocalRows() {
        db.transactionDao().insertTransactionIfAbsent(
            TransactionEntity(
                id = "local-tx",
                amount = 10.0,
                categoryId = "local-cat",
                note = "Local note",
                dateTimestamp = timestamp,
                type = TransactionType.EXPENSE.name,
                sourceSmsId = "local-source"
            )
        )
        db.categoryDao().insertCategory(
            CategoryEntity(id = "local-cat", name = "Local", icon = "Restaurant", color = 0x112233, isDefault = false)
        )
        db.budgetDao().insertBudget(
            BudgetEntity(id = "local-budget", categoryId = "local-cat", limitAmount = 1_000.0, period = "MONTHLY")
        )
        db.subscriptionDao().insertSubscription(
            SubscriptionEntity(
                id = "local-sub",
                name = "Local sub",
                amount = 10.0,
                categoryId = "local-cat",
                billingCycle = RecurrenceFrequency.MONTHLY.name,
                nextBillingDateTimestamp = timestamp
            )
        )
        db.savingsGoalDao().insertSavingsGoal(
            SavingsGoalEntity(id = "local-goal", name = "Local goal", targetAmount = 100.0)
        )
        db.smsMessageDao().insertAll(
            listOf(
                SmsMessageEntity(
                    id = "local-sms",
                    address = "HDFCBK",
                    body = "local queue body",
                    receivedAt = timestamp,
                    status = SmsMessageStatus.NEW.name
                )
            )
        )
        db.classificationMemoryDao().upsert(
            ClassificationMemoryEntity(
                memoryKey = "local-key",
                categoryId = "local-cat",
                source = ClassificationMemoryEntity.SOURCE_USER_SAVE,
                hits = 1,
                updatedAt = timestamp
            )
        )
        db.unrecognizedSmsDao().insertAll(
            listOf(
                UnrecognizedSmsEntity(
                    id = "local-unrec",
                    sender = "AXISBK",
                    body = "local unrecognized body",
                    receivedAt = timestamp,
                    createdAt = timestamp
                )
            )
        )
    }

    /** A complete backup: one row in every section, all with `backup-` ids. */
    private fun backup(
        transactionNote: String = "Backup note",
        subscriptionDate: LocalDateTime = date,
        smsBody: String = "INR 149 debited at Swiggy",
        unrecognizedBody: String = "Your a/c was debited INR 500 on 01-10",
        smsStatus: String = SmsMessageStatus.SAVED.name
    ) = BackupData(
        transactions = listOf(
            Transaction(
                id = "backup-tx",
                amount = 499.0,
                categoryId = "backup-cat",
                note = transactionNote,
                merchant = "BackupMerchant",
                bankName = "HDFC",
                date = date,
                type = TransactionType.EXPENSE,
                sourceSmsId = "backup-source"
            )
        ),
        categories = listOf(
            Category(id = "backup-cat", name = "Backup", icon = "Restaurant", color = 0x445566, isDefault = false)
        ),
        budgets = listOf(
            Budget(id = "backup-budget", categoryId = "backup-cat", limitAmount = 2_000.0, period = BudgetPeriod.MONTHLY)
        ),
        subscriptions = listOf(
            Subscription(
                id = "backup-sub",
                name = "Backup sub",
                amount = 99.0,
                categoryId = "backup-cat",
                billingCycle = RecurrenceFrequency.MONTHLY,
                nextBillingDate = subscriptionDate
            )
        ),
        smsMessages = listOf(
            SmsMessageEntity(
                id = "backup-sms",
                address = "HDFCBK",
                body = smsBody,
                receivedAt = timestamp,
                status = smsStatus
            )
        ),
        savingsGoals = listOf(
            SavingsGoal(id = "backup-goal", name = "Backup goal", targetAmount = 5_000.0)
        ),
        classificationMemory = listOf(
            ClassificationMemoryEntity(
                memoryKey = "backup-key",
                categoryId = "backup-cat",
                source = ClassificationMemoryEntity.SOURCE_USER_SAVE,
                hits = 3,
                updatedAt = timestamp
            )
        ),
        unrecognizedSms = listOf(
            UnrecognizedSmsEntity(
                id = "backup-unrec",
                sender = "AXISBK",
                body = unrecognizedBody,
                receivedAt = timestamp,
                createdAt = timestamp
            )
        )
    )

    // ─── 1. Replace ───────────────────────────────────────────────────────────

    @Test
    fun `a restore replaces every table and leaves no local row behind`() = runBlocking {
        seedLocalRows()

        val summary = requireNotNull(manager.restore(backup()))

        assertEquals(1, summary.transactions)
        assertEquals(1, summary.categories)
        assertEquals(1, summary.budgets)
        assertEquals(1, summary.subscriptions)
        assertEquals(1, summary.savingsGoals)
        assertEquals(1, summary.smsMessages)
        assertEquals(1, summary.classificationMemory)

        // Every list has exactly the backup's row: if a clear were missing, the
        // local row would still be here next to it.
        val transactions = db.transactionDao().getAllOnce()
        assertEquals(listOf("backup-tx"), transactions.map { it.id })
        assertEquals("Backup note", transactions.single().note)
        assertEquals("BackupMerchant", transactions.single().merchant)
        assertEquals(listOf("backup-cat"), db.categoryDao().getAllOnce().map { it.id })
        assertEquals(listOf("backup-budget"), db.budgetDao().getAllOnce().map { it.id })
        assertEquals(listOf("backup-sub"), db.subscriptionDao().getAllOnce().map { it.id })
        assertEquals(listOf("backup-goal"), db.savingsGoalDao().getAllOnce().map { it.id })
        assertEquals(listOf("backup-sms"), db.smsMessageDao().getAllOnce().map { it.id })
        assertEquals(listOf("backup-key"), db.classificationMemoryDao().getAll().map { it.memoryKey })
        assertEquals(listOf("backup-unrec"), db.unrecognizedSmsDao().getAll().map { it.id })
    }

    @Test
    fun `an empty backup clears every table rather than merging`() = runBlocking {
        seedLocalRows()

        val summary = requireNotNull(
            manager.restore(
                BackupData(
                    transactions = emptyList(),
                    categories = emptyList(),
                    budgets = emptyList(),
                    subscriptions = emptyList()
                )
            )
        )

        assertEquals(0, summary.transactions)
        assertEquals(0, db.transactionDao().getAllOnce().size)
        assertEquals(0, db.categoryDao().getAllOnce().size)
        assertEquals(0, db.budgetDao().getAllOnce().size)
        assertEquals(0, db.subscriptionDao().getAllOnce().size)
        assertEquals(0, db.savingsGoalDao().getAllOnce().size)
        assertEquals(0, db.smsMessageDao().getAllOnce().size)
        assertEquals(0, db.classificationMemoryDao().getAll().size)
        assertEquals(0, db.unrecognizedSmsDao().getAll().size)
    }

    // ─── 2. Atomicity ─────────────────────────────────────────────────────────

    @Test
    fun `a failure inside the transaction leaves the database exactly as it was`() = runBlocking {
        seedLocalRows()

        // A backup whose subscription date is out of range. BackupSerializer parses
        // any LocalDateTime, so this payload survives the wire and only explodes in
        // `toEntity()` — i.e. inside the restore transaction, after all eight clears
        // and after transactions, categories and budgets have already been written.
        val hostile = backup(subscriptionDate = LocalDateTime.parse("+999999999-12-31T23:59:59"))
        val payload = BackupSerializer.deserialize(BackupSerializer.serialize(hostile))
        assertEquals("the payload must survive serialization", 1, payload.subscriptions.size)

        assertNull("a failed restore reports null instead of throwing", manager.restore(payload))

        // The clears and the writes that had already happened must both be gone:
        // this is the property the previous, non-transactional implementation lost.
        assertEquals(listOf("local-tx"), db.transactionDao().getAllOnce().map { it.id })
        assertEquals(listOf("local-cat"), db.categoryDao().getAllOnce().map { it.id })
        assertEquals(listOf("local-budget"), db.budgetDao().getAllOnce().map { it.id })
        assertEquals(listOf("local-sub"), db.subscriptionDao().getAllOnce().map { it.id })
        assertEquals(listOf("local-goal"), db.savingsGoalDao().getAllOnce().map { it.id })
        assertEquals(listOf("local-sms"), db.smsMessageDao().getAllOnce().map { it.id })
        assertEquals(listOf("local-key"), db.classificationMemoryDao().getAll().map { it.memoryKey })
        assertEquals(listOf("local-unrec"), db.unrecognizedSmsDao().getAll().map { it.id })
    }

    // ─── 3. Masking (review #11) ──────────────────────────────────────────────

    @Test
    fun `a legacy backup cannot put unmasked SMS bodies back into the database`() = runBlocking {
        val rawSmsBody = "Your a/c XX4821 debited. Call 9000000042 for help"
        val rawUnrecognizedBody = "Sent Rs.500 UPI Ref no 600000000001 -HDFC"
        val rawNote = "Paid to 9000000042"

        // The test is only meaningful if the masker actually changes this text.
        assertNotEquals(rawSmsBody, SensitiveDataMasker.mask(rawSmsBody))
        assertNotEquals(rawUnrecognizedBody, SensitiveDataMasker.mask(rawUnrecognizedBody))

        val legacy = backup(
            transactionNote = rawNote,
            smsBody = rawSmsBody,
            unrecognizedBody = rawUnrecognizedBody
        )
        requireNotNull(manager.restore(legacy))

        val storedSms = db.smsMessageDao().getAllOnce().single()
        assertEquals(SensitiveDataMasker.mask(rawSmsBody), storedSms.body)
        assertFalse("the phone number must not survive restore", storedSms.body.contains("9000000042"))

        val storedUnrecognized = db.unrecognizedSmsDao().getAll().single()
        assertEquals(SensitiveDataMasker.mask(rawUnrecognizedBody), storedUnrecognized.body)
        assertFalse(
            "the UPI reference must not survive restore",
            storedUnrecognized.body.contains("600000000001")
        )

        val storedNote = db.transactionDao().getAllOnce().single().note
        assertEquals(SensitiveDataMasker.mask(rawNote), storedNote)

        // Masking is idempotent: a second restore of the already-masked rows is a no-op.
        assertEquals(storedSms.body, SensitiveDataMasker.mask(storedSms.body))
        assertEquals(storedUnrecognized.body, SensitiveDataMasker.mask(storedUnrecognized.body))
    }

    // ─── 4. SMS queue statuses ────────────────────────────────────────────────

    @Test
    fun `the SMS queue keeps the status the backup recorded, terminal rows included`() = runBlocking {
        // A local row with the same id but a *different* status. Restore clears the
        // table first, so the insert must not silently keep the local status.
        db.smsMessageDao().insertAll(
            listOf(
                SmsMessageEntity(
                    id = "backup-sms",
                    address = "HDFCBK",
                    body = "locally queued body",
                    receivedAt = timestamp,
                    status = SmsMessageStatus.NEW.name
                )
            )
        )

        val withTwoRows = backup(smsStatus = SmsMessageStatus.SKIPPED.name).copy(
            smsMessages = listOf(
                SmsMessageEntity(
                    id = "backup-sms",
                    address = "HDFCBK",
                    body = "INR 149 debited at Swiggy",
                    receivedAt = timestamp,
                    status = SmsMessageStatus.SKIPPED.name,
                    attempts = 3
                ),
                SmsMessageEntity(
                    id = "backup-terminal",
                    address = "HDFCBK",
                    body = "INR 20 debited",
                    receivedAt = timestamp,
                    status = SmsMessageStatus.SAVED.name
                )
            )
        )

        val summary = requireNotNull(manager.restore(withTwoRows))
        assertEquals(2, summary.smsMessages)

        val byId = db.smsMessageDao().getAllOnce().associateBy { it.id }
        assertEquals(2, byId.size)
        val restored = requireNotNull(byId["backup-sms"])
        assertEquals(SmsMessageStatus.SKIPPED.name, restored.status)
        assertEquals(3, restored.attempts)
        assertEquals(
            SmsMessageStatus.SAVED.name,
            requireNotNull(byId["backup-terminal"]).status
        )
    }
}
