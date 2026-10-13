package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * HDFC Mutual Fund's sender IDs ("AD-HDFCMF-AC", "VM-HDFCMF").
 *
 * SIP purchase messages name the fund as a clause — "…SIP Purchase in Folio
 * 38822412/82 under HDFC Mid Cap Fund-DG for Rs. 7,000.65…" — which the
 * generic patterns cannot reach; the fund is the merchant of record.
 */
class HdfcMutualFundParser : BankParser {

    override val bankName = "HDFC Mutual Fund"

    override fun canHandle(sender: String): Boolean = sender.contains("HDFCMF")

    private val fundName = Regex("""(?i)\bunder\s+([^.\n]{1,48}?)\s+for\s+Rs""")

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = fundName.find(rawMessage)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.length >= 2 }
            ?: base.merchant
        return base.copy(bankName = bankName, merchant = merchant)
    }
}
