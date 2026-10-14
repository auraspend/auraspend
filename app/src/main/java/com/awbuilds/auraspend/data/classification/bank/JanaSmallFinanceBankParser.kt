package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Jana Small Finance Bank's sender IDs ("JANABK", "JANASFB"). The engine
 * reads the bank's formats; this parser supplies the sender→bank attribution
 * for messages that never name the bank in the body.
 */
class JanaSmallFinanceBankParser : BankParser {

    override val bankName = "Jana Small Finance Bank"

    override fun canHandle(sender: String): Boolean =
        sender.contains("JANABK") || sender.contains("JANASFB")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
