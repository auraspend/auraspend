package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Bandhan Bank's sender IDs ("BDNSMS", "BANDHN", RCS display name). The engine
 * reads the bank's formats; this parser supplies the sender→bank attribution
 * for messages that never name the bank in the body.
 */
class BandhanBankParser : BankParser {

    override val bankName = "Bandhan Bank"

    private val dlt = Regex("^[A-Z]{2}-(?:BDNSMS|BANDHN)(?:-S)?$")

    override fun canHandle(sender: String): Boolean =
        sender.contains("BANDHAN") || dlt.containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
