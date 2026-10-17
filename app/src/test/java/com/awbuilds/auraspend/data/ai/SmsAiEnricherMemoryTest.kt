package com.awbuilds.auraspend.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.TestApplication
import com.awbuilds.auraspend.data.classification.ClassifiedSms
import com.awbuilds.auraspend.data.classification.ClassificationMemory
import com.awbuilds.auraspend.data.local.dao.ClassificationMemoryDao
import com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.classification.SmsInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A remembered category must not switch off recurring detection.
 *
 * The memory fast path used to return early and set `isSubscription` solely from
 * the remembered category. A user (or an earlier auto-save) filing Netflix under
 * Entertainment therefore stopped every future Netflix charge from being
 * detected as recurring, even though the message still says "AutoPay".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class SmsAiEnricherMemoryTest {

    private class FakeDao : ClassificationMemoryDao {
        val rows = mutableMapOf<String, ClassificationMemoryEntity>()
        override suspend fun getByKey(key: String) = rows[key]
        override suspend fun getAll() = rows.values.toList()
        override suspend fun upsert(entity: ClassificationMemoryEntity) {
            rows[entity.memoryKey] = entity
        }
        override suspend fun deleteByKey(key: String) {
            rows.remove(key)
        }
        override suspend fun clear() {
            rows.clear()
        }
    }

    @Test
    fun `memory fills the category but still detects the subscription`() = runBlocking {
        val dao = FakeDao().apply {
            rows["netflix"] = ClassificationMemoryEntity(
                memoryKey = "netflix",
                categoryId = "cat_entertainment",
                type = TransactionType.EXPENSE.name,
                source = ClassificationMemoryEntity.SOURCE_USER_SAVE
            )
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enricher = SmsAiEnricher(context, ClassificationMemory(dao))
        val parsed = ParsedBankMessage(
            amount = 499.0,
            type = TransactionType.EXPENSE,
            merchant = "NETFLIX",
            confidence = 0.7f,
            rawMessage = "AutoPay of Rs 499 NETFLIX e-mandate successful"
        )
        val classified = ClassifiedSms(
            sms = SmsInfo(id = "1", address = "HDFCBK", body = parsed.rawMessage, timestamp = 0L),
            parsed = parsed
        )

        val enriched = enricher.enrich(classified, emptyMap())

        assertEquals("cat_entertainment", enriched.parsed.categoryId)
        assertTrue("AutoPay must still mark the charge recurring", enriched.isSubscription)
    }
}
