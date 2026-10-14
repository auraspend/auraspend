package com.awbuilds.auraspend.ui.recurring

import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDateTime

/**
 * One recurring-payment suggestion, grouped from the transactions the pipeline
 * already flagged as recurring (`isRecurring = 1`).
 */
internal data class DetectedSubscription(
    val name: String,
    val amount: Double,
    val lastPaid: LocalDateTime,
    val categoryId: String?,
    val occurrences: Int,
)

/**
 * Names that are parser artefacts, not services — the word "merchant" leaked
 * out of a mandate message, "payment" out of a bill template. A suggestion must
 * read like something the user recognises.
 */
private val artifactNames = setOf(
    "merchant", "payment", "transaction", "upi", "bank", "account", "bill", "transfer",
)

/**
 * Groups flagged recurring transactions into one suggestion per service.
 *
 * - Keyed case-insensitively on the merchant; the newest transaction supplies
 *   the display name, amount, category and date.
 * - Rows without a merchant are skipped: their body has no usable name, and a
 *   suggestion titled after a message paragraph is noise, not a service.
 * - Names that are parser artefacts or carry fewer than three letters are
 *   skipped (`##` from a masking pass, "merchant" from a template).
 * - Only expenses suggest subscriptions; a flagged credit is not an outflow.
 * - Suggestions already covered by a tracked subscription ([trackedNames]) or
 *   explicitly dismissed ([dismissedNames]) are dropped, matched loosely both
 *   ways so "Netflix" silences "NETFLIX COM" — the section cannot nag about
 *   something just tracked, nor about something the user rejected.
 * - Newest first.
 */
internal fun detectedSubscriptions(
    transactions: List<Transaction>,
    trackedNames: Collection<String> = emptyList(),
    dismissedNames: Collection<String> = emptyList()
): List<DetectedSubscription> {
    val suppressed = (trackedNames + dismissedNames)
        .map { normalizedSuggestionName(it) }
        .filter { it.isNotEmpty() }
    return transactions
        .asSequence()
        // Self-contained: only the flagged rows, whatever the caller passes.
        .filter { it.isRecurring }
        .filter { it.type == TransactionType.EXPENSE }
        .mapNotNull { txn ->
            val merchant = txn.merchant?.trim().orEmpty()
            if (merchant.isEmpty()) return@mapNotNull null
            if (merchant.count { it.isLetter() } < 3) return@mapNotNull null
            if (merchant.lowercase() in artifactNames) return@mapNotNull null
            val key = normalizedSuggestionName(merchant)
            if (key.isEmpty()) return@mapNotNull null
            if (suppressed.any { t -> looselyMatches(t, key) }) return@mapNotNull null
            key to txn
        }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (_, txns) ->
            val latest = txns.maxByOrNull { it.date } ?: return@mapNotNull null
            DetectedSubscription(
                name = latest.merchant?.trim().orEmpty(),
                amount = latest.amount,
                lastPaid = latest.date,
                categoryId = latest.categoryId,
                occurrences = txns.size,
            )
        }
        .sortedByDescending { it.lastPaid }
}

/**
 * True when a tracked name and a detected name refer to the same service.
 * "netflix" silences "netflix com" both ways; containment only counts when the
 * shorter side is long enough to be meaningful.
 */
private fun looselyMatches(a: String, b: String): Boolean {
    if (a == b) return true
    if (minOf(a.length, b.length) < 4) return false
    return a in b || b in a
}

/** Letters and digits only, single-spaced, lowercased — for loose matching. */
internal fun normalizedSuggestionName(raw: String): String =
    raw.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
