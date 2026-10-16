package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
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
        Regex("HDFC\\s*Mutual\\s*Fund|HDFCMF") to "HDFC Mutual Fund",
        Regex("Navi\\s*Mutual\\s*Fund|NAVAMC") to "Navi Mutual Fund",
        Regex("HDFC\\s*Bank|HDFCB|\\bHDFC\\b") to "HDFC Bank",
        Regex("ICICI\\s*Bank|ICICIB|\\bICICI\\b") to "ICICI Bank",
        Regex("State\\s*Bank\\s*of\\s*India|\\bSBI\\b|SBIC|\\bSBIN\\b") to "SBI",
        Regex("Axis\\s*Bank|AXISB|\\bAXIS\\b") to "Axis Bank",
        Regex("Kotak\\s*(Mahindra)?\\s*Bank|KOTAKB|\\bKOTAK\\b") to "Kotak Mahindra",
        Regex("Yes\\s*Bank|YESB|\\bYESBnk\\b") to "Yes Bank",
        Regex("Punjab\\s*National|\\bPNB\\b|PUNBNB") to "PNB",
        Regex("Canara\\s*Bank|\\bCANARA\\b|CANBNB") to "Canara Bank",
        Regex("Central\\s*Bank(?:\\s*of\\s*India)?|CENTBK|\\bCBOI\\b") to "Central Bank of India",
        Regex("Bank\\s*of\\s*Baroda|\\bBOB\\b|BARBINB") to "Bank of Baroda",
        Regex("Bank\\s*of\\s*India|BOIBNK|BOIIND") to "Bank of India",
        Regex("IDFC\\s*FIRST|IDFCB|\\bIDFC\\b") to "IDFC First",
        Regex("IndusInd\\s*Bank|INDUSB|\\bINDUSIND\\b") to "IndusInd Bank",
        Regex("Union\\s*Bank\\s*of\\s*India|UNIONBNK|\\bUBOI\\b") to "Union Bank",
        Regex("Indian\\s*Overseas|IOBCHN|\\bIOB\\b") to "Indian Overseas Bank",
        Regex("Indian\\s*Bank|INDBNK") to "Indian Bank",
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
        Regex("Saraswat\\s*Bank") to "Saraswat Bank",
        Regex("UCO\\s*Bank|UCOBNK") to "UCO Bank",
        Regex("City\\s*Union\\s*Bank|CUBANK|CUBLTD") to "City Union Bank",
        Regex("Dhanlaxmi|DHANBK") to "Dhanlaxmi Bank",
        Regex("Equitas") to "Equitas Small Finance Bank",
        Regex("Punjab\\s*&\\s*Sind|PSBANK") to "Punjab & Sind Bank",
        Regex("Utkarsh|UTKSFB") to "Utkarsh Small Finance Bank",
        Regex("JKBANK|JK\\s*Bank") to "JK Bank",
        Regex("Department\\s+of\\s+Post|DOPBNK") to "Department of Post",
        Regex("American\\s*Express|AMEX") to "American Express",
        Regex("OneCard|ONECRD") to "OneCard",
        Regex("SLICEIT|SLCEIT|SLCBNK") to "Slice",
        Regex("\\bCRED\\b|CREDIN") to "CRED",
        Regex("Pluxee|PLUXEE") to "Pluxee",
        Regex("Jio\\s*Payments\\s*Bank|JIOPBS") to "Jio Payments Bank",
        Regex("NSDL\\s*Payments|NSDLPB") to "NSDL Payments Bank",
        Regex("Cashfree|CSHFRE") to "Cashfree",
        Regex("Kerala\\s*Gramin\\s*Bank|KGBANK") to "Kerala Gramin Bank",
        Regex("Kerala\\s*Bank|KELBNK") to "Kerala Bank",
        Regex("India\\s*Post\\s*Payments|IPBMSG") to "India Post Payments Bank",
        Regex("DBS\\s*Bank|DBSBNK") to "DBS Bank",
        Regex("Jana\\s*Small\\s*Finance|JANABK") to "Jana Small Finance Bank"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction-type lexicons
    // ─────────────────────────────────────────────────────────────────────────

    /** Words that mark money leaving the account. */
    private val debitWords = listOf(
        "debited", "debit", "withdrawn", "withdrawal", "atm withdrawal",
        "spent", "spent using", "used at", "paid", "payment made", "purchased", "purchase",
        "sent", "transferred to", "transfer to", "auto pay", "autopay", "auto debit",
        "autodebit", "nach debit", "charged", "deducted", "bill payment", "recharge of"
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
     * Balance annotations like "Aval Bal Rs.27000.00 CR." — the CR/DR there
     * describes the BALANCE, not the transaction, but the ledger-suffix scan
     * counted it as the opposite direction and made the type ambiguous
     * (measured on PNB's "Debited with Rs.5000.00 … Aval Bal Rs.27000.00 CR.",
     * which came back typeless). Masked out before scoring.
     */
    private val balanceCrDrAnnotation = Regex(
        """(?i)\b(?:bal|balance)\b[\s\S]{0,30}?\b(?:cr|dr)\.?\b"""
    )

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

    /**
     * True when [message] names money *leaving* the account — the debit half of
     * [hasMovementVerb].
     *
     * The distinction matters exactly once, and it is the difference between a
     * phantom and a lost purchase: informational messages say "reward points have
     * been **credited**" or quote an amount in an offer, and those words are in
     * the credit lexicon, so the amount+verb rescue would keep them. A message
     * that says money was **spent, paid, debited or purchased** is a real
     * purchase no matter what else it mentions ("…spent at X. You have earned 15
     * reward points."), so the informational vetoes stand down for it.
     */
    fun hasDebitVerb(message: String): Boolean =
        debitRegexes.any { it.containsMatchIn(message) }

    /** A completed debit/credit must not be a failure / decline notice. */
    private val failureRegex = Regex(
        """\b(failed|failure|declined|decline|denied|unsuccessful|could not be processed|not (?:been )?completed|timed? ?out)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Phrases whose literal words make both lexicons fire and the ambiguity rule
     * return no type, so the row is skipped even though it is a clean credit.
     *
     * Found on real messages (2026-10-13): a Kotak UPI reversal —
     * "Rs.20.00 is credited ... as a reversal of debit transaction" — scores
     * `credited` + `reversal` as credit and the bare word `debit` as debit, and
     * "Payment of Rs X has been received ... through Bharat Bill Payment System"
     * hits the `bill payment` debit phrase inside the BBPS brand. Both were
     * skipped for want of a type. Neutralised before scoring — the reversal
     * phrase becomes an explicit credit, the brand becomes its abbreviation.
     */
    private val reversalOfDebitPhrase = Regex(
        """\breversal\s+of\s+(?:a\s+|the\s+|your\s+)?(?:debit|txn|transaction|payment|transfer)\b""",
        RegexOption.IGNORE_CASE
    )
    private val bbpsBrandPhrase = Regex(
        """\bBharat\s+Bill\s+Payment\s+System\b""",
        RegexOption.IGNORE_CASE
    )

    /** "…has been credited to the beneficiary account" — an outgoing transfer. */
    private val creditedToBeneficiaryPhrase = Regex(
        """(?i)\bcredited\s+to\s+the\s+beneficiary(?:'s)?\s+account\b"""
    )

    /** "; <PAYEE> credited" — the counterparty clause of a UPI debit message. */
    private val counterpartyCreditedPhrase = Regex(
        """(?i);\s*[A-Za-z][A-Za-z0-9&.'\- ]{1,38}?\s+credited\b"""
    )

    /**
     * Passive mail phrasing: "Statement is sent to <email>", "ebill has been
     * sent to <email>". The literal "sent" is a debit word, and it booked
     * statements and e-bills as expenses (owner's inbox, 2026-10-13).
     * Active transfers ("Sent Rs.51 … to X", "Rs 500 sent to JOHN") have no
     * is/was/has-been before "sent" and are untouched.
     */
    private val passiveSentToPhrase = Regex(
        """(?i)\b(?:is|are|was|were|has\s+been|have\s+been|had\s+been)\s+sent\s+to\b"""
    )

    /** "If not received, SMS ENRS" — mail boilerplate, not a credit. */
    private val ifNotReceivedPhrase = Regex("""(?i)\bif\s+not\s+received\b""")

    /**
     * A biller confirming a payment the user made: "Payment of Rs 749.50 for
     * your JioHome connection … has been received on 01-Aug-26." The literal
     * "received" made the user's own payment read as INCOME (measured on the
     * owner's inbox, 2026-10-13 — nine utility payments booked as money in).
     * Rewritten to a debit verb so the direction is right; the bank-side debit
     * announced twice is then caught by the duplicate fingerprint, which
     * exists for exactly this bank+biller pairing. The credit-card form
     * ("… has been received ON YOUR ICICI Bank Credit Card") is deliberately
     * excluded — there the received payment reduces the card, which this app
     * records as income.
     */
    private val billerReceiptPhrase = Regex(
        """(?i)\bpayment\s+of\b[\s\S]{0,120}?\bhas\s+been\s+received\b(?!\s+on\s+your\b)"""
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Amount detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Currency-prefixed amounts: "Rs 1,23,456.78", "Rs.1234/-", "INR 499",
     * "₹ 59", "rs 20". Indian digit grouping (2-digit middle groups) allowed.
     * Lookbehind keeps "XX1234" from ever being read as an amount.
     * The integer part is optional on purpose — banks do print "Rs..6 Credited"
     * and "₹ .50", and skipping those let a later balance figure win the pick.
     */
    private val prefixedAmount =
        Regex("""(?<![A-Za-z0-9])(?:₹|\brs\.?|\binr\b)\s*((?:[0-9][0-9,]*(?:\.[0-9]{1,2})?|\.\d{1,2}))""", RegexOption.IGNORE_CASE)

    /** Suffix form: "1,234.56 Rs", "500/- debited". */
    private val suffixedAmount =
        Regex("""(?<![A-Za-z0-9.,])([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:/-|\brs\.?\b|\binr\b|₹)""", RegexOption.IGNORE_CASE)

    /** Bare decimal amounts like "amounting to 1500.00" (last-resort). */
    private val bareAmount = Regex("""\b([1-9][0-9]{2,9}\.[0-9]{2})\b""")

    /** Boilerplate whose numbers must never become the transaction amount. */
    private val balanceContext = Regex(
        """(?i)\b(?:avl|available|avail|a/c\s*bal|bal|balance|updated|limit|outstanding|total\s*due|min(?:imum)?\s*due|due|credit\s*limit|remaining|utilised|utilized|reward|points|cash\s*limit)[a-z\s.:-]{0,18}$"""
    )

    /**
     * A credit / available-limit clause and its figure ("Avl Limit: INR
     * 42,731.09", "Available Credit Limit: Rs.3,30,022.99", "Avl Lmt INR
     * 35182.45"). The limit is never the transaction amount. Blanked out —
     * same length, so every collected range stays valid — before candidates
     * are gathered, because the proximity filter falls back to "all
     * candidates" when every number sits in boilerplate: a statement whose
     * only number is the available limit would otherwise book it as a spend.
     */
    private val creditLimitClause = Regex(
        """(?i)\b(?:avl|avail(?:able)?)\.?\s+(?:credit\s+)?(?:limit|lmt)\b\s*(?:is)?\s*:?\s*(?:inr|rs\.?|₹)?\s*[0-9,]*\.?\d+"""
    )

    /** Replaces each match with spaces of equal length so indexes keep working. */
    private fun maskOut(message: String, pattern: Regex): String =
        pattern.replace(message) { " ".repeat(it.value.length) }

    private const val AMOUNT_PROXIMITY_CHARS = 60

    // ─────────────────────────────────────────────────────────────────────────
    // Merchant detection
    // ─────────────────────────────────────────────────────────────────────────

    /** UPI virtual payment addresses: swiggy@ybl, john.doe@oksbi, 123456789@paytm. */
    private val vpaPattern = Regex("""([a-zA-Z0-9](?:[a-zA-Z0-9._-]{1,40}))@([a-zA-Z]{2,20})""")

    /** "…towards IMPS/123456" / "via IMPS/…" — the payment rail, as identity. */
    private val impsRail = Regex("""(?i)\b(?:towards|via)\s+IMPS\b""")

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

    /** "...Info INF*…*IQBO SAL FE…" — a payroll credit's Info clause. */
    private val infoSalaryPattern = Regex("""(?i)\bInfo\b[^.\n]*?\bSAL(?:ARY)?\b""")

    private val merchantPatterns: List<Regex> = listOf(
        // "... at STARBUCKS on 23-08 ..." / "purchase at X" / "withdrawal at ATM NAME"
        Regex("""\bat\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|via|using|dated|ref|no|card|avail|has|is|for)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // Axis/ICICI card style: "Spent INR 500 ... 05-07-26 10:15:22 IST TULIP MART"
        Regex("""\b(?:IST|GMT)\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|via|using|dated|ref|no|card|avail|has|is|for|not)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // Card spends name the merchant after the SECOND "on" (the first is the
        // date): "spent using ICICI Bank Card XX5678 on 06-Sep-25 on Swiggy."
        // Scoped to spend phrasing, and a date-shaped value can never be the
        // merchant — "credited on Tuesday"-style text is out of reach.
        Regex("""\b(?:spent|using|purchas(?:e|ed))\b[^.\n]{0,60}?\bon\s+((?!\d{1,2}[-/ ])[A-Za-z0-9][A-Za-z0-9&.'\- ]{1,38}?)(?=\s*[.,]|\s+(?:Avl|Avail|If|Not|Call|SMS)\b|$)""", RegexOption.IGNORE_CASE),
        // Refunds: "INR 2 from Airport Lounge refunded to your Kotak Credit Card
        // x8848" — the merchant is the party between "from" and "refunded"; the
        // generic "from X on" pattern would swallow the card clause instead.
        Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)\s+refunded\b""", RegexOption.IGNORE_CASE),
        // UPI debits name the payee before "; <PAYEE> credited" — the counterparty
        // of the money, which is the merchant the user recognises:
        // "Acct XX051 debited for Rs 180.00 on 10-Nov-25; MADURAI STORES credited."
        Regex(""";\s*([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)\s+credited\b""", RegexOption.IGNORE_CASE),
        // "... to AMAZON PAY ... " / "sent to X"
        Regex("""\bto\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|via|using|thru|through|dated|ref|no|avail|has|is|for|towards)\b|[/.,]|$)""", RegexOption.IGNORE_CASE),
        // BoB style with a colon: "Rs.29 transferred from A/c ...5494 to:Loan Recovery Fo."
        Regex("""\bto:\s*([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s*[/.,]|$)""", RegexOption.IGNORE_CASE),
        // credits: "from JOHN DOE ref..."
        Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|ref|no|dated|has|is|avail)\b|[/.,]|$)""", RegexOption.IGNORE_CASE),
        // Canara-style NEFT credits: "... by Sender ACME SOLUTIONS LIMITED, IFSC ..."
        Regex("""\bby\s+sender\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s*,|\s+IFSC\b|\s+A/c\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // "<currency> credited by <NAME>": "Rs.2500.00 credited to A/c …
        // via IMPS/518233445566 by JOHN DOE." — the name is the counterparty.
        // Scoped to a preceding "credited" and blocked on currency words so a
        // bare "paid by AUTO-DEBIT"-style sentence can never match.
        Regex("""(?i)\bcredited\b[^.\n]{0,40}?\bby\s+(?!(?:rs|inr)\b|salary\b|[₹])([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:thru|through|via)\b|\s*[.,]|\s+on\b|$)"""),
        // HDFC-style NEFT credits name the remitting organisation as a segment:
        // "...for NEFT Cr-CITI0100000-ACME TECHNOLOGIES-PERSON NAME-CITIN…".
        Regex("""\bNEFT\s+Cr-[A-Za-z0-9]+-([^-\n.]{1,40})""", RegexOption.IGNORE_CASE),
        // ICICI-style NEFT credits name the payer after the reference:
        // "...Info NEFT-FDRLM4175907234-JOHN JOSE."
        Regex("""\bNEFT-[A-Za-z0-9]{6,}-([A-Za-z][A-Za-z .]{1,38}?)(?=[.,]|$)""", RegexOption.IGNORE_CASE),
        // "... towards BIG BAZAAR bangalore ..."
        Regex("""\btowards\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+(?:on|ref|no|dated|has|is|for)\b|[/.,]|$)""", RegexOption.IGNORE_CASE),
        // explicit labels: "merchant: ZOMATO", "at merchant SWIGGY"
        Regex("""(?:merchant|merchants?)\s*[:\-]?\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+[A-Z]|\s+ref\b|[.,]|$)""")
    )

    /** Tokens that disqualify an extracted merchant string. */
    private val merchantStopPhrases = listOf(
        "your", "you", "the", "this", "a/c", "account", "acct", "bank", "branch",
        "customer", "dear", "card", "debit card", "upi", "ref", "transaction", "txn", "info",
        "avail", "balance", "call", "sms", "block", "otp", "kyc", "imps", "neft",
        "rtgs", "ach", "nach", "valid", "login", "click", "www", "http",
        // Fund prices and plan wording are not merchant names: "NAV of Example
        // Bluechip Fund is Rs.24.53" produced the merchant "NAV of 24.53" on 25
        // monthly SIP debits in a closed tester's export (2026-10-16).
        "nav", "units", "folio", "as on", "sip",
        // "SIP purchase" was extracted as a merchant on every monthly fund debit.
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
        // NOTE the separator classes: `[ ,-]` (dash LAST) — the original `[ -,]`
        // was a character RANGE (space through comma), so a literal dash could
        // never match the year separator, "21-JUL-26" was never extracted, and
        // those messages silently fell back to `LocalDateTime.now()` at save
        // time. Measured on the owner's device, 2026-10-13; never write a dash
        // between two characters in a class.
        Regex("""\b(\d{1,2}[ -][A-Za-z]{3,9}[ ,-]\d{2,4})(?:\s+(\d{1,2}:\d{2}(?::\d{2})?)\s*([AaPp][Mm])?)?\b"""),
        // Month-first English dates ("Jul 22 2026", "JUL 22, 2026 6:19AM") match
        // none of the day-first patterns above, so a "debited ... on Jul 22 2026
        // 6:19AM" message parsed no date and was booked as the day the scan ran
        // (owner's device, 2026-10-13).
        Regex("""\b([A-Za-z]{3,9}\s+\d{1,2},?\s+\d{2,4})(?:\s+(\d{1,2}:\d{2}(?::\d{2})?)\s*([AaPp][Mm])?)?\b""")
    )

    private val dateFormats = listOf(
        "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
        "dd/MM/yy", "dd-MM-yy", "d M yy",
        // Dashed month-name dates ("21-JUL-26") are extremely common in Indian bank
        // SMS and had no format here, so those messages silently fell back to
        // `LocalDateTime.now()` at save time — wrong dates in the UI, and the
        // duplicate window collapsed onto the save hour (found on the owner's
        // device, 2026-10-13: two ICICI card payments were skipped as "duplicates"
        // of an unrelated credit saved minutes earlier).
        "dd-MMM-yy", "d-MMM-yy",
        "d MMM yyyy", "d MMM yy", "d MMMM yyyy", "dd-MMM-yyyy",
        // Month-first ordering ("Jul 22 2026"). parseDate strips commas before
        // matching, so "JUL 22, 2026" lands here too.
        "MMM d yyyy", "MMM d yy", "MMMM d yyyy",
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

        // Neutralise self-referential phrases before scoring: "reversal of debit"
        // is what the credit reverses (credit, not ambiguity), and "Bharat Bill
        // Payment System" is a brand, not a bill-payment debit. See the regex
        // comments for the real messages that motivated this.
        val scored = message
            .replace(reversalOfDebitPhrase, "credit")
            .replace(bbpsBrandPhrase, "BBPS")
            // "…Rs 22050 has been credited to the beneficiary account" is money
            // LEAVING (an outgoing NEFT); the literal "credited" made it income.
            .replace(creditedToBeneficiaryPhrase, "sent")
            // "…debited for Rs 180 …; MADURAI STORES credited." — the credited
            // party is the counterparty, not this account.
            .replace(counterpartyCreditedPhrase, " ")
            // "Aval Bal Rs.27000.00 CR." — the CR/DR annotates the balance.
            .replace(balanceCrDrAnnotation, " ")
            // "Statement is sent to <email>", "ebill … has been sent to" —
            // passive mail phrasing is not a transfer.
            .replace(passiveSentToPhrase, " ")
            .replace(ifNotReceivedPhrase, " ")
            // Biller receipts are the user's own payments, not income.
            .replace(billerReceiptPhrase, "paid")

        val debitScore = debitRegexes.count { it.containsMatchIn(scored) } +
            (if (drSuffix.containsMatchIn(scored)) 1 else 0)
        val creditScore = creditRegexes.count { it.containsMatchIn(scored) } +
            (if (crSuffix.containsMatchIn(scored)) 1 else 0)

        return when {
            debitScore > 0 && creditScore == 0 -> TransactionType.EXPENSE
            creditScore > 0 && debitScore == 0 -> TransactionType.INCOME
            else -> null
        }
    }

    internal fun detectAmount(message: String): Double? {
        data class Candidate(val value: Double, val start: Int, val end: Int)

        val candidates = mutableListOf<Candidate>()

        // Blank out credit/available-limit clauses first — see [creditLimitClause].
        val searchable = maskOut(message, creditLimitClause)

        fun collect(regex: Regex) {
            for (m in regex.findAll(searchable)) {
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
            val bankCode = vpa.groupValues[2].lowercase(Locale.ENGLISH)
            val lower = handle.lowercase(Locale.ENGLISH)

            // Generated payment-app QR ids ("paytmqr288005050101t74afkchmxjd@paytm")
            // are neither a person nor a shop; the app behind them is the identity.
            paymentAppForPrefix(lower)?.let { return it }

            // Pure digit handles are phone-number transfers: the user wants WHO
            // they paid, not the app the number is registered with.
            if (handle.count { it.isDigit() } >= 6 && handle.none { it.isLetter() }) {
                return handle.filter { it.isDigit() }
            }

            // Long mixed-alphanumeric ids are machine-generated; the bank code is
            // the most human-meaningful part left.
            if (lower.length > 24 && lower.any { it.isLetter() } && lower.any { it.isDigit() }) {
                bankDisplayName(bankCode)?.let { return it }
            }

            // A handle carrying digits is machine-ish ("ombk.aaeh000000abcd");
            // prettifying it invents a name that appears nowhere. Phone-like
            // handles were returned above; keep the rest raw.
            if (handle.any { it.isDigit() }) return handle

            val cleaned = cleanMerchant(handle) ?: return@let
            // Skip VPAs that are just long account/reference numbers.
            if (!cleaned.any { it.isLetter() }) return@let
            if (cleaned.length >= 3) return prettifyVpa(cleaned)
        }

        // 1b. Salary credits name the payroll token in an Info clause:
        // "...Info INF*000169831922*IQBO SAL FE. Available Balance..." — "Salary"
        // is the honest merchant; the employer's token is unreadable noise.
        if (infoSalaryPattern.containsMatchIn(message)) return "Salary"

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

        // The rail itself is the only identity some messages carry: "…debited
        // for INR 6440 towards IMPS/12345678901." Rail names are stop-words for
        // extraction (they are not merchants), but as labels they honestly
        // describe the movement.
        if (impsRail.containsMatchIn(message)) return "IMPS Transfer"
        if (creditedToBeneficiaryPhrase.containsMatchIn(message)) return "NEFT Transfer"

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
                // parseCaseInsensitive: bank SMS shout their months ("21-JUL-26"),
                // and plain `ofPattern` text parsing is case-sensitive, so "JUL"
                // never matched "Jul" — another way these dates were lost.
                val d = LocalDate.parse(
                    normalized,
                    DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(fmt)
                        .toFormatter(Locale.ENGLISH)
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

    /**
     * Trailing clauses that are never part of a merchant name. The capture
     * patterns stop before most of them, but provenance varies by bank ("at X
     * Ref No …", "to X on 23-08-26", "at X UPI …") and a leaked fragment is
     * user-visible noise. Bare "LTD"/"LIMITED" is deliberately NOT stripped —
     * it is part of real names (and of a pinned corpus expectation).
     */
    // "Ref No 12345", "Reference No: 12345" and the compact "Refno 12345" that
    // Indian banks prefer. The compact form was missing, so the capture in
    // "trf to SURESH MENON Refno 123456789012" still carried a 12-digit run,
    // `looksLikeMerchant` rejected it for looking like an account number, and the
    // payee vanished — a person-to-person transfer with no counterparty and no
    // category (closed tester's export, 2026-10-16).
    private val merchantRefSuffix = Regex(
        """\s+(?:ref(?:erence)?\.?\s*no|refno|rrn|utr)\.?\s*.*$""",
        RegexOption.IGNORE_CASE
    )
    private val merchantDateSuffix = Regex(
        """\s+on\s+\d{1,2}(?:[/.-]\d{1,2}(?:[/.-]\d{2,4})?|\s+[A-Za-z]{3,9}(?:\s+\d{2,4})?).*$""",
        RegexOption.IGNORE_CASE
    )
    private val merchantUpiSuffix = Regex("""\s+UPI.*$""", RegexOption.IGNORE_CASE)
    private val merchantTimeSuffix = Regex("""\s+at\s+\d{1,2}:\d{2}.*$""", RegexOption.IGNORE_CASE)
    /** "UPI-<ref>-CORN" — the merchant is the tail, not the UPI reference. */
    private val merchantUpiRefPrefix = Regex("""^UPI-\d+-""", RegexOption.IGNORE_CASE)
    private val merchantTrailingParens = Regex("""\s*\([^()]*\)\s*$""")
    private val merchantPvtLtd = Regex("""\s+(?:PVT\.?\s+LTD\.?|PRIVATE\s+LIMITED)$""", RegexOption.IGNORE_CASE)

    private fun cleanMerchant(raw: String): String? {
        val s = raw.trim()
            .replace(Regex("""[*#|]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .replace(merchantRefSuffix, "")
            .replace(merchantDateSuffix, "")
            .replace(merchantUpiSuffix, "")
            .replace(merchantTimeSuffix, "")
            .replace(merchantUpiRefPrefix, "")
            .replace(merchantTrailingParens, "")
            .replace(merchantPvtLtd, "")
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

    /** Payment-app QR prefixes → the app a user recognises. */
    private fun paymentAppForPrefix(handleLower: String): String? = when {
        handleLower.startsWith("paytmqr") -> "Paytm"
        handleLower.startsWith("phonepeqr") || handleLower.startsWith("phonepe.qr") -> "PhonePe"
        handleLower.startsWith("amazonpayqr") -> "Amazon Pay"
        handleLower.startsWith("bhimqr") -> "BHIM"
        handleLower.startsWith("bharatpeqr") -> "BharatPe"
        handleLower.startsWith("freechargeqr") -> "Freecharge"
        handleLower.startsWith("mobikwikqr") -> "MobiKwik"
        else -> null
    }

    /** UPI bank codes → the bank or app the handle belongs to. */
    private fun bankDisplayName(bankCode: String): String? = when (bankCode) {
        "ybl" -> "PhonePe"
        "okaxis", "okbizaxis" -> "Axis Bank"
        "okhdfcbank" -> "HDFC Bank"
        "okicici" -> "ICICI Bank"
        "oksbi" -> "State Bank of India"
        "paytm" -> "Paytm"
        "amazonpay" -> "Amazon Pay"
        "googlepay" -> "Google Pay"
        "airtel" -> "Airtel Money"
        "freecharge" -> "Freecharge"
        "mobikwik" -> "MobiKwik"
        "jupiteraxis" -> "Jupiter"
        "razorpay" -> "Razorpay"
        "bharatpe" -> "BharatPe"
        else -> null
    }

    private fun prettifyVpa(handle: String): String {
        // "john.doe" -> "John Doe"; "swiggy" -> "Swiggy"
        val parts = handle.split('.', '_', '-')
        val pretty = parts.filter { it.isNotBlank() }.joinToString(" ") { p ->
            p.replaceFirstChar { it.uppercase(Locale.ENGLISH) }
        }
        return pretty.ifBlank { handle }
    }

    /**
     * The note the pipeline stores when no merchant was found: whitespace
     * collapsed, cut at 120 characters with an ellipsis. Internal so the
     * transaction detail page can recognise its own filler.
     */
    internal fun truncateMessage(message: String): String {
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
        // 3. Person-to-person transfer: a payment rail plus a person-shaped
        //    counterparty, with no merchant keyword to say otherwise. The category
        //    is Transfer; the *type* comes from the direction lexicon, which is the
        //    owner's rule — received is income, sent is expense (2026-10-16). Rails
        //    alone are not category cues any more: `upi` in a Swiggy payment used
        //    to file the row under Transfer and lose the merchant's category.
        if (type != null && merchant != null && railTokenPattern.containsMatchIn(message) &&
            looksLikePersonName(
                merchant,
                // A UPI handle is a payment address, so a one-token payee derived
                // from it ("rahim@ybl" → "Rahim") is still a counterparty. Without
                // this, "Sent Rs.299 … to rahim@ybl UPI ref 512345" — the corpus
                // case that defines the owner's rule — had no category at all.
                allowSingleToken = vpaPattern.containsMatchIn(message)
            )
        ) {
            return "cat_transfer"
        }
        // 4. Income without stronger signals defaults to salary only when clearly payroll.
        if (type == TransactionType.INCOME &&
            Regex("""salary|payroll|wages""", RegexOption.IGNORE_CASE).containsMatchIn(message)
        ) return "cat_salary"
        return null
    }

    /** Payment rails: routing metadata, never a category by themselves. */
    private val railTokenPattern = Regex("""(?i)\b(?:upi|neft|imps|rtgs|trf|transfer)\b""")

    /**
     * Business words that disqualify a name from being read as a person's name.
     * Without them "LOCAL KIRANA STORE" (three alphabetic tokens) would be filed as
     * a person-to-person transfer.
     */
    private val businessWords = setOf(
        "store", "stores", "shop", "shoppe", "mart", "market", "mall", "bazaar", "bazar",
        "ltd", "limited", "pvt", "private", "enterprises", "enterprise", "services", "service",
        "traders", "trading", "agency", "company", "co", "industries", "solutions",
        "bank", "hotel", "restaurant", "restro", "cafe", "foods", "food", "kitchen",
        "medical", "medicals", "pharmacy", "hospital", "clinic", "diagnostics",
        "petrol", "fuel", "filling", "travels", "travel", "tours", "courier", "logistics",
        "technologies", "systems", "motors", "automobiles", "electronics", "mobiles"
    )

    /**
     * True when [name] reads like a person's name rather than a business: two to
     * four alphabetic tokens (initials allowed), no digits or handles, and no
     * business word — "GEETHA VARASSIAR K V" is a person, "SWIGGY" and
     * "LOCAL KIRANA STORE" are not.
     */
    private fun looksLikePersonName(name: String, allowSingleToken: Boolean = false): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) return false
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size !in (if (allowSingleToken) 1 else 2)..4) return false
        if (tokens.any { it.lowercase(Locale.ENGLISH).trim('.') in businessWords }) return false
        return tokens.all { Regex("^[A-Za-z][A-Za-z.'-]{0,20}\\.?$").matches(it) }
    }

    /** Word-boundary keyword scan across the whole message (shared logic). */
    private fun messageKeywordCategory(message: String): String? =
        keywordCategoryFor(message)
}

