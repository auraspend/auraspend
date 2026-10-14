package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Saraswat Co-operative Bank's sender IDs and its label clauses.
 *
 * The engine reads the "credited/debited with INR … Current Bal … CR" shapes;
 * this parser adds:
 *  - the colon payee of ACH details — "towards ACH Credit:MERCHANT NAME";
 *  - the rail labels its "for X" clause implies: "for S.I." (standing
 *    instruction), "for NEFT/RTGS/IMPS".
 */
class SaraswatBankParser : BankParser {

    override val bankName = "Saraswat Bank"

    override fun canHandle(sender: String): Boolean =
        sender == "SARBNK" || sender == "SARASWAT" || sender == "SARASWATBANK" ||
            Regex("^[A-Z]{2}-(?:SARBNK|SARASWAT)(?:-[ST])?$").containsMatchIn(sender)

    private val achPayee = Regex("""(?i)\btowards\s+ACH\s+Credit:\s*([^.\n]{1,40})""")

    private val rails = listOf(
        Regex("""(?i)\bfor\s+S\.?I\.?\b""") to "Standing Instruction",
        Regex("""(?i)\bfor\s+NEFT\b""") to "NEFT Transfer",
        Regex("""(?i)\bfor\s+RTGS\b""") to "RTGS Transfer",
        Regex("""(?i)\bfor\s+IMPS\b""") to "IMPS Transfer"
    )

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val specific = achPayee.find(rawMessage)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: rails.firstOrNull { it.first.containsMatchIn(rawMessage) }?.second
        return base.copy(bankName = bankName, merchant = specific ?: base.merchant)
    }
}
