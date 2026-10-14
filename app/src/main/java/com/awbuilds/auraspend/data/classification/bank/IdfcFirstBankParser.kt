package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * IDFC First Bank's sender IDs ("BM-IDFCBK-S", "JM-IDFCFB-S", "AX-IDFCBK-T", …).
 *
 * The engine reads the standard shapes — "is debited by INR … New Bal …",
 * "…debited …; REDBUS credited", card spends with an Avbl Limit tail — this
 * parser adds the sender→bank attribution and one label:
 *  - "…credited by INR 125.50 … for monthly interest" — an interest credit.
 */
class IdfcFirstBankParser : BankParser {

    override val bankName = "IDFC First"

    override fun canHandle(sender: String): Boolean = sender.contains("IDFC")

    private val interestCredit = Regex("""(?i)\bfor\s+monthly\s+interest\b""")

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = base.merchant
            ?: if (interestCredit.containsMatchIn(rawMessage)) "Interest Credit" else null
        return base.copy(bankName = bankName, merchant = merchant)
    }
}
