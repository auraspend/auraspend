package com.awbuilds.auraspend.data.ai

import com.awbuilds.auraspend.domain.model.TransactionType

/**
 * One structured result from the on-device classifier.
 *
 * `confidence` is nullable on purpose. Some runtimes return a *calibrated* score
 * for exactly this decision; others (a plain GGUF behind llama.cpp) have no such
 * head and would only be able to fabricate a number. Null means "this runtime
 * cannot say", which is honest and lets callers treat a calibrated score as the
 * stronger signal it actually is.
 */
data class SmsExtraction(
    val isTransaction: Boolean,
    val type: TransactionType?,
    /** Raw category name/id as produced by the runtime; resolved by the fusion layer. */
    val category: String?,
    val merchant: String?,
    val isSubscription: Boolean,
    /**
     * Verbatim model output, kept for debugging a bad classification. Only a
     * text-generating runtime (llama.cpp) has this; a grammar-constrained one
     * returns null.
     */
    val rawModelOutput: String? = null,
    val confidence: Float? = null,
    /**
     * How sure the runtime is that [isTransaction] is right, in `[0, 1]`.
     *
     * Null means "this runtime cannot say" and is treated as *no opinion* — the
     * fusion layer then falls back to the regex verdict. This is distinct from
     * [confidence], which scores the extraction as a whole.
     *
     * A bare boolean cannot express doubt, and that turned out to matter more
     * than the extraction's quality. `AiSignalFusion` treats a model "not a
     * transaction" verdict as authoritative: it nulls `amount` and `type`, the
     * pipeline's unresolved-fields gate then drops the row, and the transaction
     * is deleted rather than mis-filed. Measured on the golden corpus, a model
     * answered "not a transaction" for 7 of 46 real transactions (Qwen2.5-0.5B)
     * and 40 of 46 (SmolLM2-135M) — destroying 7 and 40 respectively to rescue
     * 1 and 0. A model that is 0.4 sure must not be able to do that; a model
     * that is 0.99 sure still may not, because the regex layer reading the
     * message correctly is the stronger signal.
     *
     * **This scores the answer the runtime actually gave, not one class.** A
     * runtime that reports the raw probability of the transaction class here
     * makes `AiSignalFusion.mayDiscard` unreachable on a negative verdict, since
     * that probability is below 0.5 by construction whenever the verdict is
     * "not a transaction" — the 0.9 gate then never fires and the safety
     * property is measured by dead code. Use [verdictConfidence] to convert.
     *
     * llama.cpp cannot supply this — a GGUF generation has no calibrated head —
     * so a generative runtime reports null and keeps the legacy unconditional
     * veto. The shipped encoder has a softmax head and fills it honestly.
     */
    val isTransactionProbability: Float? = null
) {
    companion object {
        /**
         * Confidence in an emitted verdict, from [pTransaction] — a runtime's raw
         * probability that the message *is* a transaction.
         *
         * `pTransaction` above 0.5 means the verdict was "transaction", and the
         * confidence in that verdict is `pTransaction` itself. Below 0.5 the
         * verdict was "not a transaction", and the confidence in *that* is
         * `1 - pTransaction`. Reporting the raw value instead is what made the
         * discard gate unreachable: inside the `!isTransaction` branch it can
         * never reach 0.9.
         */
        fun verdictConfidence(isTransaction: Boolean, pTransaction: Float): Float =
            if (isTransaction) pTransaction else 1f - pTransaction
    }
}

/**
 * The app's on-device AI contract, described as a *job* rather than a mechanism.
 *
 * The previous seam was a `generate(prompt): String?` — prompt in, text
 * out, blocking, with no schema and no confidence. That shape is dictated by
 * llama.cpp and fits nothing else: a grammar-constrained runtime has no meaningful
 * "prompt", and its two main advantages — output that is structurally guaranteed
 * to parse, and a calibrated score — cannot cross a text-shaped interface at all.
 *
 * This interface is the minimum both runtime families can honour honestly, and it
 * is what the eval harness is written against.
 */
interface OnDeviceClassifier {

    /** Stable identifier used in logs and in eval reports, e.g. `llama.cpp`. */
    val id: String

    /**
     * True when this runtime can actually classify right now.
     *
     * Every runtime's pieces — the native session, the vocabulary, the centroid
     * asset — load through a boundary that degrades to null instead of throwing,
     * which is what keeps a broken runtime from crashing the app. That design
     * left "ready" and "silently inert" indistinguishable: the shipped encoder
     * returned null for every message while `LocalLlmProvider` logged "on-device
     * classifier ready", because its centroid asset was being decoded with the
     * wrong byte order. Readiness UI asks this instead, so a degraded runtime is
     * reported as degraded.
     */
    val isOperational: Boolean

    /**
     * Extracts transaction data from one SMS.
     *
     * [categoryIdByName] is the user's live category set as `id -> human name`.
     * A runtime that can constrain its output to a vocabulary should use it, and
     * the returned [SmsExtraction.category] is resolved against it. Passing the
     * full map rather than a bare name list matters: without the real ids a
     * runtime cannot map its own answer back onto stored categories.
     *
     * Returns null when this message yields no usable result — the runtime was
     * unavailable, timed out, or the model declined. Callers fall back to the
     * regex parser; a null is deliberately *not* a "not a transaction" verdict.
     */
    suspend fun extract(smsBody: String, categoryIdByName: Map<String, String>): SmsExtraction?

    /** Releases native resources. Safe to call more than once. */
    fun close()
}
