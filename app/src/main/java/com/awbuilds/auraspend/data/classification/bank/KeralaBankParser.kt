package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Kerala Bank's sender IDs ("KELBNK"). The engine reads the bank's formats;
 * this parser supplies the sender→bank attribution for messages that never
 * name the bank in the body.
 */
class KeralaBankParser : BankParser {

    override val bankName = "Kerala Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("KELBNK")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
