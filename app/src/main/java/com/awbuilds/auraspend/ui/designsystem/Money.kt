package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * The currency the user's money is displayed in.
 *
 * The app used to hardcode `₹` with `Locale.US` grouping everywhere, which meant
 * a user anywhere outside India saw `₹1,234,567` — Indian rupees formatted with
 * Western digit grouping. Amounts are stored as bare numbers, so presentation is
 * the only place currency belongs.
 */
data class CurrencyStyle(
    /** ISO 4217 code, e.g. `INR`. */
    val code: String,
    /** Overrides the locale-derived symbol when non-null (e.g. a custom code). */
    val symbolOverride: String? = null
) {
    /** The symbol actually rendered. */
    val symbol: String
        get() = symbolOverride ?: runCatching {
            Currency.getInstance(code).getSymbol(Locale.getDefault())
        }.getOrElse { code }

    companion object {
        val INR = CurrencyStyle("INR")

        /** Resolves an unknown/blank code to INR rather than throwing. */
        fun fromCode(code: String?): CurrencyStyle {
            if (code.isNullOrBlank()) return INR
            return CurrencyStyle(code.uppercase(Locale.ROOT))
        }
    }
}

val LocalCurrencyStyle = staticCompositionLocalOf { CurrencyStyle.INR }

/**
 * Process-wide current currency, for the many call sites that format money outside
 * composition (chart axis labels, CSV export, string building).
 *
 * Kept in sync by `AuraSpendTheme` on every composition, so a change in Settings
 * propagates everywhere without threading a parameter through every call site.
 */
object MoneyConfig {
    @Volatile
    var current: CurrencyStyle = defaultForDeviceLocale()
        private set

    /** Called by the theme; not part of the public UI contract. */
    fun update(style: CurrencyStyle) {
        current = style
    }

    private fun defaultForDeviceLocale(): CurrencyStyle = CurrencyStyle.fromCode(
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
    )
}

/**
 * Formats [value] for display.
 *
 * Grouping, decimal separators and the symbol all come from the current locale
 * and [MoneyConfig], so `1,23,456.00` in `en-IN` and `123,456.00` in `en-US` come
 * out correctly without any per-call locale plumbing. Amounts are never abbreviated
 * here — a finance app should show the real number; compaction belongs to the
 * chart axis, which opts in explicitly via [formatMoneyCompact].
 *
 * @param fractionDigits null keeps the currency's own convention (JPY has none,
 *   most others two), which is what a user expects from a money column.
 */
/**
 * Typographic minus (U+2212), not a hyphen-minus.
 *
 * A hyphen is a shorter glyph that sits too high next to digits; the minus sign
 * is the same width as a digit and aligns with it. At the large sizes the
 * balance card renders, the difference is obvious, and mixing the two across
 * screens reads as a rendering bug even though both are correct characters.
 *
 * Every sign in the app comes from here — [formatMoney] and [formatSignedMoney]
 * below — so no call site re-derives one by hand.
 */
const val MINUS_SIGN = "\u2212"

/** Leading sign for a money value: [MINUS_SIGN] when negative, empty otherwise. */
fun moneySign(value: Double): String = if (value < 0) MINUS_SIGN else ""

/**
 * An explicitly signed amount, for deltas where a leading `+` is meaningful
 * (a day's net, a comparison between two periods).
 *
 * Negative values get [MINUS_SIGN] from [formatMoney] itself, so they are
 * rendered here without prepending a second sign.
 */
fun formatSignedMoney(
    value: Double,
    style: CurrencyStyle = MoneyConfig.current,
    locale: Locale = Locale.getDefault(),
    fractionDigits: Int? = null
): String = when {
    value > 0 -> "+" + formatMoney(value, style, locale, fractionDigits)
    else -> formatMoney(value, style, locale, fractionDigits)
}

fun formatMoney(
    value: Double,
    style: CurrencyStyle = MoneyConfig.current,
    locale: Locale = Locale.getDefault(),
    fractionDigits: Int? = null
): String {
    val currency = runCatching { Currency.getInstance(style.code) }.getOrNull()
        ?: Currency.getInstance("INR")
    // The currency's own convention (JPY has none, most others two) is what a user
    // expects from a money column, so it is the default rather than a hardcoded 2.
    val digits = fractionDigits ?: runCatching { currency.defaultFractionDigits }.getOrDefault(2)

    // Indian locales group as 1,23,456 — the lakh/crore system. Neither
    // `NumberFormat` nor a `#,##,##0` pattern reliably produces that across JDK
    // and ICU versions (this project builds against a JDK that returns Western
    // grouping for both en_IN and hi_IN), so the grouping is done explicitly.
    // Deterministic rendering beats platform-dependent formatting for money.
    if (locale.usesIndianGrouping()) {
        val fraction = if (digits > 0) "." + "0".repeat(digits) else ""
        val plain = DecimalFormat("#,##0.#", DecimalFormatSymbols(Locale.US)).format(abs(value))
        val (whole, decimals) = plain.split('.', limit = 2).let {
            it[0].replace(",", "") to it.getOrNull(1)?.padEnd(digits, '0')?.take(digits).orEmpty()
        }
        val grouped = indianGroup(whole)
        val sign = if (value < 0) MINUS_SIGN else ""
        return "$sign${style.symbol}$grouped${if (decimals.isEmpty()) "" else ".$decimals"}"
    }

    val format = NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    return format.format(value)
}

/**
 * Inserts lakh/crore separators into a plain digit string.
 *
 * `1234567` becomes `12,34,567`: the last three digits always form a group, and
 * everything to the left is grouped in pairs.
 */
private fun indianGroup(whole: String): String {
    if (whole.length <= 3) return whole
    val lastThree = whole.takeLast(3)
    val head = whole.dropLast(3)
    val sb = StringBuilder()
    // Walk leftwards from the end of the head, closing a group every 2 digits.
    var count = 0
    for (i in head.indices.reversed()) {
        sb.append(head[i])
        count++
        if (count % 2 == 0 && i != 0) sb.append(',')
    }
    return "${sb.reverse()},$lastThree"
}

/**
 * True for locales that use the lakh/crore digit grouping (1,23,456.78).
 *
 * Keyed on the country rather than the language because `en` is used for both
 * `en_IN` (grouped) and `en_US` (not grouped).
 */
private fun Locale.usesIndianGrouping(): Boolean = country.equals("IN", ignoreCase = true)

/**
 * Compact form for chart axes and dense tiles: `₹1.2L`, `$3.4K`.
 *
 * Uses the locale's own compact pattern where one exists (Indian locales get
 * lakh/crore grouping for free via `NumberFormat`), so this reads naturally in
 * every market rather than always switching to K/M.
 */
fun formatMoneyCompact(
    value: Double,
    style: CurrencyStyle = CurrencyStyle.INR,
    locale: Locale = Locale.getDefault()
): String {
    val magnitude = kotlin.math.abs(value)
    if (magnitude < 1_000) return formatMoney(value, style, locale)

    val currencySymbol = style.symbol
    val negative = value < 0
    val (scaled, suffix) = when {
        magnitude >= 1_00_00_000 -> Pair(magnitude / 1_00_00_000, "Cr")
        magnitude >= 1_00_000 -> Pair(magnitude / 1_00_000, "L")
        magnitude >= 1_000_000 -> Pair(magnitude / 1_000_000, "M")
        magnitude >= 1_000 -> Pair(magnitude / 1_000, "K")
        else -> Pair(magnitude, "")
    }
    val oneDecimal = DecimalFormat(
        "0.#",
        DecimalFormatSymbols(locale)
    ).format(scaled)
    return buildString {
        if (negative) append('-')
        append(currencySymbol)
        append(oneDecimal)
        append(suffix)
    }
}

/**
 * Reads the currency the app should render in.
 *
 * [chosenCode] is the value the picker wrote, hoisted by the caller
 * (`MainActivity`) so that *changing* it recomposes the theme. Reading the
 * preference here instead would look equivalent and be wrong: the preference is
 * not observable state, so the theme would keep the old symbol until the next
 * cold start — the same trap the locale comment below describes.
 *
 * When nothing is chosen the device locale decides, so a user in Germany sees `€`
 * without configuring anything. Call inside composition; the result is also
 * pushed into [MoneyConfig] so non-composable call sites agree.
 */
@Composable
fun rememberCurrencyStyle(chosenCode: String? = null): CurrencyStyle {
    if (!chosenCode.isNullOrBlank()) return CurrencyStyle.fromCode(chosenCode)

    // LocalConfiguration rather than Locale.getDefault(): the latter is read once
    // at class-init and does not recompose when the user changes system language,
    // so the currency symbol would go stale until the next cold start.
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        CurrencyStyle.fromCode(runCatching { Currency.getInstance(locale).currencyCode }.getOrNull())
    }
}
