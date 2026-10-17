package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the provider-query markers. The scan used to filter the inbox with 22
 * English body tokens, so a registered bank's "₹500 sent to …" never reached
 * the parser at all. These are the markers that close the recall hole; a
 * removal here should be a deliberate, measured change.
 */
class SmsIngestKeywordsTest {

    @Test
    fun `body markers cover rupee-only transfers and foreign currency`() {
        val markers = SmsAutoClassifier.bankKeywords
        listOf("₹", "sent", "received", "transferred", "USD", "AED", "Dr.", "Cr.")
            .forEach { marker ->
                assertTrue("missing ingest marker '$marker'", marker in markers)
            }
    }

    @Test
    fun `sender markers cover banks outside the old sender regex`() {
        val markers = SmsAutoClassifier.bankSenderKeywords
        listOf("HDFC", "CANARA", "JUPITER", "JIOPAY", "PLUXEE", "IPPB")
            .forEach { marker ->
                assertTrue("missing sender marker '$marker'", marker in markers)
            }
    }
}
