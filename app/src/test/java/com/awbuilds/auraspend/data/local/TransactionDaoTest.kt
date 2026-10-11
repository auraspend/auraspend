package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.local.dao.TransactionDao
import com.awbuilds.auraspend.data.local.entities.TransactionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dashboard's numbers come from SQL that had no test at all: the suite used
 * `TestTransactionRepositoryDefaults`, which re-implements the aggregates in
 * memory, so a wrong GROUP BY, a shifted range boundary or a broken LIKE filter
 * would have passed the whole build and been wrong on the device.
 *
 * Everything here runs against a real in-memory Room database, so it pins the
 * generated SQL rather than a Kotlin re-implementation of it.
 *
 * **Timezone note (read before changing the day-bucket test).** The daily
 * aggregate buckets with SQLite's `date(..., 'unixepoch', 'localtime')`, i.e. the
 * *host* zone as the C library sees it, while `Mappers.toDomain()` dates rows
 * with `ZoneId.systemDefault()`. The tests therefore never hardcode a zone:
 * `sqliteLocalOffsetSeconds` asks SQLite what its own offset is for the exact
 * instants under test and the expectation is computed from that. On a UTC host
 * `localtime` is a no-op and local == UTC, so no test can distinguish them there
 * — that limitation is stated where it applies instead of being papered over
 * with a hardcoded IST expectation that only passes on one machine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class TransactionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao

    /** A fixed, arbitrary instant; every range below is relative to it. */
    private val start = 1_780_000_000_000L
    private val end = start + 30L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── Fixtures and helpers ─────────────────────────────────────────────────

    private suspend fun insert(
        id: String,
        amount: Double,
        type: String,
        dateTimestamp: Long,
        categoryId: String = "cat-food",
        note: String = "note $id",
        merchant: String? = null,
        sourceSmsId: String? = null
    ) {
        dao.insertTransactionIfAbsent(
            TransactionEntity(
                id = id,
                amount = amount,
                categoryId = categoryId,
                note = note,
                merchant = merchant,
                dateTimestamp = dateTimestamp,
                type = type,
                sourceSmsId = sourceSmsId
            )
        )
    }

    /**
     * Loads one page. [append] mirrors how Paging 3 continues a list: the next
     * page is an `Append` whose key is the previous page's `nextKey` (a `Refresh`
     * with a key re-reads the page *containing* that key, which is not the same
     * thing and would silently re-test page one).
     */
    private suspend fun loadPage(
        source: PagingSource<Int, TransactionEntity>,
        key: Int? = null,
        loadSize: Int = 50,
        append: Boolean = false
    ): PagingSource.LoadResult.Page<Int, TransactionEntity> {
        val params = if (append) {
            PagingSource.LoadParams.Append(
                key = requireNotNull(key) { "an append needs a key" },
                loadSize = loadSize,
                placeholdersEnabled = false
            )
        } else {
            PagingSource.LoadParams.Refresh(key = key, loadSize = loadSize, placeholdersEnabled = false)
        }
        val result = source.load(params)
        require(result is PagingSource.LoadResult.Page) { "expected a page, got $result" }
        return result
    }

    private suspend fun page(
        query: String? = null,
        type: String? = null,
        categoryId: String? = null,
        loadSize: Int = 50
    ): List<TransactionEntity> =
        loadPage(dao.pagedTransactions(query, type, categoryId), loadSize = loadSize).data

    /** What SQLite itself thinks its local UTC offset is at [timestamp], in seconds. */
    private fun sqliteLocalOffsetSeconds(timestamp: Long): Int {
        val epochSeconds = timestamp / 1000
        val cursor = db.openHelper.writableDatabase.query(
            "SELECT CAST(strftime('%s', datetime($epochSeconds, 'unixepoch', 'localtime')) AS INTEGER)"
        )
        return cursor.use {
            require(it.moveToFirst()) { "strftime returned no row" }
            (it.getLong(0) - epochSeconds).toInt()
        }
    }

    /** The day SQLite's `date()` reports for [timestamp] with the given modifiers. */
    private fun sqliteDay(timestamp: Long, localtime: Boolean): String {
        val modifiers = if (localtime) "'unixepoch', 'localtime'" else "'unixepoch'"
        val cursor = db.openHelper.writableDatabase.query(
            "SELECT date(${timestamp / 1000}, $modifiers)"
        )
        return cursor.use {
            require(it.moveToFirst()) { "date() returned no row" }
            it.getString(0)
        }
    }

    // ─── SUM / CASE aggregate (Daos.kt observeSummary) ────────────────────────

    @Test
    fun `the summary aggregate totals each type and counts rows in range`() = runBlocking {
        insert("inc-1", 1_000.0, "INCOME", start + 10)
        insert("exp-1", 200.0, "EXPENSE", start + 20)
        insert("exp-2", 300.5, "EXPENSE", end - 10)
        insert("out-of-range", 9_999.0, "EXPENSE", end + 1)

        val summary = dao.observeSummary(start, end).first()

        assertEquals(1_000.0, summary.income, 0.001)
        assertEquals(500.5, summary.expense, 0.001)
        assertEquals(1, summary.incomeCount)
        assertEquals(2, summary.expenseCount)
    }

    @Test
    fun `the summary aggregate returns zeros for an empty range`() = runBlocking {
        // COALESCE covers income/expense; the counts rely on SUM over zero rows
        // being NULL, which Room reads as 0 for a non-null Int.
        val summary = dao.observeSummary(start, end).first()

        assertEquals(0.0, summary.income, 0.001)
        assertEquals(0.0, summary.expense, 0.001)
        assertEquals(0, summary.incomeCount)
        assertEquals(0, summary.expenseCount)
    }

    @Test
    fun `range bounds are inclusive at both ends`() = runBlocking {
        insert("at-start", 1.0, "EXPENSE", start)
        insert("at-end", 2.0, "EXPENSE", end)

        val summary = dao.observeSummary(start, end).first()

        assertEquals(2, summary.expenseCount)
        assertEquals(3.0, summary.expense, 0.001)
    }

    @Test
    fun `the balance aggregate is income minus expense`() = runBlocking {
        insert("inc", 1_000.0, "INCOME", start)
        insert("exp", 250.0, "EXPENSE", start)

        assertEquals(750.0, dao.observeBalance().first(), 0.001)
    }

    // ─── GROUP BY categoryId (Daos.kt observeExpenseByCategory) ───────────────

    @Test
    fun `the category aggregate groups expenses and ignores income and out-of-range rows`() = runBlocking {
        insert("food-1", 100.0, "EXPENSE", start + 1, categoryId = "food")
        insert("food-2", 50.0, "EXPENSE", start + 2, categoryId = "food")
        insert("travel-1", 25.0, "EXPENSE", start + 3, categoryId = "travel")
        insert("income-food", 500.0, "INCOME", start + 4, categoryId = "food")
        insert("expense-outside", 999.0, "EXPENSE", end + 1, categoryId = "food")

        val totals = dao.observeExpenseByCategory(start, end).first()
            .associate { it.categoryId to it.total }

        assertEquals(2, totals.size)
        assertEquals(150.0, requireNotNull(totals["food"]), 0.001)
        assertEquals(25.0, requireNotNull(totals["travel"]), 0.001)
    }

    // ─── GROUP BY local day (Daos.kt observeDailyExpense) ─────────────────────

    @Test
    fun `daily expense buckets use SQLite's local calendar day`() = runBlocking {
        val dayStartUtc = Instant.parse("2026-08-01T00:00:00Z").toEpochMilli()
        val hour = 60L * 60 * 1000
        val timestamps = (0 until 24).map { dayStartUtc + it * hour }
        timestamps.forEachIndexed { index, ts -> insert("exp-$index", 1.0, "EXPENSE", ts) }
        insert("income-in-window", 1_000.0, "INCOME", dayStartUtc + 60_000)
        insert("expense-outside", 999.0, "EXPENSE", dayStartUtc + 24 * hour)

        val rows = dao.observeDailyExpense(dayStartUtc, dayStartUtc + 24 * hour - 1).first()

        // The expected buckets are computed from the offset SQLite reports for
        // these exact instants, so this is correct in any host zone — including a
        // DST transition inside the window — and does not encode IST. In a UTC
        // host `localtime` is a no-op, so a query that forgot the modifier would
        // still pass here; on any other host it would bucket into a single UTC day
        // and the assertion below fails.
        val expectedDays = timestamps.map { ts ->
            Instant.ofEpochMilli(ts)
                .atOffset(ZoneOffset.ofTotalSeconds(sqliteLocalOffsetSeconds(ts)))
                .toLocalDate()
                .toString()
        }.toSet()

        assertEquals(expectedDays, rows.map { it.day }.toSet())
        assertEquals(
            "every in-range expense must land in exactly one bucket",
            24.0,
            rows.sumOf { it.total },
            0.001
        )
        assertEquals("buckets must be distinct days", rows.size, rows.map { it.day }.toSet().size)
    }

    @Test
    fun `SQLite localtime and the JVM default zone agree, so day labels match row dates`() {
        // Mappers.toDomain() dates a row with ZoneId.systemDefault() while the
        // daily chart buckets it with SQLite's `localtime`. If the two ever
        // disagree, a transaction shown as "1 Aug" would be charted on another
        // day. Asserting the agreement is what makes the day-bucket test above
        // meaningful without hardcoding a zone.
        val timestamps = listOf(
            Instant.parse("2026-08-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2026-01-01T18:30:00Z").toEpochMilli(),
            Instant.parse("2026-12-31T23:59:00Z").toEpochMilli()
        )

        timestamps.forEach { ts ->
            val jvmDay = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            assertEquals(
                "SQLite's localtime disagrees with the JVM's default zone for $ts",
                jvmDay,
                sqliteDay(ts, localtime = true)
            )
        }

        // Sanity: the helper really does apply the modifier. The probe has to be
        // an instant that lands on a different *calendar day* once the offset is
        // applied — 00:00Z is still 01 Aug in IST, so a fixed instant near either
        // end of the UTC day is the only shape that works for any real offset
        // (all of which are multiples of 15 minutes). Vacuous on a UTC host, and
        // this comment says so rather than pretending otherwise.
        val ts = timestamps.first()
        val offset = sqliteLocalOffsetSeconds(ts)
        if (offset != 0) {
            val probe = if (offset > 0) "2026-08-01T23:59:30Z" else "2026-08-01T00:00:30Z"
            val probeMillis = Instant.parse(probe).toEpochMilli()
            assertNotEquals(
                "the localtime modifier must change the day for $probe at offset ${offset}s",
                sqliteDay(probeMillis, localtime = false),
                sqliteDay(probeMillis, localtime = true)
            )
        }
    }

    // ─── Paged LIKE filter (Daos.kt pagedTransactions) ────────────────────────

    @Test
    fun `the paged filter matches note or merchant, case-insensitively`() = runBlocking {
        insert("note-hit", 1.0, "EXPENSE", start + 3, note = "Swiggy order")
        insert("merchant-hit", 2.0, "EXPENSE", start + 2, note = "Lunch", merchant = "Swiggy")
        insert("miss", 3.0, "EXPENSE", start + 1, note = "Petrol", merchant = "HP")

        assertEquals(
            listOf("note-hit", "merchant-hit"),
            page(query = "swiggy").map { it.id }
        )
        assertTrue("no match must be an empty page, not everything", page(query = "zzz").isEmpty())
    }

    @Test
    fun `the paged filter honours type, category and an empty query`() = runBlocking {
        insert("exp-food", 10.0, "EXPENSE", start + 1, categoryId = "food", note = "Coffee")
        insert("inc-food", 20.0, "INCOME", start + 2, categoryId = "food", note = "Salary")
        insert("exp-travel", 30.0, "EXPENSE", start + 3, categoryId = "travel", note = "Train")

        // NULL and '' both mean "no query constraint"; the ordering is newest first.
        assertEquals(listOf("exp-travel", "inc-food", "exp-food"), page(query = null).map { it.id })
        assertEquals(listOf("exp-travel", "inc-food", "exp-food"), page(query = "").map { it.id })
        // Newest first within each filter: expenses are exp-travel then exp-food,
        // and category "food" holds inc-food then exp-food.
        assertEquals(listOf("exp-travel", "exp-food"), page(type = "EXPENSE").map { it.id })
        assertEquals(listOf("inc-food", "exp-food"), page(categoryId = "food").map { it.id })
        assertEquals(
            listOf("exp-travel"),
            page(query = "Train", type = "EXPENSE", categoryId = "travel").map { it.id }
        )
    }

    @Test
    fun `paging is bounded by loadSize and consecutive pages do not overlap`() = runBlocking {
        insert("t1", 1.0, "EXPENSE", start + 1)
        insert("t2", 2.0, "EXPENSE", start + 2)
        insert("t3", 3.0, "EXPENSE", start + 3)

        val first = loadPage(dao.pagedTransactions(null, null, null), loadSize = 2)
        assertEquals(listOf("t3", "t2"), first.data.map { it.id })
        val nextKey = requireNotNull(first.nextKey) { "a full page must offer a next key" }

        val second = loadPage(
            dao.pagedTransactions(null, null, null),
            key = nextKey,
            loadSize = 2,
            append = true
        )
        assertEquals(listOf("t1"), second.data.map { it.id })
        assertEquals(
            "the two pages must cover every row exactly once",
            listOf("t3", "t2", "t1"),
            first.data.map { it.id } + second.data.map { it.id }
        )
    }

    @Test
    fun `a LIKE wildcard in the query behaves as a wildcard because the SQL does not escape it`() = runBlocking {
        insert("coffee", 1.0, "EXPENSE", start + 1, note = "Coffee")
        insert("tea", 2.0, "EXPENSE", start + 2, note = "Tea")

        // Current, deliberate-as-is behaviour: `note LIKE '%' || :query || '%'`
        // passes `%` and `_` through to SQLite, so a user typing "%" matches every
        // row rather than none. Pinned so that adding escaping is a conscious
        // change: if the SQL is fixed, this test must be inverted (a literal "%"
        // would then match nothing here).
        assertEquals(2, page(query = "%").size)
        assertEquals(2, page(query = "_").size)
        assertEquals(1, page(query = "Co_").size)
    }

    // ─── Duplicate writes (review #7 contract) ────────────────────────────────

    @Test
    fun `a second row with the same sourceSmsId is ignored, not replaced`() = runBlocking {
        // The regression: `sourceSmsId` is unique and the insert used to be REPLACE,
        // which SQLite implements as delete-then-insert — so saving the same
        // SMS-linked transaction twice (a worker/triage race) deleted the row the
        // user had already edited and re-created it from the pipeline's parse.
        insert("first", 100.0, "EXPENSE", start, note = "edited by the user", sourceSmsId = "sms-7")

        // Must not throw, and must not touch the existing row.
        insert("second", 999.0, "EXPENSE", start + 1, note = "pipeline parse", sourceSmsId = "sms-7")

        val rows = dao.getAllOnce()
        assertEquals(1, rows.size)
        assertEquals("first", rows.single().id)
        assertEquals(100.0, rows.single().amount, 0.001)
        assertEquals("edited by the user", rows.single().note)
        assertEquals("first", dao.getTransactionBySourceSmsId("sms-7")?.id)

        // The bulk path (CSV import, atomic restore) is IGNORE as well.
        dao.insertTransactions(
            listOf(
                TransactionEntity(
                    id = "third",
                    amount = 5.0,
                    categoryId = "cat-food",
                    note = "bulk",
                    dateTimestamp = start,
                    type = "EXPENSE",
                    sourceSmsId = "sms-7"
                )
            )
        )

        val afterBulk = dao.getAllOnce()
        assertEquals(1, afterBulk.size)
        assertEquals("first", afterBulk.single().id)
        assertEquals("first", dao.getTransactionBySourceSmsId("sms-7")?.id)
    }
}
