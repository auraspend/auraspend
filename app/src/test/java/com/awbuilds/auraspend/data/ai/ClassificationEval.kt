package com.awbuilds.auraspend.data.ai

import com.awbuilds.auraspend.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedReader

/**
 * Accuracy harness for the on-device classifier.
 *
 * Until this existed, classification accuracy was **unmeasurable**: no test ran an
 * SMS through the model, and `AuditRegressionTest` exercised only the regex layer.
 * That made any runtime change — swapping llama.cpp for a smaller grammar-driven
 * model, say — unjudgeable, because there was no baseline to compare against and
 * nothing that would fail if the new runtime were worse.
 *
 * The harness runs a labelled corpus through a classifier and reports both
 * per-field accuracy and exact-match rate. Both are reported deliberately:
 * exact-match is the honest headline number, while per-field shows *which* field
 * drifts when a runtime regresses.
 *
 * Deliberately free of Android and of any live model, so it runs in ordinary CI
 * against a scripted fake. Live comparison against a real device is a separate,
 * opt-in path (see the `classificationEval` Gradle task).
 */
object ClassificationEval {

    /**
     * The category that actually gets saved for a resolution result.
     *
     * A missing category is not a different answer from `cat_other`: the pipeline
     * stores Other when nothing matches (`TransactionClassifier` and
     * `SmsAutoClassifier` both fall back to it). Both evaluators compare against
     * the saved outcome, so they call this — before 2026-10-16 they compared the
     * intermediate null and counted thirteen correct rows as misses, which is a
     * metric bug rather than a classification one.
     */
    fun savedCategory(categoryId: String?): String = categoryId ?: "cat_other"

    /** One labelled SMS. Mirrors the shape the classifier is asked to produce. */
    data class Case(
        val id: String,
        val body: String,
        val isTransaction: Boolean,
        val type: TransactionType?,
        val category: String?,
        val merchant: String?,
        val subscription: Boolean,
        val note: String? = null,
        /**
         * The SMS sender ID, as the phone receives it (e.g. `AXISBK`,
         * `VI-AXISBK-S`). Free metadata the app already has and the corpus
         * originally did not capture, which is why sender-routed parsing could
         * not be measured before.
         */
        val sender: String? = null
    )

    /** A single field's outcome for a single case. */
    data class FieldOutcome(val field: String, val expected: String?, val actual: String?)

    data class CaseResult(
        val case: Case,
        val predicted: SmsExtraction?,
        val fields: List<FieldOutcome>,
        val exactMatch: Boolean
    )

    data class FieldAccuracy(
        val field: String,
        val correct: Int,
        val total: Int
    ) {
        val rate: Double get() = if (total == 0) 1.0 else correct.toDouble() / total
        val percent: Double get() = rate * 100.0
    }

    data class Report(
        val runtimeId: String,
        val total: Int,
        val exactMatches: Int,
        /** Cases where the runtime returned nothing at all. */
        val noResult: Int,
        val perField: List<FieldAccuracy>,
        val results: List<CaseResult>
    ) {
        val exactMatchRate: Double
            get() = if (total == 0) 1.0 else exactMatches.toDouble() / total
        val exactMatchPercent: Double get() = exactMatchRate * 100.0

        fun fieldRate(name: String): Double? = perField.firstOrNull { it.field == name }?.percent

        /**
         * Cases where the runtime claimed "not a transaction" but the corpus says
         * money did move. This is the most damaging error class: the fusion layer
         * treats such a result as a veto and throws away a good regex parse.
         */
        fun falseVetoes(): List<CaseResult> = results.filter {
            it.predicted?.isTransaction == false && it.case.isTransaction
        }

        fun format(): String = buildString {
            appendLine("runtime            : $runtimeId")
            appendLine("cases               : $total")
            appendLine("exact match         : $exactMatches/$total (${fmt(exactMatchPercent)}%)")
            appendLine("no result returned  : $noResult")
            val vetoes = falseVetoes()
            appendLine("false vetoes        : ${vetoes.size}")
            appendLine("per-field accuracy  :")
            perField.forEach { appendLine("  ${it.field.padEnd(15)} ${it.correct}/${it.total}  ${fmt(it.percent)}%") }
        }

        private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
    }

    private val FIELDS = listOf("isTransaction", "type", "category", "merchant", "subscription")

    /**
     * Runs [corpus] through [classifier] and scores every field.
     *
     * A null return is scored as a total miss for all fields rather than being
     * skipped — a runtime that declines to answer is not better than one that
     * answers wrongly, and silently excluding it would flatter the score.
     */
    fun run(runtimeId: String, corpus: List<Case>, classifier: OnDeviceClassifier): Report {
        val results = corpus.map { case ->
            // Test harness, so blocking here is fine; production call sites are suspend.
            val predicted = runBlocking { classifier.extract(case.body, DEFAULT_CATEGORIES) }
            val fields = FIELDS.map { field ->
                FieldOutcome(
                    field = field,
                    expected = expectedValue(case, field),
                    actual = predicted?.let { actualValue(it, field) }
                )
            }
            CaseResult(case, predicted, fields, fields.all { it.expected == it.actual })
        }

        val perField = FIELDS.map { field ->
            val outcomes = results.flatMap { it.fields }.filter { it.field == field }
            FieldAccuracy(
                field = field,
                correct = outcomes.count { it.expected == it.actual },
                total = outcomes.size
            )
        }

        return Report(
            runtimeId = runtimeId,
            total = results.size,
            exactMatches = results.count { it.exactMatch },
            noResult = results.count { it.predicted == null },
            perField = perField,
            results = results
        )
    }

    private fun expectedValue(case: Case, field: String): String? = when (field) {
        "isTransaction" -> case.isTransaction.toString()
        "type" -> case.type?.name
        "category" -> case.category
        "merchant" -> case.merchant
        "subscription" -> case.subscription.toString()
        else -> null
    }

    private fun actualValue(prediction: SmsExtraction, field: String): String? = when (field) {
        "isTransaction" -> prediction.isTransaction.toString()
        "type" -> prediction.type?.name
        "category" -> prediction.category
        "merchant" -> prediction.merchant
        "subscription" -> prediction.isSubscription.toString()
        else -> null
    }

    /**
     * Category names as the app seeds them. The classifier is given these at
     * runtime, so the corpus is scored against the same vocabulary.
     */
    val DEFAULT_CATEGORIES: Map<String, String> = linkedMapOf(
        "cat_food" to "Food & Dining",
        "cat_transport" to "Transport",
        "cat_subscription" to "Subscriptions",
        "cat_salary" to "Salary",
        "cat_shopping" to "Shopping",
        "cat_grocery" to "Groceries",
        "cat_healthcare" to "Healthcare",
        "cat_education" to "Education",
        "cat_bills" to "Bills & Utilities",
        "cat_transfer" to "Transfer",
        "cat_other" to "Other"
    )

    /** Where the committed corpus lives, relative to the Gradle module dir. */
    const val CORPUS_PATH = "src/test/resources/golden/sms_corpus.jsonl"

    /**
     * Loads the JSONL corpus.
     *
     * AGP routes `src/test/resources` through the `java_res` task rather than a
     * plain classpath directory, so the classpath lookup is tried first and the
     * source path is the fallback — the same relative-path approach the
     * design-system guard tests already rely on.
     */
    fun loadCorpus(resourcePath: String = "golden/sms_corpus.jsonl"): List<Case> =
        readLines(
            ClassificationEval::class.java.classLoader!!.getResourceAsStream(resourcePath)
                ?.bufferedReader()?.use(BufferedReader::readLines)
                ?: java.io.File(CORPUS_PATH).takeIf { it.exists() }?.readLines()
                ?: error("Golden corpus not found (classpath or $CORPUS_PATH)")
        )

    private fun readLines(lines: List<String>): List<Case> =
        lines.mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() }?.let(::parseCase) }

    private fun parseCase(line: String): Case {
        val o = JSONObject(line)
        return Case(
            id = o.getString("id"),
            body = o.getString("body"),
            isTransaction = o.getBoolean("isTransaction"),
            // Case-insensitive: the corpus is hand-editable ground truth written in
            // the same lowercase vocabulary the model is asked to emit, so it should
            // not have to match the enum's screaming case to be readable.
            type = o.optString("type").takeIf { it.isNotBlank() && it != "null" }
                ?.let { TransactionType.valueOf(it.uppercase()) },
            category = o.optString("category").takeIf { it.isNotBlank() },
            merchant = o.optString("merchant").takeIf { it.isNotBlank() },
            subscription = o.getBoolean("subscription"),
            note = o.optString("note").takeIf { it.isNotBlank() },
            // Blank for most cases, which is a real state rather than missing
            // data: many users bank with institutions we have no parser for, and
            // sender routing has to behave correctly when it cannot identify one.
            sender = o.optString("sender").takeIf { it.isNotBlank() }
        )
    }
}
