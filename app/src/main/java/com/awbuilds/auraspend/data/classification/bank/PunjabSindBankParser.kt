package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Punjab & Sind Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Punjab & Sind Bank-specific
 * corrections.
 */
class PunjabSindBankParser : BankParser {

    override val bankName = "Punjab & Sind Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("PSBANK") || sender.contains("PUNJAB&SIND") || sender.contains("PUNJAB & SIND")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
