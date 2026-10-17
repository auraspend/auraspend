package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.ai.SmsExtraction
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType

/**
 * Multi-signal fusion of the regex parser result with the on-device LLM verdict.
 *
 * Priority rules:
 *  - an explicit LLM "not a transaction" veto wins (wipes amount/type so the pipeline skips);
 *  - explicit debit/credit keywords from the regex parser beat an LLM type guess on conflict;
 *  - curated merchant-keyword categories survive unless a subscription signal fires;
 *  - recurring-payment keywords or a known-subscription service force subscription classification,
 *    even without AI.
 */
object AiSignalFusion {

    data class Fused(
        val parsed: ParsedBankMessage,
        val isSubscription: Boolean,
        val usedAi: Boolean
    )

    /** Mandates / auto-debit machinery that always implies a recurring charge. */
    private val recurringKeywords = Regex(
        "(?i)\\b(renewal|renews?|auto[- ]?pay(?:ment)?|autopay|nach|e[- ]?mandate|emandate|" +
            "mandate|standing\\s+instruction|si\\s+debit|recurring|subscription|plan\\s+renewal|billing\\s+cycle|" +
            "insurance\\s+(?:premium|policy)|lic\\s+(?:insurance|premium))\\b"
    )

    /** True when [text] clearly names a subscription service. */
    fun looksLikeSubscriptionService(text: String?): Boolean =
        SubscriptionCatalog.match(text) != null

    fun fuse(regexParsed: ParsedBankMessage, ai: SmsExtraction?): Fused {
        val base = regexParsed

        if (ai != null && !ai.isTransaction && mayDiscard(ai, base)) {
            // Model veto: not a transaction (OTP, promo, balance alert...).
            return Fused(base.copy(amount = null, type = null), isSubscription = false, usedAi = true)
        }

        val resolvedType = when {
            ai?.type == null -> base.type
            base.type == null -> ai.type
            // Both present but conflicting: keyword signals ("credited"/"debited") are explicit.
            // Applies to every runtime including a calibrated one — the parser
            // read a literal movement word out of the message, which is stronger
            // evidence than any model's inference from it.
            else -> base.type
        }

        val recurringByKeyword = resolvedType == TransactionType.EXPENSE &&
            recurringKeywords.containsMatchIn(base.rawMessage)
        val subscriptionMerchant = resolvedType == TransactionType.EXPENSE && (
            looksLikeSubscriptionService(base.merchant) ||
                (base.merchant == null && looksLikeSubscriptionService(base.note)) ||
                // Last resort: a debit naming a subscription service in its body.
                (base.merchant == null && base.note == null &&
                    looksLikeSubscriptionService(base.rawMessage))
            )
        val isSubscription = (ai?.isSubscription == true) || recurringByKeyword || subscriptionMerchant

        val resolvedMerchant = base.merchant ?: ai?.merchant

        // Precedence is deliberate and one-directional: a category the parser or
        // the user already resolved always wins, and the keyword map is consulted
        // only after the model, because a whole-word scan over a closed merchant
        // vocabulary is more reliable than a 0.5B model's guess. The map can
        // therefore only ever fill a gap, never override.
        val resolvedCategory = when {
            isSubscription -> "cat_subscription"
            base.categoryId != null -> base.categoryId
            else -> ai?.category
                ?: CategoryKeywordMap.resolve(
                    resolvedMerchant,
                    resolvedType == TransactionType.EXPENSE
                )
        }

        val confidence = computeConfidence(base.confidence, base.type, resolvedType, ai)

        return Fused(
            parsed = base.copy(
                amount = base.amount,
                type = resolvedType,
                merchant = resolvedMerchant,
                categoryId = resolvedCategory,
                confidence = confidence
            ),
            isSubscription = isSubscription,
            usedAi = ai != null
        )
    }

    /**
     * Confidence above which a model may discard a transaction on its own.
     *
     * Deliberately high, and deliberately not the only condition. A false veto
     * does not mis-file a transaction — it deletes it, because the pipeline
     * skips any row whose amount or type is unresolved. Measured rates of
     * "not a transaction" on real transactions were 7/46 (Qwen2.5-0.5B) and
     * 40/46 (SmolLM2-135M), so the model is far more often wrong about this
     * than right and the bar has to reflect that.
     */
    const val DISCARD_MIN_CONFIDENCE = 0.9f

    /**
     * Whether a model verdict is allowed to discard a parsed transaction.
     *
     * Two independent conditions, both required:
     *
     *  - the model must be *confident*, and
     *  - the regex layer must not have found an amount.
     *
     * The second is the important one. A message the parser could not read has
     * nothing worth protecting, so a confident verdict can safely clear it. A
     * message the parser *did* read is exactly the case where the model is most
     * likely to be wrong, and where discarding destroys real data. Confidence
     * alone is not sufficient.
     *
     * A runtime with no calibrated head reports null, and keeps the previous
     * unconditional behaviour. The shipped encoder *does* have a calibrated
     * head and fills this field via `SmsExtraction.verdictConfidence` — the
     * confidence in the verdict it actually emitted, not the raw probability of
     * the transaction class. Reporting that raw probability instead made this
     * gate unreachable on a negative verdict (it is below 0.5 there by
     * construction), so the recorded "0 destroyed" measurement was produced by
     * dead code; `FusedAiEvalTest` now fails if the gate becomes unreachable
     * again.
     */
    private fun mayDiscard(ai: SmsExtraction, base: ParsedBankMessage): Boolean {
        val probability = ai.isTransactionProbability ?: return true
        return probability >= DISCARD_MIN_CONFIDENCE && base.amount == null
    }

    private fun computeConfidence(
        regexConfidence: Float,
        regexType: TransactionType?,
        fusedType: TransactionType?,
        ai: SmsExtraction?
    ): Float {
        var confidence = regexConfidence
        when {
            ai == null -> Unit // heuristic only
            regexType != null && ai.type != null && regexType == ai.type ->
                confidence = (confidence + 0.08f).coerceAtMost(0.99f) // signals agree
            regexType != null && ai.type != null ->
                confidence = (confidence - 0.10f).coerceAtLeast(0.30f) // conflict
            else -> Unit
        }
        if (fusedType == null) confidence = confidence.coerceAtMost(0.35f)
        return confidence
    }
}
