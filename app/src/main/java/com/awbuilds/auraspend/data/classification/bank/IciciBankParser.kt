package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * ICICI Bank's sender IDs and the formats the global engine cannot label.
 *
 * ICICI sends from DLT templates ("AX-ICICIT-S", "JM-ICICIT-S", "AD-ICICIT-S")
 * and direct IDs ("ICICIB", "ICICIBANK"). The engine reads the standard
 * shapes — "Acct XX123 debited for Rs 500 …", card spends, salary credits,
 * UPI payee credits, NEFT beneficiary direction — this parser only adds:
 *  - ATM markers nothing else explains: "NFSCASH WDL" / "NFS*CASH WDL*"
 *    (shared network, "Cash Withdrawal") and "CAM*…" (ICICI's own ATM,
 *    "ATM Withdrawal");
 *  - bill payments that label the biller in an Info clause: "InfoBIL*Auto Loan";
 *  - the sender→bank attribution for short messages that never say "ICICI".
 */
class IciciBankParser : BankParser {

    override val bankName = "ICICI Bank"

    override fun canHandle(sender: String): Boolean =
        sender.contains("ICICI") || sender == "ICICIB" || sender == "ICICIBANK"

    private val atmNetworkMarker = Regex("""(?i)\bNFS[* ]*CASH\s+WDL\b""")
    private val atmOwnMarker = Regex("""(?i)\bCAM\*\w""")
    private val billerMarker = Regex("""(?i)\bInfoBIL\*([^.\n]{1,40})""")

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = base.merchant ?: specificMerchant(rawMessage)
        return base.copy(bankName = bankName, merchant = merchant)
    }

    private fun specificMerchant(message: String): String? {
        if (atmNetworkMarker.containsMatchIn(message)) return "Cash Withdrawal"
        if (atmOwnMarker.containsMatchIn(message)) return "ATM Withdrawal"
        billerMarker.find(message)?.let { m ->
            m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }
}
