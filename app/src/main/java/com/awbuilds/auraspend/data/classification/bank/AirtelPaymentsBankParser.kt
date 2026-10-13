package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Airtel Payments Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Airtel Payments Bank-specific
 * corrections.
 */
class AirtelPaymentsBankParser : BankParser {

    override val bankName = "Airtel Payments Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("AIRBNK")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
