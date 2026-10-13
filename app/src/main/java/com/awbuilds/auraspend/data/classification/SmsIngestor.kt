package com.awbuilds.auraspend.data.classification

import android.content.Context
import com.awbuilds.auraspend.data.local.dao.SmsMessageDao
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.data.privacy.SensitiveDataMasker
import com.awbuilds.auraspend.ui.classification.SmsInfo

/**
 * Snapshots bank-like messages from the device inbox into the persistent [SmsMessageEntity] queue.
 *
 * Ingestion is idempotent (the queue is keyed on the provider's `_id`, inserted with `OR IGNORE`),
 * so re-scanning is always safe.
 *
 * Two scans run, in this order, and the split is the whole design:
 *
 *  1. **New arrivals** — everything above the high-water mark, oldest-first so a
 *     burst larger than one page still makes progress. Cheap in steady state,
 *     and always first, because these are the messages the user cares about now.
 *  2. **History** — one page per call, walking downwards from the high-water
 *     mark to the bottom of the inbox, and **only when the live queue is empty**.
 *     The gate is what keeps a backfill from growing the queue faster than it is
 *     classified: history is filled in during idle time, never ahead of the
 *     message that just arrived.
 *
 * The cursor and the reason it needs three values (rather than the single
 * watermark it used to have, which permanently stranded every message older than
 * the newest 500) are documented on [SmsIngestCursor].
 *
 * PRIVACY: bodies are passed through [SensitiveDataMasker] BEFORE storage, so phone numbers,
 * reference ids and account fragments never touch the database (or anything derived from it).
 *
 * NOISE: OTP / alert / promo messages are dropped at [isIngestable] rather than at the
 * processor. They were never saveable, but queueing them cost three things: they inflated the
 * progress denominator ("categorising 34 of 900" when two thirds were OTPs), they occupied the
 * SMS tab's message list, and each one burned a pipeline slot. The processor keeps the same
 * check as defence in depth — one predicate, shared with [SmsAutoClassifier], so the ingest
 * filter and the classifier can never disagree about what money moving looks like.
 */
object SmsIngestor {

    private const val PREF_FILE = "auraspend_prefs"
    private const val PREF_INGEST_SINCE = "sms_ingest_since"
    private const val PREF_HISTORY_BEFORE = "sms_ingest_history_before"
    private const val PREF_HISTORY_WALK_DONE = "sms_ingest_history_walk_done"

    /** Returns how many new messages were enqueued. */
    suspend fun ingest(
        context: Context,
        dao: SmsMessageDao,
        maxMessages: Int = 500
    ): Int {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val stored = SmsIngestCursor(
            newestIngested = prefs.getLong(PREF_INGEST_SINCE, 0L),
            historyBefore = prefs.getLong(PREF_HISTORY_BEFORE, 0L),
            historyWalkDone = prefs.getBoolean(PREF_HISTORY_WALK_DONE, false)
        )

        var cursor = stored
        var enqueued = 0

        // 1. New arrivals (or the newest page on a cold start).
        val live = SmsIngestPlanner.liveScan(cursor)
        val liveBatch = scan(context, live, maxMessages)
        enqueued += enqueue(dao, liveBatch)
        cursor = SmsIngestPlanner.afterLiveScan(cursor, live, liveBatch, maxMessages)

        // 2. One history page, and only once the live queue has drained.
        //
        // Two steps on purpose: `countByStatus` scans the queue, and once the walk
        // has finished — most installs, most of the time — there is nothing to gate,
        // so the count is not worth running on every pass.
        val history = SmsIngestPlanner.historyScan(cursor)?.let {
            SmsIngestPlanner.historyScanWhenIdle(
                cursor = cursor,
                pendingMessages = dao.countByStatus(SmsMessageStatus.NEW.name)
            )
        }
        if (history != null) {
            val batch = scan(context, history, maxMessages)
            enqueued += enqueue(dao, batch)
            cursor = SmsIngestPlanner.afterHistoryScan(cursor, batch, maxMessages)
        }

        prefs.edit()
            .putLong(PREF_INGEST_SINCE, cursor.newestIngested)
            .putLong(PREF_HISTORY_BEFORE, cursor.historyBefore)
            .putBoolean(PREF_HISTORY_WALK_DONE, cursor.historyWalkDone)
            .apply()

        return enqueued
    }

    /**
     * True while the inbox history walk still has pages left to pull.
     *
     * Read by both background drivers so the progress notification can tell "nothing to do
     * right now, more coming" (an indeterminate bar) apart from "the backlog is finished"
     * (a summary). Absent pref means done — a fresh install has not walked anything yet, and
     * its first pass discovers that within seconds.
     */
    fun hasMoreHistory(context: Context): Boolean =
        !context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(PREF_HISTORY_WALK_DONE, true)

    private fun scan(context: Context, scan: InboxScan, maxMessages: Int): List<SmsInfo> =
        SmsAutoClassifier.queryBankSms(
            context = context,
            sinceTimestamp = scan.sinceTimestamp,
            maxMessages = maxMessages,
            beforeTimestamp = scan.beforeTimestamp,
            oldestFirst = scan.oldestFirst
        )

    private suspend fun enqueue(dao: SmsMessageDao, messages: List<SmsInfo>): Int {
        val ingestable = messages.filter { isIngestable(it) }
        if (ingestable.isEmpty()) return 0
        dao.insertAll(ingestable.map { message ->
            SmsMessageEntity(
                id = message.id,
                address = message.address,
                body = SensitiveDataMasker.mask(message.body),
                receivedAt = message.timestamp,
                status = SmsMessageStatus.NEW.name
            )
        })
        return ingestable.size
    }
}

/**
 * True when a scanned message is worth queueing.
 *
 * The negation of [SmsAutoClassifier.isOtpOrAlertMessage] — the same predicate the
 * processor applies, including its amount+movement-verb rescue, so a genuine debit that
 * happens to end in an OTP is still ingested. It is a pure function on purpose: the
 * filter is a rule worth pinning in a unit test without a ContentResolver.
 */
internal fun isIngestable(sms: SmsInfo): Boolean =
    !SmsAutoClassifier.isOtpOrAlertMessage(sms.body)
