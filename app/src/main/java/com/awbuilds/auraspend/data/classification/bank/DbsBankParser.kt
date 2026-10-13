package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * DBS Bank's sender IDs ("DBSBNK", "DBS", DLT templates). The engine reads the
 * bank's formats; this parser supplies the sender→bank attribution — keyed on
 * the distinctive DBSBNK/DBSSMS tokens rather than a bare "DBS" substring, so
 * unrelated senders cannot be claimed.
 */
class DbsBankParser : BankParser {

    override val bankName = "DBS Bank"

    override fun canHandle(sender: String): Boolean =
        sender.contains("DBSBNK") || sender.contains("DBSSMS") ||
            sender == "DBS" || sender == "DBSBANK" ||
            Regex("^[A-Z]{2}-DBS(?:BNK|BANK)?(?:-[ST])?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
