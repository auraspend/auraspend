package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Jupiter sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Jupiter-specific
 * corrections.
 */
class JupiterBankParser : BankParser {

    override val bankName = "Jupiter"

    override fun canHandle(sender: String): Boolean = sender.contains("JTEDGE") || sender == "JUPITER"

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
