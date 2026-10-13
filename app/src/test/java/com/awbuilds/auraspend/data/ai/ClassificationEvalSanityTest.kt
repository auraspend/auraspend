package com.awbuilds.auraspend.data.ai

import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the eval harness itself.
 *
 * An accuracy harness is only as trustworthy as its arithmetic. If the scoring
 * quietly mis-counts, it will happily report that a worse runtime is better — the
 * exact failure this harness exists to prevent. These tests pin the metric maths
 * against hand-computed expectations.
 */
class ClassificationEvalSanityTest {

    private fun case(
        id: String = "c",
        isTransaction: Boolean = true,
        type: TransactionType? = TransactionType.EXPENSE,
        category: String? = "cat_food",
        merchant: String? = "Swiggy",
        subscription: Boolean = false
    ) = ClassificationEval.Case(
        id = id,
        body = "body-$id",
        isTransaction = isTransaction,
        type = type,
        category = category,
        merchant = merchant,
        subscription = subscription
    )

    private fun extraction(
        isTransaction: Boolean = true,
        type: TransactionType? = TransactionType.EXPENSE,
        category: String? = "cat_food",
        merchant: String? = "Swiggy",
        subscription: Boolean = false
    ) = SmsExtraction(isTransaction, type, category, merchant, subscription)

    /** A classifier that replays a fixed list of results, in order. */
    private class ScriptedClassifier(private val results: List<SmsExtraction?>) : OnDeviceClassifier {
        override val id = "scripted"
        override val isOperational: Boolean = true
        var calls = 0
        override suspend fun extract(smsBody: String, categoryIdByName: Map<String, String>): SmsExtraction? {
            val r = results.getOrNull(calls)
            calls++
            return r
        }

        override fun close() = Unit
    }

    @Test
    fun `a perfect runtime scores 100 percent exact match`() {
        val corpus = List(10) { case(id = "c$it") }
        val classifier = ScriptedClassifier(List(10) { extraction() })
        val report = ClassificationEval.run("scripted", corpus, classifier)

        assertEquals(10, report.total)
        assertEquals(10, report.exactMatches)
        assertEquals(100.0, report.exactMatchPercent, 0.001)
        assertEquals(0, report.noResult)
        report.perField.forEach { assertEquals(100.0, it.percent, 0.001) }
    }

    @Test
    fun `a runtime returning nothing is scored as a total miss, not skipped`() {
        // The important assertion: a null must NOT be excluded from the
        // denominator, or "declines to answer" would look like a high score.
        val corpus = listOf(case(id = "a"), case(id = "b"))
        val classifier = ScriptedClassifier(listOf(extraction(), null))
        val report = ClassificationEval.run("scripted", corpus, classifier)

        assertEquals(2, report.total)
        assertEquals(1, report.exactMatches)
        assertEquals(1, report.noResult)
        assertEquals(50.0, report.exactMatchPercent, 0.001)
        // A null contributes a wrong answer to every field, not an empty tally:
        // 1 of 2 correct is 50%, not 100% (which is what skipping would give).
        assertEquals(50.0, report.fieldRate("category")!!, 0.001)
    }

    @Test
    fun `per-field accuracy isolates which field drifted`() {
        val corpus = List(4) { case(id = "c$it") }
        // Every prediction has the right shape but the wrong category and merchant.
        val classifier = ScriptedClassifier(
            List(4) { extraction(category = "cat_other", merchant = "Zomato") }
        )
        val report = ClassificationEval.run("scripted", corpus, classifier)

        assertEquals(0.0, report.exactMatchPercent, 0.001)
        // The fields that were right still score 100.
        assertEquals(100.0, report.fieldRate("isTransaction")!!, 0.001)
        assertEquals(100.0, report.fieldRate("type")!!, 0.001)
        assertEquals(100.0, report.fieldRate("subscription")!!, 0.001)
        // The two that drifted score 0.
        assertEquals(0.0, report.fieldRate("category")!!, 0.001)
        assertEquals(0.0, report.fieldRate("merchant")!!, 0.001)
    }

    @Test
    fun `false vetoes are surfaced separately`() {
        // A "not a transaction" verdict on a real transaction is the most damaging
        // error: the fusion layer treats it as a veto and discards a good parse.
        val corpus = listOf(
            case(id = "real", isTransaction = true),
            case(id = "noise", isTransaction = false, type = null, category = "cat_other", merchant = null)
        )
        val classifier = ScriptedClassifier(
            listOf(
                extraction(isTransaction = false, type = null, category = "cat_other", merchant = null),
                extraction(isTransaction = false, type = null, category = "cat_other", merchant = null)
            )
        )
        val report = ClassificationEval.run("scripted", corpus, classifier)

        val vetoes = report.falseVetoes()
        assertEquals(1, vetoes.size)
        assertEquals("real", vetoes.first().case.id)
    }

    @Test
    fun `null fields compare equal to a null prediction field`() {
        // A corpus case with no merchant must not be scored as a miss just
        // because both sides are null.
        val corpus = listOf(
            case(id = "nom", isTransaction = false, type = null, category = "cat_other", merchant = null)
        )
        val classifier = ScriptedClassifier(
            listOf(extraction(isTransaction = false, type = null, category = "cat_other", merchant = null))
        )
        val report = ClassificationEval.run("scripted", corpus, classifier)
        assertEquals(1, report.exactMatches)
        assertEquals(100.0, report.fieldRate("merchant")!!, 0.001)
    }

    @Test
    fun `the golden corpus loads and is well formed`() {
        val corpus = ClassificationEval.loadCorpus()
        assertTrue("corpus should not be empty", corpus.isNotEmpty())
        assertTrue("corpus should be a meaningful size, got ${corpus.size}", corpus.size >= 50)

        // Ids must be unique, or a diff report would be ambiguous.
        val ids = corpus.map { it.id }
        assertEquals("duplicate corpus ids", ids.size, ids.distinct().size)

        // Every body must be non-blank; a blank body is a fixture bug.
        assertTrue("blank SMS body in corpus", corpus.none { it.body.isBlank() })

        // A corpus with no negatives cannot measure the false-veto rate, which
        // is the metric the decision hinges on.
        val negatives = corpus.count { !it.isTransaction }
        assertTrue("corpus needs non-transaction cases, found $negatives", negatives >= 10)

        // And it must exercise more than one category, or it proves nothing about
        // category resolution.
        assertTrue(
            "corpus needs category variety",
            corpus.mapNotNull { it.category }.distinct().size >= 5
        )

        // Subscription cases matter because the AI can only turn them on.
        assertTrue("corpus needs subscription cases", corpus.any { it.subscription })
    }

    @Test
    fun `the corpus is the one on the classpath`() {
        // Guards against a silently empty load making every accuracy claim vacuous.
        val corpus = ClassificationEval.loadCorpus()
        assertTrue(
            "expected the real corpus, not a stub",
            corpus.any { it.id == "axis_multiline_tulip" }
        )
    }

    @Test
    fun `report formats without throwing and names the runtime`() {
        val classifier = ScriptedClassifier(listOf(extraction(), extraction()))
        val report = ClassificationEval.run("llama.cpp", listOf(case("a"), case("b")), classifier)
        val text = report.format()
        assertTrue("report should name the runtime: $text", text.contains("llama.cpp"))
        assertTrue("report should include exact match", text.contains("exact match"))
    }
}
