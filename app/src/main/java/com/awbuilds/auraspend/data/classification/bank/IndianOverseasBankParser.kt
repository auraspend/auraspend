package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Indian Overseas Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Indian Overseas Bank-specific
 * corrections.
 */
class IndianOverseasBankParser : BankParser {

    override val bankName = "Indian Overseas Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("IOB") || sender.contains("IOBCHN")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
