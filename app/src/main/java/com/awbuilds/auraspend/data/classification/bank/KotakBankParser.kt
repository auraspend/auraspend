package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Kotak Mahindra Bank's sender IDs and the formats the global engine cannot label.
 *
 * Kotak sends from DLT templates ("JD-KOTAKB-S", "VM-KOTAKD-S", mixed-case
 * variants like "JM-Kotakb-S") and, in RCS, from the decoded display name
 * ("Kotak", "Kotak Mahindra Bank"). The engine reads the standard shapes —
 * "Sent Rs.X from Kotak Bank AC X1234 to payee@upi", QR-handle app names,
 * "…refunded to your Kotak Credit Card …", short-SMS payees — this parser adds:
 *  - the sender→bank attribution for short messages that never say "Kotak"
 *    in the body;
 *  - IMPS credits whose counterparty is a masked mobile: "by an A/C linked to
 *    mobile x111" — the mobile mask is the only identity the message carries.
 */
class KotakBankParser : BankParser {

    override val bankName = "Kotak Mahindra"

    override fun canHandle(sender: String): Boolean = sender.contains("KOTAK")

    private val mobileLinkedCounterparty = Regex("""\blinked\s+to\s+mobile\s+([xX*]+\d{2,})""")

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = base.merchant
            ?: mobileLinkedCounterparty.find(rawMessage)?.groupValues?.get(1)
        return base.copy(bankName = bankName, merchant = merchant)
    }
}
