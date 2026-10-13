package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Yes Bank's sender IDs ("VM-YESBNK-S", "YESBNK", "YESBANK"). The engine reads
 * the bank's formats; this parser supplies the sender→bank attribution for
 * messages that never name the bank in the body.
 */
class YesBankParser : BankParser {

    override val bankName = "Yes Bank"

    private val dlt = Regex("^[A-Z]{2}-YESBNK(?:-[ST])?$")

    override fun canHandle(sender: String): Boolean =
        sender == "YESBNK" || sender == "YESBANK" || dlt.containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
