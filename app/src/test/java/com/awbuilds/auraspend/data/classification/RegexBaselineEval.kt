package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.ai.ClassificationEval
import com.awbuilds.auraspend.domain.model.TransactionType

/**
 * Measures what the app achieves with **no on-device AI at all**.
 *
 * This is the floor. Any runtime — llama.cpp today, Needle tomorrow — is only
 * worth its download size if it beats this number. Without it, "the AI improves
 * accuracy" is an unfalsifiable claim, because nothing in the codebase was
 * measuring accuracy before.
 *
 * The path exercised is exactly the production one with the model absent:
 * `SmsAutoClassifier.isOtpOrAlertMessage` (skip gate) → `BankMessageParser.parse`
 * → `AiSignalFusion.fuse(ai = null)`.
 */
object RegexBaselineEval {

    fun run(corpus: List<ClassificationEval.Case> = ClassificationEval.loadCorpus()): ClassificationEval.Report {
        val results = corpus.map { case ->
            // Sender-routed, exactly as production does it: the corpus carries the
            // sender precisely so routing is measured rather than assumed.
            val classified = classify(case.body, case.sender)
            val predicted = classified.extraction
            val fields = FIELDS.map { field ->
                ClassificationEval.FieldOutcome(
                    field = field,
                    expected = expected(case, field),
                    actual = predicted?.let { actual(it, field) }
                )
            }
            ClassificationEval.CaseResult(case, predicted, fields, fields.all { it.expected == it.actual })
        }

        val perField = FIELDS.map { field ->
            val outcomes = results.flatMap { it.fields }.filter { it.field == field }
            ClassificationEval.FieldAccuracy(
                field = field,
                correct = outcomes.count { it.expected == it.actual },
                total = outcomes.size
            )
        }

        return ClassificationEval.Report(
            runtimeId = "regex-only (no AI)",
            total = results.size,
            exactMatches = results.count { it.exactMatch },
            noResult = results.count { it.predicted == null },
            perField = perField,
            results = results
        )
    }

    private val FIELDS = listOf("isTransaction", "type", "category", "merchant", "subscription")

    /** Why a case produced no saveable result. The distinction matters when
     *  reading a report: "the alert filter dropped it" and "the parser could not
     *  read a type" are very different problems. */
    enum class SkipReason { NONE, ALERT_FILTER, UNRESOLVED_FIELDS }

    data class Classified(val extraction: com.awbuilds.auraspend.data.ai.SmsExtraction?, val skip: SkipReason)

    /** Mirrors `SmsPipelineProcessor.processOne` with `ai == null`, and reports
     *  which gate stopped it, exactly as the production pipeline orders them. */
    fun classify(body: String, sender: String? = null): Classified = classify(body, ai = null, sender = sender)

    /**
     * The same path with a model's verdict in the loop.
     *
     * This is the only function in the codebase that reproduces production
     * end-to-end: alert filter, then the parser, then `AiSignalFusion.fuse`,
     * then the pipeline's own unresolved-fields gate. [FusedAiEval] uses it to
     * measure what a model actually changes, which cannot be derived from a
     * standalone model score — a model "not a transaction" verdict wipes amount
     * and type inside `fuse`, so a false veto deletes the transaction rather
     * than mis-filing it.
     */
    fun classify(
        body: String,
        ai: com.awbuilds.auraspend.data.ai.SmsExtraction?,
        sender: String? = null
    ): Classified {
        if (SmsAutoClassifier.isOtpOrAlertMessage(body)) {
            return Classified(null, SkipReason.ALERT_FILTER)
        }
        val parsed = TransactionClassifier.classify(body, sender)
        val fused = AiSignalFusion.fuse(parsed, ai)
        // The pipeline skips a row when amount or type is unresolved; scoring that
        // as "no result" keeps the floor honest about what the app can actually save.
        if (fused.parsed.amount == null || fused.parsed.type == null) {
            return Classified(null, SkipReason.UNRESOLVED_FIELDS)
        }
        return Classified(
            com.awbuilds.auraspend.data.ai.SmsExtraction(
                isTransaction = true,
                type = fused.parsed.type,
                category = fused.parsed.categoryId,
                merchant = fused.parsed.merchant,
                isSubscription = fused.isSubscription,
                confidence = fused.parsed.confidence
            ),
            SkipReason.NONE
        )
    }

    private fun expected(case: ClassificationEval.Case, field: String): String? = when (field) {
        "isTransaction" -> case.isTransaction.toString()
        "type" -> case.type?.name
        "category" -> case.category
        "merchant" -> case.merchant
        "subscription" -> case.subscription.toString()
        else -> null
    }

    private fun actual(
        prediction: com.awbuilds.auraspend.data.ai.SmsExtraction,
        field: String
    ): String? = when (field) {
        "isTransaction" -> prediction.isTransaction.toString()
        "type" -> prediction.type?.name
        // A missing category is not a different answer from "Other": the pipeline
        // saves `cat_other` when nothing matches (TransactionClassifier and
        // SmsAutoClassifier both fall back to it), so comparing the intermediate
        // null against a corpus that labelled the saved outcome as `cat_other`
        // counted a correct result as a miss. Measured 2026-10-16 while removing
        // the rail-to-Transfer mapping: four NEFT/UPI cases expected "Other" and
        // were scored as misses for saying "no keyword", and they became exact
        // once this compared the same thing the database stores.
        "category" -> ClassificationEval.savedCategory(prediction.category)
        "merchant" -> prediction.merchant
        "subscription" -> prediction.isSubscription.toString()
        else -> null
    }

    /**
     * True for a message the hard fraud veto will always block, whether or not it
     * is genuine. Lets the report separate "a bug we should fix" from "a trade-off
     * we chose", instead of lumping both under "dropped".
     */
    fun isFraudLineVeto(body: String): Boolean =
        SmsAutoClassifier.isOtpOrAlertMessage(body) &&
            !Regex("(?i)otp").containsMatchIn(body)

    /** Categories the seeded defaults expose, so the regex path resolves the same vocabulary. */
    val CATEGORY_IDS: List<String> = defaultCategories.map { it.id }

    /** Convenience for reporting an unknown type without crashing a report. */
    fun describeType(type: TransactionType?): String = type?.name ?: "none"
}
