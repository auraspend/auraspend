package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * HDFC Bank's sender IDs and the one format the global engine reads wrongly.
 *
 * HDFC sends from DLT templates ("CP-HDFCBK-S", "VM-HDFCBK-S", "AX-HDFCBK-S")
 * and, in RCS, from the bare "HDFC Bank" display name. Routing matters here
 * mostly for attribution: short formats ("Sent Rs.50 from XXXXXX1234 to …")
 * never name the bank in the body, so the sender is the only honest source
 * for [ParsedBankMessage.bankName].
 *
 * The extraction layers corrections on the global parse rather than
 * re-implementing it — the engine already reads the standard
 * "debited from A/c …, deposited …, spent …" shapes, and a second copy of
 * that logic would drift from it:
 *  - "Transaction Reversed!… By AMAZON …" — the merchant follows "By";
 *    no generic pattern may claim a bare "By <word>" (it appears in too many
 *    other sentences to be safe), but in a reversal it is exactly the merchant.
 */
class HdfcBankParser : BankParser {

    override val bankName = "HDFC Bank"

    // Mutual-fund templates ("AD-HDFCMF-S") must NOT route here — those
    // messages are unit allotments, not Bank-account transactions, and the
    // reference project orders its MF parser before the bank parser for the
    // same reason.
    private val dltTemplate = Regex("^[A-Z]{2}-HDFCB(?:K|ANK)?(?:-[STPGR])?$")

    override fun canHandle(sender: String): Boolean {
        if (sender.contains("MF")) return false
        return when (sender) {
            "HDFCBK", "HDFCBANK", "HDFC", "HDFCB", "HDFC BANK" -> true
            else -> dltTemplate.containsMatchIn(sender)
        }
    }

    private val reversalMerchant = Regex(
        """\bBy\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=\s+\d{3}|\s+On\b|[.,]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val reversalWord = Regex("""(?i)\brevers(?:ed|al)\b""")

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = base.merchant ?: run {
            if (!reversalWord.containsMatchIn(rawMessage)) return@run null
            reversalMerchant.find(rawMessage)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 }
        }
        return base.copy(bankName = bankName, merchant = merchant)
    }
}
