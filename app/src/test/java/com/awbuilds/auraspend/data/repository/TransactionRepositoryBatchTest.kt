package com.awbuilds.auraspend.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.local.AppDatabase
import com.awbuilds.auraspend.data.local.dao.TransactionDao
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The CSV-import write path against a real in-memory database.
 *
 * The regression this pins: `saveTransactions` used to be a per-row loop of
 * update+insert pairs, each in its own implicit transaction. A file that failed
 * halfway (a UNIQUE violation on the second row, a revoked SAF grant mid-read)
 * persisted the rows already written while the caller reported failure — the
 * user could not tell which of their file's rows had actually landed. The batch
 * now runs inside one Room transaction (`upsertTransactions`), so it is
 * all-or-nothing like the bulk insert it replaced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.awbuilds.auraspend.TestApplication::class)
class TransactionRepositoryBatchTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao
    private lateinit var repository: TransactionRepositoryImpl

    private val when_ = LocalDateTime.of(2026, 10, 11, 12, 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.transactionDao()
        repository = TransactionRepositoryImpl(
            transactionDao = dao,
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
        sourceSmsId: String? = null
    ) = Transaction(
        id = id,
        amount = amount,
        categoryId = "cat-food",
        note = "note $id",
        date = when_,
        type = TransactionType.EXPENSE,
        sourceSmsId = sourceSmsId
    )

    @Test
    fun `a batch that fails halfway persists nothing`() = runBlocking {
        dao.insertTransactionIfAbsent(
            com.awbuilds.auraspend.data.local.entities.TransactionEntity(
                id = "owner", amount = 100.0, categoryId = "cat-food", note = "edited",
                dateTimestamp = 1_780_000_000_000L, type = "EXPENSE", sourceSmsId = "sms-1"
            )
        )
        dao.insertTransactionIfAbsent(
            com.awbuilds.auraspend.data.local.entities.TransactionEntity(
                id = "other", amount = 50.0, categoryId = "cat-food", note = "other",
                dateTimestamp = 1_780_000_000_001L, type = "EXPENSE", sourceSmsId = "sms-2"
            )
        )

        // Row one is a clean insert; row two is an update of "owner" that claims a
        // `sourceSmsId` "other" already owns — a UNIQUE violation inside the batch.
        val failure = runCatching {
            repository.saveTransactions(
                listOf(
                    txn("fresh", 7.0),
                    txn("owner", 250.0, sourceSmsId = "sms-2")
                )
            )
        }.exceptionOrNull()

        assertNotNull("the constraint violation must escape so the caller can report it", failure)
        assertEquals(
            listOf("other", "owner"),
            dao.getAllOnce().map { it.id }.sorted()
        )
        assertEquals(100.0, dao.getTransactionById("owner")?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `a batch updates rows already present and inserts the rest`() = runBlocking {
        dao.insertTransactionIfAbsent(
            com.awbuilds.auraspend.data.local.entities.TransactionEntity(
                id = "owner", amount = 100.0, categoryId = "cat-food", note = "edited",
                dateTimestamp = 1_780_000_000_000L, type = "EXPENSE", sourceSmsId = "sms-1"
            )
        )

        repository.saveTransactions(
            listOf(
                txn("owner", 250.0, sourceSmsId = "sms-1"),
                txn("fresh", 7.0)
            )
        )

        assertEquals(250.0, dao.getTransactionById("owner")?.amount ?: 0.0, 0.001)
        assertEquals(7.0, dao.getTransactionById("fresh")?.amount ?: 0.0, 0.001)
        assertEquals(2, dao.getAllOnce().size)
    }
}
