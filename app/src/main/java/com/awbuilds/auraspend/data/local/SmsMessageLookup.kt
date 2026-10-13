package com.awbuilds.auraspend.data.local

import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.data.local.dao.SmsMessageDao
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity

/**
 * Read-only lookup of the queue row behind an SMS-detected transaction, so the
 * detail page can show the original message.
 *
 * Lookup failures degrade to `null` — the detail page drops the message card
 * rather than losing the transaction. A null [dao] covers the defensive case
 * where the application container is unavailable.
 */
class SmsMessageLookup(private val dao: SmsMessageDao?) {

    suspend fun byId(id: String): SmsMessageEntity? =
        boundaryOrNull(TAG) { dao?.getById(id) }

    private companion object {
        const val TAG = "SmsMessageLookup"
    }
}
