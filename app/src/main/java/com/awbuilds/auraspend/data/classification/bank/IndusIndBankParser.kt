package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * IndusInd Bank's sender IDs ("AD-INDUSB-S", "VM-INDUSIND-S", "JK-INDUSB-S", …).
 *
 * The engine reads IndusInd's formats — "debited from A/c … at ZOMATO",
 * "*XX1234 credited by … from xxxx@ybl", "towards IMPS/123…", "Avl Lmt: INR …"
 * card spends — so this parser exists for the sender→bank attribution and as
 * the home of future IndusInd-specific corrections. The "Net interest … paid
 * on your Deposit" notice is vetoed at ingest (a general guard), not here.
 */
class IndusIndBankParser : BankParser {

    override val bankName = "IndusInd Bank"

    private val dltShort = Regex("^[A-Z]{2}-INDUSB(?:-[A-Z])?$")
    private val dltLong = Regex("^[A-Z]{2}-INDUSIND(?:-[A-Z])?$")
    private val dltVaried = Regex("^[A-Z]{2}-INDUS(?:[A-Z]{2,})?-[A-Z]$")

    override fun canHandle(sender: String): Boolean =
        sender == "INDUSB" || sender == "INDUSIND" || sender.contains("INDUSIND BANK") ||
            dltShort.containsMatchIn(sender) ||
            dltLong.containsMatchIn(sender) ||
            dltVaried.containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
