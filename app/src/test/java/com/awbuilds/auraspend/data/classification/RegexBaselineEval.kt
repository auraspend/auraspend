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
            score(case, classify(case.body, case.sender))
        }

        val perField = FIELDS.map { field ->
            val outcomes = results.flatMap { it.fields }
                .filter { it.field == field && it.applicable }
            ClassificationEval.FieldAccuracy(
                field = field,
                correct = outcomes.count { ClassificationEval.same(it.field, it.expected, it.actual) },
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

    internal val FIELDS = listOf(
        "isTransaction", "type", "category", "merchant", "subscription", "amount", "date"
    )

    /** Why a case produced no saveable result. The distinction matters when
     *  reading a report: "the alert filter dropped it" and "the parser could not
     *  read a type" are very different problems. */
    enum class SkipReason { NONE, ALERT_FILTER, UNRESOLVED_FIELDS }

    data class Classified(
        val extraction: com.awbuilds.auraspend.data.ai.SmsExtraction?,
        val skip: SkipReason,
        /** The regex parse, even when the row is not saveable; amount/date live here. */
        val parsed: com.awbuilds.auraspend.domain.model.ParsedBankMessage? = null
    )

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
        if (SmsAutoClassifier.isOtpOrAlertMessage(body, sender)) {
            return Classified(null, SkipReason.ALERT_FILTER)
        }
        val parsed = TransactionClassifier.classify(body, sender)
        val fused = AiSignalFusion.fuse(parsed, ai)
        // The pipeline skips a row when amount or type is unresolved; scoring that
        // as "no result" keeps the floor honest about what the app can actually save.
        if (fused.parsed.amount == null || fused.parsed.type == null) {
            return Classified(null, SkipReason.UNRESOLVED_FIELDS, fused.parsed)
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
            SkipReason.NONE,
            fused.parsed
        )
    }

    /**
     * Score one case. Shared with [FusedAiEval] so the model harness and the
     * regex floor cannot drift in how they treat a rejection or an undated SMS.
     */
    internal fun score(case: ClassificationEval.Case, classified: Classified): ClassificationEval.CaseResult {
        val fields = FIELDS.map { field ->
            ClassificationEval.FieldOutcome(
                field = field,
                expected = expected(case, field),
                actual = actual(classified, field),
                // Only score a date the corpus actually states. An undated
                // message inherits its arrival time, which the corpus does not
                // record — the parser no longer reads "today" out of prose
                // (`MessageDateFallbackTest`), so this is about the fixture's
                // silence, not about a legitimate wall-clock answer.
                applicable = field != "date" || case.date != null
            )
        }
        val rejection = when (classified.skip) {
            SkipReason.ALERT_FILTER -> ClassificationEval.Rejection.ALERT_FILTER
            SkipReason.UNRESOLVED_FIELDS -> ClassificationEval.Rejection.UNRESOLVED_FIELDS
            SkipReason.NONE -> ClassificationEval.Rejection.NONE
        }
        return ClassificationEval.CaseResult(
            case = case,
            predicted = classified.extraction,
            fields = fields,
            exactMatch = fields.filter { it.applicable }
                .all { ClassificationEval.same(it.field, it.expected, it.actual) },
            rejection = rejection,
            parsed = classified.parsed
        )
    }

    private fun expected(case: ClassificationEval.Case, field: String): String? = when (field) {
        "isTransaction" -> case.isTransaction.toString()
        "type" -> case.type?.name
        "category" -> case.category
        "merchant" -> case.merchant
        "subscription" -> case.subscription.toString()
        "amount" -> case.amount?.let(ClassificationEval::formatAmount)
        "date" -> case.date
        else -> null
    }

    private fun actual(
        classified: Classified,
        field: String
    ): String? {
        // A skip is a verdict, not a missing value. A non-transaction the
        // classifier correctly rejected must score as a pass; a transaction it
        // dropped must score as a failure with a reason attached.
        if (classified.skip != SkipReason.NONE) {
            return when (field) {
                "isTransaction" -> "false"
                "category" -> "cat_other"
                "subscription" -> "false"
                else -> null
            }
        }
        val prediction = classified.extraction ?: return null
        return when (field) {
            "isTransaction" -> prediction.isTransaction.toString()
            "type" -> prediction.type?.name
            // A missing category is not a different answer from "Other": the pipeline
            // saves `cat_other` when nothing matches (TransactionClassifier and
            // SmsAutoClassifier both fall back to it), so comparing the intermediate
            // null against a corpus that labelled the saved outcome as `cat_other`
            // counted a correct result as a miss.
            "category" -> ClassificationEval.savedCategory(prediction.category)
            "merchant" -> prediction.merchant
            "subscription" -> prediction.isSubscription.toString()
            "amount" -> classified.parsed?.amount?.let(ClassificationEval::formatAmount)
            "date" -> classified.parsed?.date?.toLocalDate()?.toString()
            else -> null
        }
    }

    /** Categories the seeded defaults expose, so the regex path resolves the same vocabulary. */
    val CATEGORY_IDS: List<String> = defaultCategories.map { it.id }

    /** Convenience for reporting an unknown type without crashing a report. */
    fun describeType(type: TransactionType?): String = type?.name ?: "none"
}
