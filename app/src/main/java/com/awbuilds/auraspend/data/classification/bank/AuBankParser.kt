package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * AU Small Finance Bank's sender IDs ("AUBANK…"). The engine reads the bank's
 * formats; this parser supplies the sender→bank attribution for messages that
 * never name the bank in the body.
 */
class AuBankParser : BankParser {

    override val bankName = "AU Small Finance Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("AUBANK")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
