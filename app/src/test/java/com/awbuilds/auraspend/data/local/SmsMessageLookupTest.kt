package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The read-only path the transaction detail page uses to show the original
 * message.
 *
 * Two properties matter: it returns the stored row, and every failure —
 * unknown id, unavailable database — degrades to `null` so the page drops the
 * message card instead of losing the transaction. The body here is a synthetic
 * twin of a railway-wallet SMS, not a real message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class SmsMessageLookupTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `returns the stored message by id`() = runBlocking {
        db.smsMessageDao().insertAll(
            listOf(
                SmsMessageEntity(
                    id = "sms-1",
                    address = "VM-IRSMSa-G",
                    body = "Rs.315.00 debited from your RWallet Account on Jul 22 2026 6:19AM.",
                    receivedAt = 1_784_681_340_000L
                )
            )
        )

        val row = SmsMessageLookup(db.smsMessageDao()).byId("sms-1")

        assertEquals("VM-IRSMSa-G", row?.address)
        assertEquals(1_784_681_340_000L, row?.receivedAt)
    }

    @Test
    fun `an unknown id returns null`() = runBlocking {
        assertNull(SmsMessageLookup(db.smsMessageDao()).byId("missing"))
    }

    @Test
    fun `an unavailable database degrades to null`() = runBlocking {
        assertNull(SmsMessageLookup(null).byId("sms-1"))
    }
}
