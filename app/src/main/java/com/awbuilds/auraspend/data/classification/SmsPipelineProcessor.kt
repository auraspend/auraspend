package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.data.local.dao.SmsMessageDao
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.classification.SmsInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield

/**
 * Result of a single pipeline run.
 */
data class SmsProcessResult(
    val saved: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val attempted: Int = 0
)

/**
 * Persistent-queue classifier. The worker feeds [SmsMessageEntity] rows in one at a time
 * (idempotent ingestion keyed on the provider `_id`), and this processor advances each row to a
 * terminal state:
 *
 *  - [SmsMessageStatus.SAVED]  - a transaction was written (unique `sourceSmsId` prevents dupes)
 *  - [SmsMessageStatus.SKIPPED] - not a transaction (OTP/alert, or no amount/type could be parsed)
 *  - [SmsMessageStatus.FAILED]  - save errored and retry attempts were exhausted
 *  - stays [SmsMessageStatus.NEW] - transient error, retried next run (attempts incremented)
 *
 * The message body is never modified; every row is kept in the queue so the UI can show live
 * status and the user can re-save skipped messages manually from Smart Add.
 *
 * Live status is the queue itself: a row being classified right now is
 * [SmsMessageStatus.CLASSIFYING], which the UI renders per-message and which is swept back to
 * [SmsMessageStatus.NEW] at the start of the next pass if a pass was killed mid-row.
 */
class SmsPipelineProcessor(
    private val dao: SmsMessageDao,
    private val transactionRepository: TransactionRepository,
    private val enrich: suspend (ClassifiedSms, Map<String, String>) -> ClassifiedSms = { c, _ -> c },
    private val categoryResolver: (String?) -> String? = { merchant ->
        merchant?.let { getCategoryIdForKeyword(it) }
    },
    /** Called after a successful save so the classification memory can learn the mapping. */
    private val onSaved: suspend (transaction: com.awbuilds.auraspend.domain.model.Transaction) -> Unit = {},
    /**
     * Records a bank message that looks like money moving but that no parser
     * could read. Defaults to a no-op so existing construction sites and the
     * pipeline's own tests are unaffected; production wires the real DAO.
     */
    private val recordUnrecognized: suspend (
        id: String, sender: String, body: String, receivedAt: Long
    ) -> Unit = { _, _, _, _ -> },
    private val maxAttempts: Int = 3,
    /**
     * Pause between messages so a long background batch never saturates the CPU
     * for minutes straight — the phone stays responsive while the queue drains.
     */
    private val interMessageDelayMs: Long = 0L
) {

    private companion object {
        const val TAG = "SmsPipeline"

        /** Bodies shorter than this are too generic for exact-content matching. */
        const val MIN_BODY_FOR_EXACT_MATCH = 20

        /** How far back (and slightly forward) of the candidate's own date to look. */
        const val DUPLICATE_WINDOW_MS = 72L * 60 * 60 * 1000
        const val DUPLICATE_WINDOW_AFTER_MS = 6L * 60 * 60 * 1000

        /** Same amount + same type within this many minutes = same event, two senders. */
        const val FINGERPRINT_WINDOW_MINUTES = 120L

        /**
         * High precision on purpose: auto-skipping a real payment is worse than letting a
         * rare duplicate through.
         */
        const val DUPLICATE_SIMILARITY_THRESHOLD = 0.96f
    }

    /**
     * Layer 1 — identical normalized body already produced a SAVED transaction.
     * Layer 2 — fingerprint: same amount + same type within ±2h is the same real-world
     *   event announced twice (bank SMS + biller SMS), unless both sides carry clearly
     *   DIFFERENT merchants (two distinct equal-amount payments).
     * Layer 3 — near-identical transaction per [DuplicateDetector].
     *
     * The comparison window is centred on the CANDIDATE'S OWN DATE, because historical
     * messages are often processed weeks after they arrived. Skipped rows stay in the
     * queue, so a wrongly-caught real payment remains re-saveable from Smart Add.
     */
    private suspend fun isSemanticDuplicate(
        message: SmsMessageEntity,
        transaction: com.awbuilds.auraspend.domain.model.Transaction
    ): Boolean {
        val normalized = normalizeBody(message.body)
        if (normalized.length >= MIN_BODY_FOR_EXACT_MATCH) {
            val alreadySaved = dao.observeAll().first().any {
                it.status == SmsMessageStatus.SAVED.name &&
                    it.id != message.id &&
                    normalizeBody(it.body) == normalized
            }
            if (alreadySaved) return true
        }

        val txnMillis = transaction.date
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recent = transactionRepository.getTransactionsInRange(
            txnMillis - DUPLICATE_WINDOW_MS, txnMillis + DUPLICATE_WINDOW_AFTER_MS
        ).first()

        val fingerprintDuplicate = recent.any { other ->
            val minutesApart = kotlin.math.abs(
                java.time.Duration.between(other.date, transaction.date).toMinutes()
            )
            other.type == transaction.type &&
                kotlin.math.abs(other.amount - transaction.amount) < 0.01 &&
                minutesApart <= FINGERPRINT_WINDOW_MINUTES &&
                merchantsCompatible(other.merchant, transaction.merchant)
        }
        if (fingerprintDuplicate) return true

        val sameTypeRecent = recent
            .filter { it.type == transaction.type }
            // Near-identical means the SAME payment re-delivered, and SMS amounts are
            // deterministic: a few-percent gap is a different payment. Without this
            // gate the similarity score's merchantless case (40% free when both
            // merchants are null) + a same-hour date could push a 6%-apart pair over
            // the 0.96 threshold. Measured on the owner's device (2026-10-13): an
            // ₹735.50 payment was skipped as a "duplicate" of an unrelated ₹815.75
            // credit 37 minutes earlier, because its date had failed to parse and
            // collapsed onto the save hour. The date-parse fix removes that trigger;
            // this gate makes the class impossible.
            .filter {
                kotlin.math.abs(it.amount - transaction.amount) <=
                    maxOf(1.0, 0.002 * transaction.amount)
            }
        return DuplicateDetector.findMostLikelyDuplicate(
            transaction,
            sameTypeRecent,
            threshold = DUPLICATE_SIMILARITY_THRESHOLD
        ) != null
    }

    /** Two payments are indistinguishable when at least one side has no merchant name. */
    private fun merchantsCompatible(a: String?, b: String?): Boolean = when {
        a.isNullOrBlank() || b.isNullOrBlank() -> true
        a.trim().equals(b.trim(), ignoreCase = true) -> true
        else -> DuplicateDetector.textSimilarity(a, b) >= 0.5f
    }

    private fun normalizeBody(body: String): String =
        body.replace(Regex("\\s+"), " ").trim().lowercase()

    private enum class Outcome { SAVED, SKIPPED, FAILED, RETRY }

    /** Process at most [maxMessages] pending messages, one at a time, newest first. */
    suspend fun processPending(maxMessages: Int = 10): SmsProcessResult {
        var saved = 0
        var skipped = 0
        var failed = 0
        var attempted = 0

        // Crash recovery: a row left CLASSIFYING by a killed pass is invisible to
        // `getPending(NEW, …)`, so without this sweep the message would be stranded
        // forever. PROCESSED rows are untouched — they are parked by the user.
        dao.sweepInFlight(System.currentTimeMillis())

        val categories = transactionRepository.getAllCategories().first()
            .associate { it.id to it.name }

        repeat(maxMessages) { index ->
            val message = dao.getPending(SmsMessageStatus.NEW.name, 1).firstOrNull()
                ?: return SmsProcessResult(saved, skipped, failed, attempted)
            if (index > 0 && interMessageDelayMs > 0L) delay(interMessageDelayMs)
            attempted++
            when (processOne(message, categories)) {
                Outcome.SAVED -> saved++
                Outcome.SKIPPED -> skipped++
                Outcome.FAILED -> failed++
                Outcome.RETRY -> {} // row left NEW for the next run
            }
        }
        return SmsProcessResult(saved, skipped, failed, attempted)
    }

    private suspend fun processOne(
        message: SmsMessageEntity,
        categories: Map<String, String>
    ): Outcome {
        yield()
        val now = System.currentTimeMillis()

        if (SmsAutoClassifier.isOtpOrAlertMessage(message.body)) {
            dao.update(message.copy(
                status = SmsMessageStatus.SKIPPED.name,
                updatedAt = now
            ))
            return Outcome.SKIPPED
        }

        // Claim the row before the expensive work so the UI can show "categorising…" on
        // exactly this message while it is being classified, and so a pass killed mid-row
        // leaves a marker the next pass sweeps (rather than a silent NEW row nobody owns).
        dao.update(message.copy(
            status = SmsMessageStatus.CLASSIFYING.name,
            updatedAt = now
        ))

        val classified = ClassifiedSms(
            sms = SmsInfo(
                id = message.id,
                address = message.address,
                body = message.body,
                timestamp = message.receivedAt
            ),
            // Sender-routed: the address travels with the queued row precisely so
            // the per-bank parser can be chosen before the body is read.
            parsed = TransactionClassifier.classify(message.body, message.address)
        )
        val enriched = enrich(classified, categories)

        if (enriched.parsed.amount == null || enriched.parsed.type == null) {
            // No amount or no type means nothing saveable. If the message still
            // *looks* like money moving, it is recorded for the user rather than
            // dropped: a missing transaction with no explanation is the failure
            // mode this table exists to prevent. Messages that merely mention a
            // bank have already been filtered by isOtpOrAlertMessage above.
            // Shared with the OTP/alert rescue in `SmsAutoClassifier`: one
            // predicate, so the two can never disagree about what money moving
            // looks like. The copy that used to live here knew six debit verbs
            // and no credit verb, which is how a deposit or refund that no parser
            // claimed got dropped instead of being surfaced to the user.
            if (SmsAutoClassifier.looksLikeTransaction(message.body)) {
                recordUnrecognized(
                    message.id,
                    message.address,
                    message.body,
                    message.receivedAt
                )
            }
            dao.update(message.copy(
                status = SmsMessageStatus.SKIPPED.name,
                updatedAt = now
            ))
            return Outcome.SKIPPED
        }

        val categoryId = enriched.parsed.categoryId
            ?: categoryResolver(enriched.parsed.merchant)
        val transaction = SmsAutoClassifier.toTransaction(
            enriched,
            categoryId,
            receivedAt = message.receivedAt
        )

        // Semantic-duplicate guard: banks/wallets often deliver the SAME payment as several
        // distinct SMS (provider retries, app + bank notifications). The sourceSmsId unique
        // index only stops the identical message id — this catches identical content and
        // near-identical transactions before they inflate totals.
        if (isSemanticDuplicate(message, transaction)) {
            dao.update(message.copy(
                status = SmsMessageStatus.SKIPPED.name,
                updatedAt = now
            ))
            return Outcome.SKIPPED
        }

        return try {
            transactionRepository.saveTransaction(transaction)
            onSaved(transaction)
            dao.update(message.copy(
                status = SmsMessageStatus.SAVED.name,
                amount = transaction.amount,
                type = transaction.type.name,
                merchant = transaction.merchant,
                categoryId = transaction.categoryId,
                isSubscription = transaction.isRecurring,
                confidence = enriched.parsed.confidence,
                updatedAt = now
            ))
            Outcome.SAVED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A duplicate `sourceSmsId` is no longer an error here: the transaction
            // DAO inserts with `OnConflictStrategy.IGNORE`, so a second save of the
            // same SMS-linked row is a no-op that leaves the user's own edits in
            // place. The constraint-exception branch that used to live here was
            // unreachable — the old `REPLACE` strategy resolved the conflict by
            // deleting the existing row instead of throwing.
            AuraLog.w(TAG, "Failed to save a classified message; will retry", e)
            if (message.attempts + 1 >= maxAttempts) {
                dao.update(message.copy(
                    status = SmsMessageStatus.FAILED.name,
                    attempts = message.attempts + 1,
                    updatedAt = now
                ))
                Outcome.FAILED
            } else {
                dao.update(message.copy(
                    status = SmsMessageStatus.NEW.name,
                    attempts = message.attempts + 1,
                    updatedAt = now
                ))
                Outcome.RETRY
            }
        }
    }
}
