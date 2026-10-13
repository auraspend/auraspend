package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * OneCard sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future OneCard-specific
 * corrections.
 */
class OneCardParser : BankParser {

    override val bankName = "OneCard"

    override fun canHandle(sender: String): Boolean = sender.contains("ONECRD") || sender.contains("ONECARD")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
