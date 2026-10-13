package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the golden corpus through the *production* pipeline with a recorded
 * model's output in the loop, and prints the report.
 *
 * This is the measurement that decides model questions, and it is the one that
 * had never been run. `RegexBaselineEval` measures the floor with no AI;
 * `tools/gguf_eval.py` measures a model in isolation. Neither is what a user
 * gets, because `AiSignalFusion` gives a model one dangerous power — a "not a
 * transaction" verdict wipes amount and type — and gives it no power at all on
 * any other conflict.
 *
 * Not an assertion-bearing test on the model numbers: a model's score is a
 * measurement, not a contract, and pinning it would turn a tuning decision into
 * a red build. What *is* asserted is the harness's own correctness — the
 * null-model path must reproduce `RegexBaselineEval` exactly, or every number
 * derived from it is untrustworthy.
 */
class FusedAiEvalTest {

    @Test
    fun `with no model the fused path reproduces the regex baseline exactly`() {
        // Guards the harness. FusedAiEval exists to measure the *difference* a
        // model makes, so the no-model case must be a fixed point: if this
        // drifts, every comparison against it is meaningless.
        val regex = RegexBaselineEval.run()
        val fused = FusedAiEval.run(emptyMap(), runtimeId = "regex-only (via fused eval)")

        assertEquals(
            "the fused evaluator must agree with RegexBaselineEval when no model is supplied",
            regex.exactMatches, fused.exactMatches
        )
        assertEquals(regex.total, fused.total)
        regex.perField.forEach { expected ->
            val actual = fused.perField.first { it.field == expected.field }
            assertEquals(
                "field ${expected.field} must match the baseline",
                expected.correct, actual.correct
            )
        }
        assertTrue(
            "a model must not be able to destroy a transaction in the null case",
            fused.destroyedTransactions.isEmpty()
        )
    }

    @Test
    fun `the shipped report is replayed in the runtime's probability protocol`() {
        // The polarity fix's real proof. `isTransactionProbability` means "how sure
        // are you of the answer you gave". The recorded report stores the model's
        // raw P(transaction), so the loader converts it — and if it ever stops
        // doing that, every negative verdict replays below 0.5 and
        // `AiSignalFusion`'s 0.9 discard gate becomes unreachable again. That was
        // the state this test exists to end: 0 destroyed, but because the gate
        // never ran.
        val (_, ai) = FusedAiEval.loadReport("docs/evals/minilm-2026-09-29.json")
        val negative = ai.values.filterNotNull().filter { !it.isTransaction }

        assertTrue("the shipped report must contain negative verdicts", negative.isNotEmpty())
        assertTrue(
            "negative verdicts are replayed as raw P(transaction) (all < 0.5), so the " +
                "discard gate can never fire: " +
                negative.mapNotNull { it.isTransactionProbability },
            negative.all { (it.isTransactionProbability ?: 0f) >= 0.5f }
        )
        assertTrue(
            "the discard gate is still unreachable for every replayed negative verdict — " +
                "the encoder's 0-false-veto result would then be produced by dead code",
            negative.any {
                (it.isTransactionProbability ?: 0f) >= AiSignalFusion.DISCARD_MIN_CONFIDENCE
            }
        )
    }

    @Test
    fun `a model that vetoes everything destroys exactly the transactions it vetoes`() {
        // The failure mode measured on SmolLM2-135M: 43 of 46 real transactions
        // answered "not a transaction". Pinned here as a property of the fusion
        // rules, so the blast radius of a false veto can never silently grow.
        val alwaysVeto = RegexBaselineEval.run().results.associate { result ->
            result.case.id to com.awbuilds.auraspend.data.ai.SmsExtraction(
                isTransaction = false,
                type = null,
                category = null,
                merchant = null,
                isSubscription = false
            )
        }
        val fused = FusedAiEval.run(alwaysVeto, runtimeId = "always-veto (control)")

        // Every row the regex layer could have saved is now destroyed.
        val saveableWithoutModel = RegexBaselineEval.run()
            .results.count { it.predicted != null }
        assertEquals(
            "a blanket veto must destroy every saveable transaction",
            saveableWithoutModel, fused.destroyedTransactions.size
        )
        assertTrue(
            "a blanket veto cannot rescue anything",
            fused.rescuedTransactions.isEmpty()
        )
    }

    @Test
    fun `the shipped encoder must not delete a single real transaction`() {
        // The one criterion every generative runtime failed. A model that calls
        // a real debit "not a transaction" has it nulled in `AiSignalFusion` and
        // then dropped by the pipeline's unresolved-fields gate, so the error is
        // a deletion, not a mis-filing.
        //
        // Recorded from `docs/evals/minilm-2026-09-29.json`, measured through
        // this same evaluator. Generative baselines for contrast: SmolLM2 43/46,
        // FunctionGemma 5/46, Qwen2.5-0.5B 8/46.
        val (runtimeId, ai) = FusedAiEval.loadReport("docs/evals/minilm-2026-09-29.json")
        val fused = FusedAiEval.run(ai, runtimeId = "$runtimeId (fused)")

        println()
        println("===== SHIPPED RUNTIME: $runtimeId =====")
        println(fused.format())

        assertTrue(
            "the shipped encoder deleted ${fused.destroyedTransactions.size} real " +
                "transaction(s): ${fused.destroyedTransactions}. Every generative " +
                "runtime failed this; it is the reason this swap was made.",
            fused.destroyedTransactions.isEmpty()
        )
    }

    @Test
    fun `reports a recorded model end to end`() {
        // Not an assertion on the model's quality — see the class KDoc. This only
        // requires that a recorded report parses and produces a coherent
        // report, and it prints the numbers so they are visible in CI output.
        val (runtimeId, ai) = FusedAiEval.loadReport()
        val fused = FusedAiEval.run(ai, runtimeId = "$runtimeId (fused)")
        compareAgainstRegexOnly(fused)

        println()
        println("===== FUSED SYSTEM (production pipeline, model in the loop) =====")
        println(fused.format())
        println("  destroyed: ${fused.destroyedTransactions.take(12)}")
        println("  rescued  : ${fused.rescuedTransactions.take(12)}")
        println("  dropped  : ${fused.dropped}")
        println("==============================================================")

        assertEquals("every corpus case must be accounted for", 65, fused.total)
    }

    /**
     * Prints the model-in-the-loop result directly against the no-model floor.
     *
     * Kept as a side-by-side because the two numbers differ by an order of
     * magnitude and reading them separately is how the standalone score came to
     * look like a pass. A model can look respectable in isolation and be far
     * worse than useless once `AiSignalFusion` is in the path.
     */
    private fun compareAgainstRegexOnly(fused: FusedAiEval.Outcome) {
        val regex = RegexBaselineEval.run()
        val fields = listOf(
            "isTransaction", "type", "category", "merchant", "subscription"
        )
        val regexPct = { f: String ->
            regex.perField.first { it.field == f }.let { 100.0 * it.correct / it.total }
        }
        println()
        println("===== MODEL vs NO MODEL, through the production pipeline =====")
        println(String.format("%-16s %10s %10s %8s", "field", "regex-only", "with model", "delta"))
        fields.forEach { f ->
            val r = regexPct(f)
            val m = fused.percent(f)
            println(String.format(java.util.Locale.US, "%-16s %9.1f%% %9.1f%% %+7.1f", f, r, m, m - r))
        }
        println(String.format(java.util.Locale.US, "%-16s %9.1f%% %9.1f%% %+7.1f",
            "exact match", regex.exactMatchPercent, fused.exactPercent,
            fused.exactPercent - regex.exactMatchPercent))
        println("  transactions destroyed by a model veto : ${fused.destroyedTransactions.size}")
        println("  transactions rescued by the model     : ${fused.rescuedTransactions.size}")
        println("===========================================================")
    }
}
