package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Standard Chartered's sender IDs ("VM-SCBANK-S", "SCBANK", "9220", …).
 *
 * The engine reads the "debited/credited for Rs X" shapes; this parser adds
 * the rail labels of its credit notices — "there is an NEFT/RTGS/IMPS credit
 * of INR X in your account …" — so the merchant names the channel rather than
 * staying empty. (The PKR/IBFT/RAAST family is deliberately not parsed: this
 * app stores one number with no per-transaction currency, so foreign-amount
 * messages stay out rather than being mislabelled in rupees.)
 */
class StandardCharteredBankParser : BankParser {

    override val bankName = "Standard Chartered"

    override fun canHandle(sender: String): Boolean =
        sender.contains("SCBANK") || sender.contains("STANCHART") ||
            sender.contains("STANDARDCHARTERED") || sender.contains("STANDARD CHARTERED") ||
            sender == "9220" || Regex("^[A-Z]{2}-SCBANK(?:-[STPG])?$").containsMatchIn(sender)

    private val railCredit = Regex("""(?i)\b(NEFT|RTGS|IMPS)\s+credit\b""")

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = base.merchant
            ?: railCredit.find(rawMessage)?.groupValues?.get(1)?.uppercase()?.let { "$it Credit" }
        return base.copy(bankName = bankName, merchant = merchant)
    }
}
