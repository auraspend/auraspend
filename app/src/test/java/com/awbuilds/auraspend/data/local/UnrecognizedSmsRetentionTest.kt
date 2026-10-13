package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.data.local.entities.UnrecognizedSmsEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The unrecognized-message retention policy, run against real Room.
 *
 * `purgeOlderThan` was implemented, documented and never called — so the table
 * grew for ever and every Drive backup carried an unbounded archive of bank
 * messages. The wiring is what the worker does; this pins the cutoff arithmetic
 * the wiring depends on, and the boundary the policy must not cross: the
 * `sms_messages` queue is *not* swept, because those rows are the app's visible
 * history and the user's route back to a message it could not file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class UnrecognizedSmsRetentionTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: UnrecognizedSmsRepository

    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = UnrecognizedSmsRepository(db.unrecognizedSmsDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record(id: String, ageDays: Long) = UnrecognizedSmsEntity(
        id = id,
        sender = "AXISBK",
        body = "Your a/c was debited INR 500 on 01-10",
        receivedAt = now - ageDays * day,
        createdAt = now - ageDays * day
    )

    @Test
    fun `messages older than the retention window are dropped and newer ones kept`() =
        runBlocking {
            db.unrecognizedSmsDao().insertAll(
                listOf(
                    record("too-old", 31),
                    record("boundary-old", 30),
                    record("just-inside", 29),
                    record("today", 0)
                )
            )

            repository.purgeOlderThan()

            val kept = db.unrecognizedSmsDao().getAll().map { it.id }.toSet()
            assertEquals(
                "the cutoff is 30 days: anything older goes, anything newer stays",
                setOf("just-inside", "today"),
                kept
            )
        }

    @Test
    fun `the retention window is configurable and the default is 30 days`() = runBlocking {
        db.unrecognizedSmsDao().insertAll(listOf(record("week-old", 8), record("day-old", 1)))

        repository.purgeOlderThan(days = 7)

        assertEquals(
            "a 7-day sweep keeps yesterday and drops last week",
            setOf("day-old"),
            db.unrecognizedSmsDao().getAll().map { it.id }.toSet()
        )
    }

    @Test
    fun `the sweep never touches the classified-message queue`() = runBlocking {
        // Two different tables, and the difference is load-bearing: the queue is
        // what the SMS tab shows the user and what Smart Add lets them file by
        // hand. Purging it would delete the visible history of messages the app
        // declined to save.
        db.smsMessageDao().insertAll(
            listOf(
                SmsMessageEntity(
                    id = "queued-new",
                    address = "HDFCBank",
                    body = "INR 149 debited at Swiggy",
                    receivedAt = now - 400 * day,
                    status = SmsMessageStatus.NEW.name
                ),
                SmsMessageEntity(
                    id = "queued-skipped",
                    address = "HDFCBank",
                    body = "Your OTP is 482913",
                    receivedAt = now - 400 * day,
                    status = SmsMessageStatus.SKIPPED.name
                )
            )
        )
        db.unrecognizedSmsDao().insertAll(listOf(record("stale", 90)))

        repository.purgeOlderThan()

        val queue = db.smsMessageDao().getAllOnce().map { it.id }.toSet()
        assertEquals(setOf("queued-new", "queued-skipped"), queue)
        assertTrue(
            "and the stale unrecognized row is still gone",
            db.unrecognizedSmsDao().getAll().isEmpty()
        )
    }
}
