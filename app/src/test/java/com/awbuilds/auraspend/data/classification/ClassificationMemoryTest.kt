package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.local.dao.ClassificationMemoryDao
import com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ClassificationMemoryTest {

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
    fun `a learned merchant is found through a location suffix`() = runBlocking {
        val dao = FakeDao()
        val memory = ClassificationMemory(dao)
        memory.learn(
            Transaction(
                amount = 250.0,
                categoryId = "cat_food",
                note = "Swiggy",
                merchant = "Swiggy",
                date = LocalDateTime.of(2026, 10, 17, 12, 0),
                type = TransactionType.EXPENSE
            ),
            ClassificationMemoryEntity.SOURCE_USER_SAVE
        )

        val hit = memory.lookup("SWIGGY BANGALORE", note = null)
        assertEquals("cat_food", hit?.categoryId)
    }
}
