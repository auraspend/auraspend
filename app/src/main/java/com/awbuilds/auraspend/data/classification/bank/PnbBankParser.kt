package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Punjab National Bank's sender IDs and the labels its wording carries.
 *
 * PNB sends from "VM-PNBSMS-S" (accounts) and "VA-PNBCCD-S" (credit cards),
 * and the engine reads those messages' amounts and directions. This parser
 * adds the sender→bank attribution and the labels the wording implies:
 *
 *  - "…thru card XX9239" — the card the account moved through;
 *  - "…thru Debitcard XXXX0000" with no named merchant — a generic card move;
 *  - "…thru UPI…" with no counterparty — a generic UPI move.
 *
 * Note the parser deliberately does NOT rescue "is debited … and a/c …
 * credited" messages (the PNB IMPS self-transfer shape): the direction is
 * genuinely ambiguous, and the pipeline's null-type gate skips them rather
 * than booking a self-transfer as spending.
 */
class PnbBankParser : BankParser {

    override val bankName = "PNB"

    private val dlt = Regex("^[A-Z]{2}-PNB(?:BNK)?(?:-[ST])?$")
    private val cardDlt = Regex("^(?:[A-Z]{2}-)?PNBCCD(?:-[ST])?$")

    override fun canHandle(sender: String): Boolean {
        if (sender == "PNB" || sender == "PNBBNK") return true
        if (sender.contains("PNBSMS") || sender.contains("PUNBN")) return true
        return dlt.containsMatchIn(sender) || cardDlt.containsMatchIn(sender)
    }

    private val thruCard = Regex("""(?i)\bthru\s+card\s+([Xx*\d][Xx*\d ]{2,8})""")
    private val debitCard = Regex("""(?i)\bthru\s+debitcard\b""")
    private val thruUpi = Regex("""(?i)\bthru\s+UPI\b""")

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = base.merchant ?: label(rawMessage)
        return base.copy(bankName = bankName, merchant = merchant)
    }

    private fun label(message: String): String? {
        thruCard.find(message)?.groupValues?.get(1)?.trim()?.let { mask ->
            if (mask.isNotEmpty()) return "Card $mask"
        }
        if (debitCard.containsMatchIn(message)) return "Debit Card Transaction"
        if (thruUpi.containsMatchIn(message)) return "UPI Transaction"
        return null
    }
}
