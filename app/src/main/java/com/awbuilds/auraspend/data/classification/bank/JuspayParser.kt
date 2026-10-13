package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Amazon Pay sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Amazon Pay-specific
 * corrections.
 */
class JuspayParser : BankParser {

    override val bankName = "Amazon Pay"

    override fun canHandle(sender: String): Boolean = sender.contains("JUSPAY") || sender.contains("APAY") || sender == "AMAZON PAY"

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
