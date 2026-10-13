package com.awbuilds.auraspend.data.classification

import android.content.Context
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.data.ai.SmsAiEnricher
import com.awbuilds.auraspend.data.local.dao.SmsMessageDao
import com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity

private const val TAG = "SmsPipeline"

/**
 * The one way production builds an [SmsPipelineProcessor].
 *
 * Both background entry points — the 6-hourly [AutoClassificationWorker] and the
 * in-process [BackgroundClassificationCoordinator] — used to construct the
 * processor themselves, and they had already drifted: the worker wired
 * `recordUnrecognized` to the real repository, the coordinator did not, so a bank
 * message that looked like money moving but that no parser could read was
 * recorded for the user on the worker path and silently marked SKIPPED on the
 * coordinator path. The coordinator is the common one — it runs whenever the app
 * is backgrounded with its process still alive — which made the "never silently
 * into the bin" invariant hold only on the rarer path.
 *
 * A factory makes that class of drift impossible: there is one configuration, and
 * a new entry point gets all of it by construction rather than by remembering to
 * copy it.
 */
internal fun buildSmsPipelineProcessor(
    context: Context,
    app: AuraSpendApp,
    dao: SmsMessageDao,
    interMessageDelayMs: Long
): SmsPipelineProcessor {
    val enricher = SmsAiEnricher(context, app.classificationMemory)
    return SmsPipelineProcessor(
        dao = dao,
        transactionRepository = app.transactionRepository,
        enrich = { classified, categories -> enricher.enrich(classified, categories) },
        onSaved = { transaction ->
            // Learning is best-effort: failing to record a mapping must never
            // turn a successful save into a pipeline failure.
            runCatching {
                app.classificationMemory.learn(
                    transaction,
                    ClassificationMemoryEntity.SOURCE_AUTO_SAVE
                )
            }.onFailure {
                AuraLog.w(TAG, "Failed to learn from a saved transaction", it)
            }
        },
        // A bank message that looks like money moving but that no parser could
        // read is surfaced to the user instead of vanishing. Best-effort: a
        // failure here must not stop the batch, so the row is simply not recorded
        // and the message is skipped as before.
        recordUnrecognized = { id: String, sender: String, body: String, receivedAt: Long ->
            runCatching {
                app.unrecognizedSms.record(id, sender, body, receivedAt)
            }.onFailure {
                AuraLog.w(TAG, "Failed to record an unrecognized SMS", it)
            }
            Unit
        },
        interMessageDelayMs = interMessageDelayMs
    )
}
