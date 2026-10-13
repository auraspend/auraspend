package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * India Post Payments Bank's sender IDs ("AD-IPBMSG-S", "IPBMSG"). The engine
 * reads the bank's formats; this parser supplies the sender→bank attribution
 * for messages that never name the bank in the body.
 */
class IppbParser : BankParser {

    override val bankName = "India Post Payments Bank"

    override fun canHandle(sender: String): Boolean =
        sender == "IPBMSG" || Regex("^[A-Z]{2}-IPBMSG(?:-[ST])?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
