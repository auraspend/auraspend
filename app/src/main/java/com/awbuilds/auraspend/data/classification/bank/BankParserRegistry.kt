package com.awbuilds.auraspend.data.classification.bank

/**
 * Maps an SMS sender ID to the parser that knows that bank's format.
 *
 * Sender ID is the one piece of routing metadata the app receives for free and
 * reliably: it is delivered with the message, costs nothing to read, and is
 * stable. The golden corpus did not capture it until now, which is why
 * sender-routed parsing had never been measurable in this project.
 *
 * Returning null is a normal, expected outcome, not a failure. Most users bank
 * with at least one institution not in [all], and the caller must fall back to
 * the global parser rather than guess.
 */
object BankParserRegistry {

    val all: List<BankParser> = listOf(
        AxisBankParser(),
        CanaraBankParser(),
        SbiBankParser(),
        HdfcBankParser(),
        IciciBankParser(),
        KotakBankParser(),
        PnbBankParser(),
        BankOfBarodaParser(),
        IdfcFirstBankParser(),
        IndusIndBankParser(),
        FederalBankParser(),
        YesBankParser(),
        AuBankParser(),
        BandhanBankParser(),
        UnionBankParser(),
        CentralBankOfIndiaParser(),
        SouthIndianBankParser(),
        BankOfIndiaParser(),
        IndianBankParser(),
        IndianOverseasBankParser(),
        IdbiBankParser(),
        UcoBankParser(),
        KarnatakaBankParser(),
        CityUnionBankParser(),
        DhanlaxmiBankParser(),
        DopBankParser(),
        EquitasBankParser(),
        PunjabSindBankParser(),
        UtkarshBankParser(),
        JkBankParser(),
        HsbcBankParser(),
        StandardCharteredBankParser(),
        AmexBankParser(),
        OneCardParser(),
        SliceParser(),
        CredParser(),
        LazyPayParser(),
        JupiterBankParser(),
        JioPaymentsBankParser(),
        JioPayParser(),
        AirtelPaymentsBankParser(),
        NsdlPaymentsBankParser(),
        PluxeeBankParser(),
        JuspayParser(),
        CashfreeParser(),
        SaraswatBankParser()
    )

    /**
     * The parser for [sender], or null when no registered bank claims it.
     *
     * [sender] is normalised here rather than by the caller, so a parser's
     * [BankParser.canHandle] only ever sees a trimmed, upper-cased address.
     */
    fun parserFor(sender: String): BankParser? {
        if (sender.isBlank()) return null
        val normalized = sender.trim().uppercase()
        return all.firstOrNull { it.canHandle(normalized) }
    }
}
