package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue-row mapping the SMS inbox depends on, with the enum safety pinned.
 *
 * This mapping used to live inside `ClassificationViewModel` as
 * `TransactionType.valueOf(type!!)` / `SmsMessageStatus.valueOf(status)` inside a
 * Room `Flow.map` with no `catch` anywhere above it — so one row carrying a string
 * the current enum does not know (a Drive restore repopulates these columns
 * wholesale, so they are backup-controlled input) threw inside the collector and
 * the inbox froze on its last snapshot, silently, with no error and no retry. It
 * is the same bug handoff 0015 fixed in `Mappers.kt`, where one bad string
 * blanked the Dashboard.
 */
class SmsQueueRowMapperTest {

    private fun row(
        id: String = "sms-1",
        status: String = SmsMessageStatus.NEW.name,
        amount: Double? = 250.0,
        type: String? = TransactionType.EXPENSE.name,
        body: String = "INR 250 debited at SWIGGY"
    ) = SmsMessageEntity(
        id = id,
        address = "AXISBK",
        body = body,
        receivedAt = 1_780_000_000_000L,
        status = status,
        amount = amount,
        type = type,
        merchant = "SWIGGY",
        categoryId = "cat_food"
    )

    @Test
    fun `a stored parse is used verbatim`() {
        val classified = row().toClassifiedSms()

        assertEquals(250.0, classified.parsed.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, classified.parsed.type)
        assertEquals("SWIGGY", classified.parsed.merchant)
        assertEquals("cat_food", classified.parsed.categoryId)
        assertEquals(SmsStatus.PENDING, classified.status)
        assertFalse(classified.isSaved)
        assertFalse(classified.isDismissed)
    }

    @Test
    fun `an unknown status does not throw and does not hide the message`() {
        // Restored from a backup written by a build whose enum had a value this one
        // does not. The row must stay visible: hiding it would be silent money loss,
        // and the body may hold a real debit.
        val classified = row(status = "ARCHIVED").toClassifiedSms()

        assertEquals(SmsStatus.PENDING, classified.status)
        assertFalse("an unreadable status is not 'saved'", classified.isSaved)
        assertFalse("and not 'dismissed' either", classified.isDismissed)
        assertTrue("so it stays in the triage inbox", pendingInbox(listOf(classified)).isNotEmpty())
    }

    @Test
    fun `an unknown type falls back to re-parsing the body`() {
        val classified = row(type = "TRANSFER").toClassifiedSms()

        // Re-derived from the body rather than rendered or dropped: the parser reads
        // a real debit out of it.
        assertEquals(250.0, classified.parsed.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, classified.parsed.type)
    }

    @Test
    fun `every stored status maps to its UI status`() {
        assertEquals(SmsStatus.PENDING, row(status = "NEW").toClassifiedSms().status)
        assertEquals(SmsStatus.CLASSIFYING, row(status = "PROCESSED").toClassifiedSms().status)
        assertEquals(SmsStatus.SAVED, row(status = "SAVED").toClassifiedSms().status)
        assertEquals(SmsStatus.SKIPPED, row(status = "SKIPPED").toClassifiedSms().status)
        assertEquals(SmsStatus.FAILED, row(status = "FAILED").toClassifiedSms().status)
    }

    @Test
    fun `handled rows leave the inbox and unhandled rows stay in it`() {
        val saved = row(id = "s", status = "SAVED").toClassifiedSms()
        val skipped = row(id = "k", status = "SKIPPED").toClassifiedSms()
        val pending = row(id = "p", status = "NEW").toClassifiedSms()

        assertTrue(saved.isSaved)
        assertTrue(skipped.isDismissed)
        assertEquals(listOf("p"), pendingInbox(listOf(saved, skipped, pending)).map { it.sms.id })
    }

    @Test
    fun `a row with no stored parse is parsed from the body`() {
        val classified = row(amount = null, type = null).toClassifiedSms()

        assertEquals(250.0, classified.parsed.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, classified.parsed.type)
    }

    @Test
    fun `an unparseable row keeps its body and reports no amount`() {
        val classified = row(
            amount = null,
            type = null,
            body = "Happy birthday! See you soon."
        ).toClassifiedSms()

        assertNull(classified.parsed.amount)
        assertEquals("Happy birthday! See you soon.", classified.sms.body)
    }
}
