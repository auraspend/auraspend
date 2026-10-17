package com.awbuilds.auraspend.data.ai

import android.content.Context
import android.util.Log
import com.awbuilds.auraspend.data.classification.AiSignalFusion
import com.awbuilds.auraspend.data.classification.ClassificationMemory
import com.awbuilds.auraspend.data.classification.ClassifiedSms
import com.awbuilds.auraspend.domain.model.TransactionType
import kotlinx.coroutines.CancellationException

/**
 * Enriches heuristic classification results, in order of trust:
 *
 *  1. **Learned memory** - when this merchant/note was categorized before, apply the remembered
 *     category instantly and skip the LLM entirely (fast path; also keeps results stable).
 *  2. **On-device LLM** - when the model is downloaded and the runtime is linked, fuse its verdict
 *     with the regex parse via [AiSignalFusion].
 *  3. **Regex only** - fall back to the heuristic result untouched.
 */
class SmsAiEnricher(
    context: Context,
    private val memory: ClassificationMemory? = null
) {

    /**
     * Resolved once at construction, so the model-download check and the
     * native-link check are frozen for the life of this batch. Null means "no
     * runtime", which the fusion layer treats as regex-only.
     */
    private val classifier: OnDeviceClassifier? = run {
        val runtime = LocalLlmProvider.get()
        if (ModelConstants.isDownloaded(context) && runtime !is UnavailableClassifier) {
            runtime
        } else {
            null
        }
    }

    suspend fun enrich(
        classified: ClassifiedSms,
        categoryIdByName: Map<String, String>
    ): ClassifiedSms {
        // 1. Memory fills the category/type, but it must not return early:
        //    recurring-keyword detection still has to run. A remembered
        //    Entertainment category used to suppress "AutoPay ... NETFLIX"
        //    from the Subscriptions screen for ever.
        val remembered = memory?.lookup(classified.parsed.merchant, classified.parsed.note)
        val base = if (remembered != null) {
            val rememberedType = remembered.type?.let { type ->
                runCatching { TransactionType.valueOf(type) }.getOrNull()
            }
            classified.parsed.copy(
                categoryId = remembered.categoryId,
                type = classified.parsed.type ?: rememberedType,
                confidence = maxOf(classified.parsed.confidence, MEMORY_CONFIDENCE)
            )
        } else {
            classified.parsed
        }

        // 2. LLM fusion when available; regex-only fusion otherwise. Both paths apply
        //    recurring-keyword / known-subscription-service detection. A memory
        //    hit deliberately skips the model call (the original fast-path
        //    promise) but still runs the fusion, so recurring detection is not
        //    switched off by a remembered category.
        val runtime = if (remembered == null) classifier else null
        val ai = if (runtime != null) {
            try {
                runtime.extract(classified.sms.body, categoryIdByName)
            } catch (e: CancellationException) {
                // Never swallow cancellation: the caller's coroutine scope owns it.
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "On-device categorisation crashed; using regex result", e)
                null
            }
        } else null

        val fused = AiSignalFusion.fuse(base, ai)
        // The user's remembered category wins over the fusion's
        // "subscription -> cat_subscription" rewrite, but the recurring flag is
        // kept. That is the difference between "this is how I file it" and
        // "stop telling me it recurs".
        val finalCategory = remembered?.categoryId ?: fused.parsed.categoryId
        return classified.copy(
            parsed = fused.parsed.copy(categoryId = finalCategory),
            isSubscription = fused.isSubscription || finalCategory == "cat_subscription"
        )
    }

    private companion object {
        const val TAG = "SmsAiEnricher"
        const val MEMORY_CONFIDENCE = 0.92f
    }
}
