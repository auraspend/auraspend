package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Union Bank of India's sender IDs ("VM-UNIONB-S", "UNIONBNK", "UBOI…"). The
 * engine reads the bank's formats; this parser supplies the sender→bank
 * attribution for messages that never name the bank in the body.
 */
class UnionBankParser : BankParser {

    override val bankName = "Union Bank"

    private val dlt = Regex("^[A-Z]{2}-UNIONB(?:ANK)?(?:-[STPG])?$")

    override fun canHandle(sender: String): Boolean =
        sender.contains("UNIONB") || sender.contains("UBOI") || dlt.containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
