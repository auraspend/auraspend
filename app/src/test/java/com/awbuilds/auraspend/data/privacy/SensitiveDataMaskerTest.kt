package com.awbuilds.auraspend.data.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Privacy contract: real leak patterns found in the production Room database
 * (60 phone numbers, 99 reference ids, a half-revealed account "40213377XXXX")
 * must never survive masking — while amounts, dates and bank-pre-masked
 * card/account fragments stay intact for parsing and UX.
 */
class SensitiveDataMaskerTest {

    @Test
    fun `merchant block-request phone number is masked`() {
        val input = "Not you? SMS BLOCK 4821 to 919900000000"
        val out = SensitiveDataMasker.mask(input)
        assertFalse(out.contains("919900000000"))
        assertTrue(out.contains("BLOCK 4821 to"))
    }

    @Test
    fun `bare ten digit phone is masked`() {
        val out = SensitiveDataMasker.mask("Call support at 9000000042 now")
        assertFalse(out.contains("9000000042"))
    }

    @Test
    fun `upi reference number is masked`() {
        val out = SensitiveDataMasker.mask("Sent Rs.299 UPI Ref no 600000000001 -HDFC")
        assertFalse(out.contains("600000000001"))
        assertTrue(out.contains("Ref"))
    }

    @Test
    fun `utr is masked`() {
        val out = SensitiveDataMasker.mask("towards NEFT by Sender ACME, UTR HDFCH09912345678, HDFC BANK")
        assertFalse(out.contains("HDFCH09912345678"))
        assertTrue(out.contains("UTR"))
    }

    @Test
    fun `folio number is masked`() {
        val out = SensitiveDataMasker.mask("Your SIP Purchase of Rs.999.95 in Folio 30917265")
        assertFalse(out.contains("30917265"))
        // The amount survives.
        assertTrue(out.contains("999.95"))
    }

    @Test
    fun `half revealed account fragment is masked`() {
        val out = SensitiveDataMasker.mask("Transfer from a/c 40213377XXXX successful")
        // The exposed first-8 digits must not survive verbatim.
        assertFalse(out.contains("40213377"))
    }

    @Test
    fun `hash like token is masked`() {
        val out = SensitiveDataMasker.mask("Rs 500 debited at 9f3a1c7b2d8e4f6091a2b3c4d5e6f708 ok")
        assertFalse(out.contains("9f3a1c7b2d8e4f6091a2b3c4d5e6f708"))
    }

    // ─── Things that must SURVIVE ─────────────────────────────────────────────

    @Test
    fun `amounts are preserved`() {
        val input = "An amount of INR 46,800.00 has been credited. Total Avail.bal INR 63120.55"
        val out = SensitiveDataMasker.mask(input)
        assertTrue(out.contains("46,800.00"))
        assertTrue(out.contains("63120.55"))
    }

    @Test
    fun `dates and times are preserved`() {
        val input = "Spent INR 135 Axis Bank Card no. XX4821 05-07-26 10:15:22 IST"
        val out = SensitiveDataMasker.mask(input)
        assertTrue(out.contains("05-07-26"))
        assertTrue(out.contains("10:15:22"))
    }

    @Test
    fun `bank premasked card and account fragments are preserved`() {
        val input = "A/c XXXX6021 linked to card XXXX9083 debited Rs.500. XX9084 credited back."
        val out = SensitiveDataMasker.mask(input)
        assertTrue(out.contains("XXXX6021"))
        assertTrue(out.contains("XXXX9083"))
        assertTrue(out.contains("XX9084"))
    }

    @Test
    fun `ifsc code is preserved for bank detection`() {
        val out = SensitiveDataMasker.mask("NEFT IFSC HDFC0000240 sender info")
        assertTrue(out.contains("HDFC0000240"))
    }

    @Test
    fun `masking is idempotent`() {
        val input = "UPI Ref no 600000000001 call 9000000042 folio 30917265"
        val once = SensitiveDataMasker.mask(input)
        val twice = SensitiveDataMasker.mask(once)
        assertEquals(once, twice)
    }

    @Test
    fun `plain transaction text passes through unchanged`() {
        val input = "Rs 250 spent at SWIGGY on 23-08-26. Avl bal Rs 25,000.00 - SBI"
        assertEquals(input, SensitiveDataMasker.mask(input))
    }
}
