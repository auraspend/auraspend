package com.awbuilds.auraspend.data.privacy

/**
 * Removes personally identifiable financial data from message text before it is
 * persisted anywhere (Room queue, transaction notes, LLM prompts, exports).
 *
 * What banks already mask (XX4821, XXXX6021) is preserved as-is — it stays
 * useful for the user to recognise their own cards. What this masker removes:
 *
 *  - phone numbers (+91 / bare 10-12 digit forms, e.g. "SMS BLOCK .. to 91XXXXX")
 *  - labelled reference identifiers (UPI ref / UTR / folio / SI-Hub ids)
 *  - bare long digit runs (half-masked account fragments like "40213377")
 *  - hash-like alphanumeric tokens (accidental reference leaks)
 *
 * Amounts (contain , or .), dates/times (short separator groups) and short OTP-style
 * digits are deliberately left intact so parsing and UX keep working.
 */
object SensitiveDataMasker {

    // ── Phones: +91XXXXXXXXXX / 91XXXXXXXXXX / bare 10-digit starting 6-9 ──
    private val phoneWithCountry = Regex("""(?<![\dA-Za-z])(?:\+?91[- ]?)?[6-9]\d{9}(?![\d])""")
    private val phoneTwelveDigit = Regex("""(?<![\dA-Za-z])91[6-9]\d{9}(?![\d])""")

    // ── Labelled references: "UPI Ref no 600000000001", "UTR HDFCH00…", "Folio 30917265" ──
    private val labelledRef = Regex(
        """(?i)\b((?:upi\s+)?ref(?:\s*no)?\.?|utr|folio|si-?hub\s+id|mandate\s+id)\s*[:#]?\s*([A-Z0-9]{7,})"""
    )

    // ── Bare digit runs ≥7 without separators (amounts use , or . and survive).
    // Not preceded by a letter: digit runs glued after letters are identifier codes
    // (IFSC "HDFC0000240") that must stay readable for bank detection.
    private val bareLongDigitRun = Regex("""(?<![\dA-Za-z,.+-])(\d{7,})(?![\d,.])""")

    // ── Hash-like tokens ≥16 chars mixing letters & digits ("9f3a1c7b2d8e4f60…") ──
    private val hashLikeToken = Regex("""(?<![\w])([A-Za-z0-9]*(?:\d[A-Za-z]|[A-Za-z]\d)[A-Za-z0-9]{14,})(?![\w])""")

    /** Max characters preserved at each end of a masked value. */
    private const val KEEP_ENDS = 2

    fun mask(text: String): String {
        if (text.isBlank()) return text

        var out = text

        out = out.replace(phoneTwelveDigit) { m -> partial(m.value) }
        out = out.replace(phoneWithCountry) { m ->
            // Don't double-mask amounts that happen to be 10 digits with separators —
            // the lookarounds already exclude adjacent separators/digits.
            partial(m.value)
        }
        out = out.replace(labelledRef) { m ->
            "${m.groupValues[1]} ${partial(m.groupValues[2])}"
        }
        out = out.replace(hashLikeToken) { m -> partial(m.groupValues[1]) }
        out = out.replace(bareLongDigitRun) { m -> partial(m.groupValues[1]) }

        return out
    }

    /** Keeps the first and last [KEEP_ENDS] characters; the rest become bullets. */
    private fun partial(value: String): String {
        if (value.length <= KEEP_ENDS * 2) return "•".repeat(value.length)
        val head = value.take(KEEP_ENDS)
        val tail = value.takeLast(KEEP_ENDS)
        return head + "•".repeat(value.length - KEEP_ENDS * 2) + tail
    }
}
