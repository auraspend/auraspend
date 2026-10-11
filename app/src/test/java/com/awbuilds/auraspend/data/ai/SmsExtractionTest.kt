package com.awbuilds.auraspend.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the meaning of [SmsExtraction.isTransactionProbability].
 *
 * The field is what licenses `AiSignalFusion` to *delete* a row rather than
 * mis-file it, so its polarity is load-bearing. The shipped encoder used to
 * report the raw probability of the transaction class, which is below 0.5 on
 * every negative verdict — so inside the `!isTransaction` branch the 0.9 discard
 * gate could never fire. The gate was dead code in production, and the recorded
 * "0 of 46 destroyed" result came from it never being consulted rather than from
 * the encoder being trustworthy.
 */
class SmsExtractionTest {

    @Test
    fun `a negative verdict is scored by how sure the runtime is of that verdict`() {
        // 0.9 confident that this is NOT a transaction. Reported raw, 0.1 would
        // read as "90% unsure" and refuse the discard the runtime actually wants.
        assertEquals(0.9f, SmsExtraction.verdictConfidence(false, 0.1f), 1e-6f)
        assertEquals(0.98f, SmsExtraction.verdictConfidence(false, 0.02f), 1e-6f)
        assertEquals(0.51f, SmsExtraction.verdictConfidence(false, 0.49f), 1e-6f)
    }

    @Test
    fun `a positive verdict is scored by the probability of the transaction class`() {
        assertEquals(0.98f, SmsExtraction.verdictConfidence(true, 0.98f), 1e-6f)
        assertEquals(0.51f, SmsExtraction.verdictConfidence(true, 0.51f), 1e-6f)
    }

    @Test
    fun `the mapping is continuous at the decision boundary`() {
        // A verdict that barely won must be barely confident, in both directions,
        // or the floor and the discard gate disagree about where the boundary is.
        assertEquals(0.5f, SmsExtraction.verdictConfidence(true, 0.5f), 1e-6f)
        assertEquals(0.5f, SmsExtraction.verdictConfidence(false, 0.5f), 1e-6f)
    }
}
