package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * High-precision parser for Indian bank / wallet transaction SMS.
 *
 * Pipeline: normalise -> detect bank -> detect type (credit/debit lexicon)
 * -> detect amount (position-aware, balance-aware) -> detect merchant
 * -> detect timestamp -> keyword category -> confidence.
 *
 * Design rules:
 *  - Every regex is precompiled (parser runs per-SMS on background threads).
 *  - Amount selection prefers the number closest to a transaction verb and
 *    ignores numbers inside balance / limit / due-date boilerplate.
 *  - Merchants are aggressively cleaned: VPA handles, POS "at X", card
 *    "on X", reference noise, all-caps spam and account fragments are all
 *    normalised or rejected.
 */
object BankMessageParser {

    // ─────────────────────────────────────────────────────────────────────────
    // Bank identification (ordered longest-first so specific names win)
    // ─────────────────────────────────────────────────────────────────────────

    private val bankPatterns: List<Pair<Regex, String>> = listOf(
        Regex("HDFC\\s*Bank|HDFCB|\\bHDFC\\b") to "HDFC Bank",
        Regex("ICICI\\s*Bank|ICICIB|\\bICICI\\b") to "ICICI Bank",
        Regex("State\\s*Bank\\s*of\\s*India|\\bSBI\\b|SBIC|\\bSBIN\\b") to "SBI",
        Regex("Axis\\s*Bank|AXISB|\\bAXIS\\b") to "Axis Bank",
        Regex("Kotak\\s*(Mahindra)?\\s*Bank|KOTAKB|\\bKOTAK\\b") to "Kotak Mahindra",
        Regex("Yes\\s*Bank|YESB|\\bYESBnk\\b") to "Yes Bank",
        Regex("Punjab\\s*National|\\bPNB\\b|PUNBNB") to "PNB",
        Regex("Canara\\s*Bank|\\bCANARA\\b|CANBNB") to "Canara Bank",
        Regex("Bank\\s*of\\s*Baroda|\\bBOB\\b|BARBINB") to "Bank of Baroda",
        Regex("IDFC\\s*FIRST|IDFCB|\\bIDFC\\b") to "IDFC First",
        Regex("IndusInd\\s*Bank|INDUSB|\\bINDUSIND\\b") to "IndusInd Bank",
        Regex("Union\\s*Bank\\s*of\\s*India|UNIONBNK|\\bUBOI\\b") to "Union Bank",
        Regex("Indian\\s*Bank|INDBNK|\\bIOB\\b") to "Indian Bank",
        Regex("IDBI\\s*Bank|\\bIDBI\\b") to "IDBI Bank",
        Regex("Federal\\s*Bank|FEDERAL|\\bFBIL\\b") to "Federal Bank",
        Regex("RBL\\s*Bank|\\bRBL\\b") to "RBL Bank",
        Regex("AU\\s*(Small\\s*Finance)?\\s*Bank|AUBANK") to "AU Small Finance Bank",
        Regex("Bandhan\\s*Bank|\\bBANDHAN\\b") to "Bandhan Bank",
        Regex("Paytm\\s*Payments?\\s*Bank|PAYTM") to "Paytm Payments Bank",
        Regex("Airtel\\s*Payments?\\s*Bank|AIRTEL") to "Airtel Payments Bank",
        Regex("Jupiter|CSB\\s*Bank") to "Jupiter",
        Regex("\\bFi\\s*(Money|Federal)?\\b") to "Fi Money",
        Regex("\\bNiyo\\b") to "Niyo",
        Regex("Citibank|\\bCITI\\b") to "Citibank",
        Regex("HSBC") to "HSBC",
        Regex("Standard\\s*Chartered|\\bSCBL\\b") to "Standard Chartered",
        Regex("Deutsche\\s*Bank") to "Deutsche Bank",
        Regex("Barclays") to "Barclays",
        Regex("Nainital\\s*Bank") to "Nainital Bank",
        Regex("Karnataka\\s*Bank") to "Karnataka Bank",
        Regex("Karur\\s*Vysya") to "Karur Vysya Bank",
        Regex("South\\s*Indian\\s*Bank") to "South Indian Bank",
        Regex("DCB\\s*Bank") to "DCB Bank",
        Regex("Saraswat\\s*Bank") to "Saraswat Bank"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction-type lexicons
    // ─────────────────────────────────────────────────────────────────────────

    /** Words that mark money leaving the account. */
    private val debitWords = listOf(
        "debited", "debit", "withdrawn", "withdrawal", "atm withdrawal",
        "spent", "spent using", "paid", "payment made", "purchased", "purchase",
        "sent", "transferred to", "transfer to", "auto pay", "autopay", "auto debit",
        "autodebit", "nach debit", "charged", "bill payment", "recharge of"
    )

    /** Words that mark money entering the account. */
    private val creditWords = listOf(
        "credited", "credit of", "received", "deposited", "deposit of",
        "refund", "refunded", "cashback", "cash back", "reversal", "reversed",
        "inward", "salary", "sal cr", "interest credited", "dividend", "payout"
    )

    private val debitRegexes = debitWords.map { Regex("\\b${it.replace(" ", "\\s+")}\\b", RegexOption.IGNORE_CASE) }
    private val creditRegexes = creditWords.map { Regex("\\b${it.replace(" ", "\\s+")}\\b", RegexOption.IGNORE_CASE) }

    /** "Rs 100 Dr", "INR 250 Cr." ledger-style suffixes. */
    private val drSuffix = Regex("""\bDr\.?(?=\s|$|\d)""", RegexOption.IGNORE_CASE)
    private val crSuffix = Regex("""\bCr\.?(?=\s|$|\d)""", RegexOption.IGNORE_CASE)

    /**
     * True when [message] names money *actually* moving — a completed debit or
     * credit — in the same vocabulary the parser uses to decide the type.
     *
     * Shared on purpose. `SmsAutoClassifier` uses it to rescue a real transaction
     * from its OTP/alert filter, and `SmsPipelineProcessor` uses it to decide
     * whether an unreadable message should be recorded for the user. Both carried
     * their own six-word copy (`debited|credited|spent|paid|transferred|withdrawn`),
     * which disagreed with the parser exactly where it mattered: a deposit or a
     * refund with a balance tail ("Rs 10,000 has been deposited to A/c XX123.
     * Available balance is Rs 45,200") matched a balance-alert pattern, contained
     * no word from the six, and was dropped *before* the parser that reads
     * "deposited" correctly ever ran. That is the "loses the user's money
     * silently" case the OTP/alert invariant exists to prevent — and the invariant
     * as written (no amount **and** no movement verb) was being violated, because
     * that message has both.
     */
    fun hasMovementVerb(message: String): Boolean =
        debitRegexes.any { it.containsMatchIn(message) } ||
            creditRegexes.any { it.containsMatchIn(message) }

    /** A completed debit/credit must not be a failure / decline notice. */
    private val failureRegex = Regex(
        """\b(failed|failure|declined|decline|denied|unsuccessful|could not be processed|not (?:been )?completed|timed? ?out)\b""",
        RegexOption.IGNORE_CASE
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Amount detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Currency-prefixed amounts: "Rs 1,23,456.78", "Rs.1234/-", "INR 499",
     * "₹ 59", "rs 20". Indian digit grouping (2-digit middle groups) allowed.
     * Lookbehind keeps "XX1234" from ever being read as an amount.
     */
    private val prefixedAmount =
        Regex("""(?<![A-Za-z0-9])(?:₹|\brs\.?|\binr\b)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    /** Suffix form: "1,234.56 Rs", "500/- debited". */
    private val suffixedAmount =
        Regex("""(?<![A-Za-z0-9.,])([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:/-|\brs\.?\b|\binr\b|₹)""", RegexOption.IGNORE_CASE)

    /** Bare decimal amounts like "amounting to 1500.00" (last-resort). */
    private val bareAmount = Regex("""\b([1-9][0-9]{2,9}\.[0-9]{2})\b""")

    /** Boilerplate whose numbers must never become the transaction amount. */
    private val balanceContext = Regex(
        """(?i)\b(?:avl|available|avail|a/c\s*bal|bal|balance|limit|outstanding|total\s*due|min(?:imum)?\s*due|due|credit\s*limit|remaining|utilised|utilized|reward|points|cash\s*limit)[a-z\s.:-]{0,18}$"""
    )

    private const val AMOUNT_PROXIMITY_CHARS = 60

    // ─────────────────────────────────────────────────────────────────────────
    // Merchant detection
    // ─────────────────────────────────────────────────────────────────────────

    /** UPI virtual payment addresses: swiggy@ybl, john.doe@oksbi, 123456789@paytm. */
    private val vpaPattern = Regex("""([a-zA-Z0-9](?:[a-zA-Z0-9._-]{1,40}))@([a-zA-Z]{2,20})""")

    /**
     * Axis/ICICI card style with LITERAL NEWLINES (real device SMS):
     * ```
     * Spent INR 135
     * Axis Bank Card no. XX4821
     * 05-07-26 10:15:22 IST
     * Tulip
     * Avl Limit: INR 42731.09
     * ```
     * The merchant is the standalone line right after the "...IST" line.
     */
    private val istLinePattern = Regex(
        """(?im)^[^\n]*\b(?:IST|GMT)[ \t]*$\n[ \t]*([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)[ \t]*$"""
    )

    private val merchantPatterns: List<Regex> = listOf(
        // "... at STARBUCKS on 23-08 ..." / "purchase at X" / "withdrawal at ATM NAME"
        Regex("""\bat\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|via|using|dated|ref|no|card|avail|has|is|for)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // Axis/ICICI card style: "Spent INR 500 ... 05-07-26 10:15:22 IST TULIP MART"
        Regex("""\b(?:IST|GMT)\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|via|using|dated|ref|no|card|avail|has|is|for|not)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // "... to AMAZON PAY ... " / "sent to X"
        Regex("""\bto\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|via|using|dated|ref|no|avail|has|is|for|towards)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // credits: "from JOHN DOE ref..."
        Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|ref|no|dated|has|is|avail)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // Canara-style NEFT credits: "... by Sender ACME SOLUTIONS LIMITED, IFSC ..."
        Regex("""\bby\s+sender\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s*,|\s+IFSC\b|\s+A/c\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // "... towards BIG BAZAAR bangalore ..."
        Regex("""\btowards\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|ref|no|dated|has|is)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // explicit labels: "merchant: ZOMATO", "at merchant SWIGGY"
        Regex("""(?:merchant|merchants?)\s*[:\-]?\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+[A-Z]|\s+ref\b|[.,]|$)""")
    )

    /** Tokens that disqualify an extracted merchant string. */
    private val merchantStopPhrases = listOf(
        "your", "you", "the", "this", "a/c", "account", "acct", "bank", "branch",
        "customer", "dear", "card", "upi", "ref", "transaction", "txn", "info",
        "avail", "balance", "call", "sms", "block", "otp", "kyc", "imps", "neft",
        "rtgs", "ach", "nach", "valid", "login", "click", "www", "http",
        // Boilerplate fragments observed leaking into production data.
        "enjoy", "uninterrupted", "pay your", "bill pay", "installment", "scheme",
        "investor", "report", "fraud", "cyber", "scam", "phishing", "statement",
        "due", "overdue", "reminder", "offer", "win", "download", "subscribe"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Date & time parsing
    // ─────────────────────────────────────────────────────────────────────────

    private val dateTimePatterns: List<Regex> = listOf(
        // ISO first so "2026-08-23T14:35" is never misread by the numeric pattern below.
        Regex("""\b(\d{4}-\d{2}-\d{2})(?:[T ](\d{1,2}:\d{2}(?::\d{2})?)\s*([AaPp][Mm])?)?\b"""),
        Regex("""(?<![0-9/.-])(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4})(?:\s+(\d{1,2}:\d{2}(?::\d{2})?)\s*([AaPp][Mm])?)?(?![0-9])"""),
        Regex("""\b(\d{1,2}[ -][A-Za-z]{3,9}[ -,]\d{2,4})(?:\s+(\d{1,2}:\d{2}(?::\d{2})?)\s*([AaPp][Mm])?)?\b""")
    )

    private val dateFormats = listOf(
        "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
        "dd/MM/yy", "dd-MM-yy", "d M yy",
        "d MMM yyyy", "d MMM yy", "d MMMM yyyy", "dd-MMM-yyyy",
        "yyyy-MM-dd"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun parse(rawMessage: String): ParsedBankMessage {
        val message = rawMessage.trim()
        if (message.isEmpty()) {
            return ParsedBankMessage(rawMessage = message, confidence = 0f)
        }

        val bankName = detectBank(message)
        val type = detectType(message)
        val amount = detectAmount(message)
        val merchant = detectMerchant(message)
        val date = detectDateTime(message)
        val categoryId = categoryIdFor(message, merchant, type)
        val confidence = calculateConfidence(message, amount, type, bankName, merchant)

        return ParsedBankMessage(
            amount = amount,
            type = type,
            merchant = merchant,
            bankName = bankName,
            date = date,
            note = merchant ?: truncateMessage(message),
            categoryId = categoryId,
            confidence = confidence,
            rawMessage = message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detection internals
    // ─────────────────────────────────────────────────────────────────────────

    internal fun detectBank(message: String): String? =
        bankPatterns.firstOrNull { it.first.containsMatchIn(message) }?.second

    internal fun detectType(message: String): TransactionType? {
        if (failureRegex.containsMatchIn(message)) return null

        val debitScore = debitRegexes.count { it.containsMatchIn(message) } +
            (if (drSuffix.containsMatchIn(message)) 1 else 0)
        val creditScore = creditRegexes.count { it.containsMatchIn(message) } +
            (if (crSuffix.containsMatchIn(message)) 1 else 0)

        return when {
            debitScore > 0 && creditScore == 0 -> TransactionType.EXPENSE
            creditScore > 0 && debitScore == 0 -> TransactionType.INCOME
            else -> null
        }
    }

    internal fun detectAmount(message: String): Double? {
        data class Candidate(val value: Double, val start: Int, val end: Int)

        val candidates = mutableListOf<Candidate>()

        fun collect(regex: Regex) {
            for (m in regex.findAll(message)) {
                val value = parseIndianNumber(m.groupValues[1]) ?: continue
                if (value <= 0.0 || value >= 100_000_000.0) continue
                candidates.add(Candidate(value, m.range.first, m.range.last))
            }
        }

        collect(prefixedAmount)
        collect(suffixedAmount)
        if (candidates.isEmpty()) collect(bareAmount)

        if (candidates.isEmpty()) return null

        // Drop candidates sitting in balance/limit boilerplate.
        val usable = candidates.filter { c ->
            val lookBehindStart = maxOf(0, c.start - 30)
            !balanceContext.containsMatchIn(message.substring(lookBehindStart, c.start))
        }.ifEmpty { candidates } // all-in-boilerplate edge case: fall back to all

        // Prefer the candidate nearest to a debit/credit verb.
        val verbs = (debitRegexes + creditRegexes).mapNotNull { r ->
            r.find(message)?.range
        }

        val best = usable.minByOrNull { c ->
            val distance = verbs.minOfOrNull { v ->
                when {
                    c.end < v.first -> v.first - c.end
                    c.start > v.last -> c.start - v.last
                    else -> 0 // overlapping the verb phrase
                }
            } ?: Int.MAX_VALUE
            distance
        } ?: return null

        // Only trust verb-proximity when a verb exists at all.
        if (verbs.isEmpty()) {
            // No explicit verb: take the first non-boilerplate candidate (legacy behaviour).
            return usable.firstOrNull()?.value ?: best.value
        }
        val chosenDistance = verbs.minOf { v ->
            when {
                best.end < v.first -> v.first - best.end
                best.start > v.last -> best.start - v.last
                else -> 0
            }
        }
        return if (chosenDistance <= AMOUNT_PROXIMITY_CHARS) best.value
        else usable.first().value
    }

    internal fun detectMerchant(message: String): String? {
        // 1. UPI VPA wins outright — most precise identity in the message.
        vpaPattern.find(message)?.let { vpa ->
            val handle = vpa.groupValues[1]
            val cleaned = cleanMerchant(handle) ?: return@let
            // Skip VPAs that are just long account/reference numbers.
            if (!cleaned.any { it.isLetter() }) return@let
            if (cleaned.length >= 3) return prettifyVpa(cleaned)
        }

        // 2. Multiline card SMS: merchant on its own line after "...IST".
        istLinePattern.find(message)?.let { m ->
            val name = cleanMerchant(m.groupValues[1])
            if (name != null && name.length in 2..40 && looksLikeMerchant(name)) return name
        }

        for (pattern in merchantPatterns) {
            val match = pattern.find(message) ?: continue
            val name = cleanMerchant(match.groupValues[1]) ?: continue
            if (name.length in 2..40 && looksLikeMerchant(name)) return name
        }
        return null
    }

    internal fun detectDateTime(message: String): LocalDateTime? {
        for (pattern in dateTimePatterns) {
            val m = pattern.find(message) ?: continue
            val dateStr = m.groupValues[1].trim()
            val timeStr = m.groups[2]?.value?.trim()
            val meridiem = m.groups[3]?.value?.trim()

            val date = parseDate(dateStr) ?: continue
            val time = parseTime(timeStr, meridiem)

            return if (time != null) LocalDateTime.of(date, time) else date.atTime(12, 0)
        }

        // Relative-day phrases.
        val lower = message.lowercase(Locale.ENGLISH)
        val now = LocalDateTime.now()
        return when {
            "yesterday" in lower -> now.minusDays(1)
            "today" in lower -> now
            else -> null
        }
    }

    private fun parseDate(raw: String): LocalDate? {
        val normalized = raw.replace(',', ' ').replace(Regex("\\s+"), " ").trim()
        for (fmt in dateFormats) {
            try {
                val d = LocalDate.parse(
                    normalized,
                    DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                )
                // Two-digit years: treat 00-68 as 2000-2068 (java.time default is fine).
                if (d.year in 1990..2100) return d
            } catch (_: DateTimeParseException) { /* try next */ }
        }
        return null
    }

    private fun parseTime(timeStr: String?, meridiem: String?): LocalTime? {
        if (timeStr.isNullOrBlank()) return null
        val base = try {
            LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm:ss", Locale.ENGLISH))
        } catch (_: DateTimeParseException) {
            try {
                LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH))
            } catch (_: DateTimeParseException) {
                return null
            }
        }
        return when (meridiem?.uppercase(Locale.ENGLISH)) {
            "PM" -> if (base.hour < 12) base.plusHours(12) else base
            "AM" -> if (base.hour == 12) base.minusHours(12) else base
            else -> base
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Parses "12,34,567.89"-style numbers (any comma grouping) safely. */
    private fun parseIndianNumber(raw: String): Double? =
        raw.replace(",", "").toDoubleOrNull()

    private fun cleanMerchant(raw: String): String? {
        val s = raw.trim()
            .replace(Regex("""[*#|]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', ',', '-', ':')
            .trim()
        if (s.isEmpty()) return null

        val lower = s.lowercase(Locale.ENGLISH)
        if (merchantStopPhrases.any { lower.startsWith(it) }) return null
        return s
    }

    private fun looksLikeMerchant(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.none { it.isLetter() }) return false
        if (Regex("^\\d+$").containsMatchIn(name)) return false
        // Reject reference/account-number fragments and address tails.
        if (Regex("""\d{3,}""").containsMatchIn(name)) return false
        // Reject hash-like identifiers ("9f3a1c7b2d8e4f60..." leaked into prod data).
        if (Regex("""(?i)[0-9a-f]{10,}""").containsMatchIn(name)) return false
        // Reject date fragments like "23 08" or "15 Aug".
        if (Regex("""^\d{1,2}[ /.-]\d{1,2}([ /.-]\d{2,4})?$""").containsMatchIn(name)) return false
        return true
    }

    private fun prettifyVpa(handle: String): String {
        // "john.doe" -> "John Doe"; "swiggy" -> "Swiggy"
        val parts = handle.split('.', '_', '-')
        val pretty = parts.filter { it.isNotBlank() }.joinToString(" ") { p ->
            p.replaceFirstChar { it.uppercase(Locale.ENGLISH) }
        }
        return pretty.ifBlank { handle }
    }

    private fun truncateMessage(message: String): String {
        val cleaned = message.replace(Regex("""\s+"""), " ").trim()
        return if (cleaned.length > 120) cleaned.take(120) + "..." else cleaned
    }

    private fun calculateConfidence(
        message: String,
        amount: Double?,
        type: TransactionType?,
        bankName: String?,
        merchant: String?
    ): Float {
        if (amount == null || type == null) {
            return if (amount != null || type != null) 0.35f else 0.15f
        }
        var score = 0.70f
        if (bankName != null) score += 0.10f
        if (merchant != null) score += 0.10f
        // Date/time presence adds signal.
        if (dateTimePatterns.any { it.containsMatchIn(message) }) score += 0.05f
        return score.coerceAtMost(0.97f)
    }

    /** Category resolution shared by manual paste flow and auto pipeline. */
    internal fun categoryIdFor(message: String, merchant: String?, type: TransactionType?): String? {
        // 1. Curated merchant-database lookup (highest precision).
        if (merchant != null) {
            getCategoryIdForKeyword(merchant)?.let { return it }
        }
        // 2. Keyword scan over the message body.
        messageKeywordCategory(message)?.let { return it }
        // 3. Income without stronger signals defaults to salary only when clearly payroll.
        if (type == TransactionType.INCOME &&
            Regex("""salary|payroll|wages""", RegexOption.IGNORE_CASE).containsMatchIn(message)
        ) return "cat_salary"
        return null
    }

    /** Word-boundary keyword scan across the whole message (shared logic). */
    private fun messageKeywordCategory(message: String): String? =
        keywordCategoryFor(message)
}

