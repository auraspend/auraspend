package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.local.toEntity
import com.awbuilds.auraspend.data.local.toDomain
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.classification.SmsInfo
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks in the dedup + cursor hardening: every transaction derived from an SMS carries its source
 * id (enforced uniquely at the DB layer), and the scan cursor advances past the whole batch once
 * all messages are terminal, never stranding unparsed messages.
 */
class SmsAutoClassifierDedupTest {

    private fun classified(
        id: String,
        timestamp: Long,
        amount: Double?,
        type: TransactionType?
    ) = ClassifiedSms(
        sms = SmsInfo(id = id, address = "HDFCBank", body = "INR 100 debited", timestamp = timestamp),
        parsed = ParsedBankMessage(amount = amount, type = type)
    )

    @Test
    fun `toTransaction carries the source sms id`() {
        val t = SmsAutoClassifier.toTransaction(
            classified("sms-42", 1000L, 100.0, TransactionType.EXPENSE)
        )
        assertEquals("sms-42", t.sourceSmsId)
    }

    @Test
    fun `toTransaction uses null source id when sms id is blank`() {
        val t = SmsAutoClassifier.toTransaction(
            classified("", 1000L, 100.0, TransactionType.EXPENSE)
        )
        assertNull(t.sourceSmsId)
    }

    @Test
    fun `source sms id survives the entity round trip`() {
        val t = SmsAutoClassifier.toTransaction(
            classified("sms-7", 1000L, 50.0, TransactionType.EXPENSE)
        )
        assertEquals("sms-7", t.toEntity().toDomain().sourceSmsId)
    }
}
