package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.local.dao.ClassificationMemoryDao
import com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity
import com.awbuilds.auraspend.domain.model.Transaction

/**
 * Derives stable lookup keys from the noisy text found in bank messages.
 *
 * Merchant strings vary a lot between banks ("SWIGGY*Zomato", "Swiggy", "SWIGGY BANGALORE"), so
 * keys are aggressively normalized: lowercased, punctuation stripped, digits and long numeric /
 * alphanumeric tokens (order ids, card refs, UPI refs) removed, whitespace collapsed.
 */
object MemoryKeys {

    /** Tokens that carry no identity (bank boilerplate, generic words, TLD fragments). */
    internal val stopWords = setOf(
        "limited", "ltd", "pvt", "india", "the", "and", "for",
        "upi", "neft", "imps", "rtgs", "pos", "purchase", "payment", "paid",
        "debit", "credit", "debited", "credited", "card", "acct", "account",
        "com", "org", "net", "info", "www"
    )

    /**
     * Normalizes [raw] into a memory key, or null when nothing usable remains
     * (e.g. only numbers/refs were present).
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var text = raw.lowercase()

        // Drop everything from the first reference-noise separator onwards.
        for (sep in listOf("*", "@", "#", "|")) {
            val idx = text.indexOf(sep)
            if (idx > 0) text = text.substring(0, idx)
        }

        // Keep letters, spaces and '&'; drop digits/punctuation noise.
        text = text.replace(Regex("[^a-z& ]+"), " ")

        val key = text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .filter { it.length >= 3 || it == "&" }
            .filter { it !in stopWords }
            .joinToString(" ")
            .trim()

        return key.ifBlank { null }?.take(64)
    }

    /** Best-effort key for a parsed message: merchant first, then note. */
    fun fromParsed(merchant: String?, note: String?): String? =
        normalize(merchant) ?: normalize(note)

    /**
     * Lookup keys from most to least specific. Banks append a branch, city or
     * product suffix to the same merchant ("SWIGGY", "SWIGGY BANGALORE"), so the
     * full key alone made one correction fail to transfer to the next message.
     * The first token is a deliberately simple generalisation; it is only tried
     * after the full key misses.
     */
    fun candidates(raw: String?): List<String> {
        val full = normalize(raw) ?: return emptyList()
        val first = full.substringBefore(' ').takeIf { it.length >= 3 && it != full }
        return listOfNotNull(full, first).distinct()
    }

    /** Merchant candidates first, then note candidates. */
    fun fromParsedCandidates(merchant: String?, note: String?): List<String> =
        (candidates(merchant) + candidates(note)).distinct()
}

/**
 * Read/write access to learned classifications. Purely suspend-based so callers decide on which
 * dispatcher to run; safe to share app-wide.
 */
class ClassificationMemory(private val dao: ClassificationMemoryDao) {

    /** Looks up the remembered category/type for a parsed message, if any. */
    suspend fun lookup(merchant: String?, note: String?): ClassificationMemoryEntity? {
        for (key in MemoryKeys.fromParsedCandidates(merchant, note)) {
            dao.getByKey(key)?.let { return it }
        }
        return null
    }

    /**
     * Records (or strengthens) the mapping for [transaction]. User saves always overwrite;
     * auto-saves only bump confidence so a user correction is never silently reverted.
     */
    suspend fun learn(transaction: Transaction, source: String) {
        val key = MemoryKeys.fromParsed(transaction.merchant, transaction.note) ?: return
        if (key.length < MIN_KEY_LENGTH) return

        val existing = dao.getByKey(key)
        val entity = when {
            existing == null -> ClassificationMemoryEntity(
                memoryKey = key,
                categoryId = transaction.categoryId,
                type = transaction.type.name,
                source = source,
                hits = 1
            )
            source == ClassificationMemoryEntity.SOURCE_USER_SAVE ->
                existing.copy(
                    categoryId = transaction.categoryId,
                    type = transaction.type.name,
                    source = source,
                    hits = existing.hits + 1,
                    updatedAt = System.currentTimeMillis()
                )
            else -> existing.copy(
                hits = existing.hits + 1,
                updatedAt = System.currentTimeMillis()
            )
        }
        dao.upsert(entity)
    }

    companion object {
        private const val MIN_KEY_LENGTH = 3
    }
}
