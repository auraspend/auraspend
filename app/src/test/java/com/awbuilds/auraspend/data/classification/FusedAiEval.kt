package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.data.ai.ClassificationEval
import com.awbuilds.auraspend.data.ai.SmsExtraction
import com.awbuilds.auraspend.domain.model.TransactionType
import org.json.JSONObject
import java.io.File

/**
 * Scores what the app actually does with an on-device model in the loop.
 *
 * Every accuracy number recorded so far is one of two things that never happen
 * in production: the regex layer alone (`RegexBaselineEval`) or a bare model
 * (`tools/gguf_eval.py`). Neither is what a user experiences. Production runs
 *
 *   isOtpOrAlertMessage -> BankMessageParser.parse -> AiSignalFusion.fuse(ai)
 *
 * and that is a different system, because of two rules inside `fuse`:
 *
 *  - a model "not a transaction" verdict wipes `amount` and `type`, so the
 *    pipeline's unresolved-fields gate then drops the row entirely. A false veto
 *    does not merely mis-file a transaction, it deletes it.
 *  - on every other conflict the *regex* result wins. The model can only ever
 *    fill a gap the parser left, so a weak model degrades toward the regex
 *    answer instead of corrupting it.
 *
 * The second rule is why a standalone model score is close to meaningless on its
 * own: a model can look terrible standalone and change almost nothing, or look
 * respectable and still destroy transactions through the veto. This evaluator
 * exists to measure the system rather than the component.
 *
 * It takes the model output from `tools/gguf_eval.py` as a file rather than
 * running weights on the JVM, so the same measurement works for any GGUF and the
 * unit suite stays fast and hermetic. The *fusion* and *parsing* are the real
 * production code, not a reimplementation.
 */
object FusedAiEval {

    /**
     * Where `tools/gguf_eval.py --json` writes, relative to the repo root.
     *
     * Gradle runs unit tests with the *module* directory as the working
     * directory, not the repo root, so both are tried — the same
     * relative-path fallback `ClassificationEval.loadCorpus` already uses for
     * `src/test/resources`.
     */
    const val DEFAULT_REPORT = "docs/evals/qwen2.5-0.5b-2026-09-29.json"

    /** Report's probability field is a runtime's raw P(transaction). */
    const val SEMANTICS_P_TRANSACTION = "p_transaction"

    /** Report's probability field already matches `SmsExtraction.isTransactionProbability`. */
    const val SEMANTICS_VERDICT_CONFIDENCE = "verdict_confidence"

    private val PROBABILITY_SEMANTICS = setOf(SEMANTICS_P_TRANSACTION, SEMANTICS_VERDICT_CONFIDENCE)

    private val FIELDS = listOf("isTransaction", "type", "category", "merchant", "subscription")

    data class Outcome(
        val runtimeId: String,
        val total: Int,
        val exactMatches: Int,
        val perField: List<ClassificationEval.FieldAccuracy>,
        /** Rows the pipeline would have saved on its own but the model destroyed. */
        val destroyedTransactions: List<String>,
        /** Rows the model turned from nothing into a saveable transaction. */
        val rescuedTransactions: List<String>,
        /** Rows dropped by the pipeline, and which gate. */
        val dropped: Map<String, Int>
    ) {
        fun percent(field: String): Double =
            perField.firstOrNull { it.field == field }
                ?.let { 100.0 * it.correct / it.total } ?: 0.0

        val exactPercent: Double get() = if (total == 0) 0.0 else 100.0 * exactMatches / total

        fun format(): String = buildString {
            appendLine("runtime          : $runtimeId")
            appendLine("cases             : $total")
            appendLine("exact match      : $exactMatches/$total (${pct(exactPercent)})")
            appendLine("DESTROYED by veto: ${destroyedTransactions.size}  <-- regex would have saved these")
            appendLine("rescued by model : ${rescuedTransactions.size}")
            appendLine("per-field accuracy:")
            perField.forEach { appendLine("  ${it.field.padEnd(15)} ${it.correct}/${it.total}  ${pct(100.0 * it.correct / it.total)}%") }
        }

        private fun pct(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
    }

    /**
     * @param aiByCaseId the model's per-message output, or null to score regex-only
     *        (which must reproduce [RegexBaselineEval] exactly — a self-check).
     */
    fun run(
        aiByCaseId: Map<String, SmsExtraction?>,
        runtimeId: String,
        corpus: List<ClassificationEval.Case> = ClassificationEval.loadCorpus()
    ): Outcome {
        val destroyed = mutableListOf<String>()
        val rescued = mutableListOf<String>()
        val dropped = mutableMapOf<String, Int>()
        val outcomes = mutableListOf<Triple<String, String, Boolean>>() // field, expected, ok

        corpus.forEach { case ->
            val ai = aiByCaseId[case.id]
            val fused = RegexBaselineEval.classify(case.body, ai, case.sender)

            // What the pipeline would have produced with no model at all. The
            // difference between the two is the entire effect of the model.
            val withoutAi = RegexBaselineEval.classify(case.body, null, case.sender)
            if (withoutAi.extraction != null && fused.extraction == null) {
                destroyed += case.id
            }
            if (withoutAi.extraction == null && fused.extraction != null) {
                rescued += case.id
            }
            if (fused.extraction == null) {
                val key = fused.skip.name
                dropped[key] = (dropped[key] ?: 0) + 1
            }

            val p = fused.extraction
            FIELDS.forEach { field ->
                val exp = expected(case, field)
                val act = p?.let { actual(it, field) }
                outcomes += Triple(field, exp ?: "\u0000null", exp == act)
            }
        }

        val perField = FIELDS.map { f ->
            val rows = outcomes.filter { it.first == f }
            ClassificationEval.FieldAccuracy(
                field = f,
                correct = rows.count { it.third },
                total = rows.size
            )
        }

        // Exact match, recomputed per case rather than accumulated per field.
        var exact = 0
        corpus.forEach { case ->
            val fused = RegexBaselineEval.classify(case.body, aiByCaseId[case.id], case.sender)
            val p = fused.extraction
            if (FIELDS.all { expected(case, it) == (p?.let { s -> actual(s, it) }) }) exact++
        }

        return Outcome(
            runtimeId = runtimeId,
            total = corpus.size,
            exactMatches = exact,
            perField = perField,
            destroyedTransactions = destroyed,
            rescuedTransactions = rescued,
            dropped = dropped
        )
    }

    /** Reads a `tools/gguf_eval.py --json` report into per-case extractions. */
    fun loadReport(path: String = DEFAULT_REPORT): Pair<String, Map<String, SmsExtraction?>> {
        val file = File(path).takeIf { it.exists() }
            ?: File("../$path").takeIf { it.exists() }
            ?: error(
                "eval report not found at $path or ../$path — run " +
                    "`tools/gguf_eval.py --model <gguf> --json $path` first"
            )
        val root = JSONObject(file.readText())
        val runtimeId = root.optString("model", "model")
        val rows = root.getJSONArray("rows")
        // How the report's `isTransactionProbability` is meant. `p_transaction` is
        // a runtime's raw probability of the transaction class (what a softmax
        // head emits); `verdict_confidence` is already the value
        // `SmsExtraction.isTransactionProbability` is defined to carry. Reading a
        // raw probability as if it were a verdict confidence silently disables
        // `AiSignalFusion.mayDiscard` for every negative verdict — which is
        // exactly how the shipped encoder's 0-false-veto result came to be
        // produced by a gate that could never fire.
        val semantics = root.optString("probabilitySemantics", SEMANTICS_VERDICT_CONFIDENCE)
        require(semantics in PROBABILITY_SEMANTICS) {
            "report declares unknown probabilitySemantics='$semantics'; expected one of " +
                PROBABILITY_SEMANTICS
        }
        val out = LinkedHashMap<String, SmsExtraction?>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val id = row.getString("id")
            out[id] = if (row.isNull("pred")) null
            else toExtraction(row.getJSONObject("pred"), semantics)
        }
        return runtimeId to out
    }

    private fun toExtraction(pred: JSONObject, semantics: String): SmsExtraction {
        val isTransaction = pred.optBoolean("isTransaction", false)
        val rawProbability = if (pred.has("isTransactionProbability") &&
            !pred.isNull("isTransactionProbability")
        ) pred.optDouble("isTransactionProbability").toFloat() else null
        return SmsExtraction(
            isTransaction = isTransaction,
            type = pred.optString("type").takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { TransactionType.valueOf(it.uppercase()) }.getOrNull() },
            category = pred.optString("category").takeIf { it.isNotBlank() && it != "null" },
            merchant = pred.optString("merchant").takeIf { it.isNotBlank() && it != "null" },
            isSubscription = pred.optBoolean("isSubscription", false),
            rawModelOutput = pred.optString("raw").takeIf { it.isNotBlank() },
            // Must be carried through, or `AiSignalFusion.mayDiscard` sees null and
            // takes the legacy unconditional-veto branch — which would silently
            // measure a *worse* system than the one that ships, and blame the model
            // for a fusion path the model is never actually on. And it must carry
            // the *same* meaning the runtime produces, or the veto gate is measured
            // in a protocol the runtime does not speak.
            isTransactionProbability = rawProbability?.let {
                if (semantics == SEMANTICS_P_TRANSACTION) {
                    SmsExtraction.verdictConfidence(isTransaction, it)
                } else {
                    it
                }
            }
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

    private fun actual(p: SmsExtraction, field: String): String? = when (field) {
        "isTransaction" -> p.isTransaction.toString()
        "type" -> p.type?.name
        "category" -> ClassificationEval.savedCategory(p.category)
        "merchant" -> p.merchant
        "subscription" -> p.isSubscription.toString()
        else -> null
    }
}
