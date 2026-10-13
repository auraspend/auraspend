package com.awbuilds.auraspend.ui.designsystem

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Currency
import java.util.Locale

/**
 * The currencies the user can pick, hardcoded so the picker needs no network.
 *
 * **Source:** `GET https://api.frankfurter.dev/v1/currencies`, fetched
 * 2026-10-11 — the ECB reference set, 30 currencies. The response is captured
 * here verbatim; the app never calls that endpoint at runtime. This is a
 * deliberate choice, not a shortcut: AuraSpend is offline-first and its on-screen
 * claim is that nothing leaves the phone, so a settings screen that phoned home
 * for a list of names would be both a new network path and a new failure mode on
 * a plane.
 *
 * **Names are for provenance and fallback only.** What the user sees comes from
 * `java.util.Currency` (`displayName`/`getSymbol`), which Android localizes
 * already — so "Indian Rupee" reads correctly in Hindi without shipping 30
 * translated strings per locale. [currencyDisplayName] does that lookup and falls
 * back to the name below, then to the bare code.
 *
 * This replaced `CurrencyStyle.supported`, a 10-entry list with **zero call
 * sites**: a picker that was never built, leaving a currency the user could not
 * change. `CurrencyCatalogTest` pins the 30 codes so a future edit is deliberate.
 */
data class CatalogCurrency(
    /** ISO 4217 alpha code, e.g. `INR`. */
    val code: String,
    /** English name exactly as the API returned it. Display prefers the platform's. */
    val name: String
)

/** The 30 currencies, alphabetical by code. */
val currencyCatalog: List<CatalogCurrency> = listOf(
    CatalogCurrency("AUD", "Australian Dollar"),
    CatalogCurrency("BRL", "Brazilian Real"),
    CatalogCurrency("CAD", "Canadian Dollar"),
    CatalogCurrency("CHF", "Swiss Franc"),
    CatalogCurrency("CNY", "Chinese Renminbi Yuan"),
    CatalogCurrency("CZK", "Czech Koruna"),
    CatalogCurrency("DKK", "Danish Krone"),
    CatalogCurrency("EUR", "Euro"),
    CatalogCurrency("GBP", "British Pound"),
    CatalogCurrency("HKD", "Hong Kong Dollar"),
    CatalogCurrency("HUF", "Hungarian Forint"),
    CatalogCurrency("IDR", "Indonesian Rupiah"),
    CatalogCurrency("ILS", "Israeli New Shekel"),
    CatalogCurrency("INR", "Indian Rupee"),
    CatalogCurrency("ISK", "Icelandic Króna"),
    CatalogCurrency("JPY", "Japanese Yen"),
    CatalogCurrency("KRW", "South Korean Won"),
    CatalogCurrency("MXN", "Mexican Peso"),
    CatalogCurrency("MYR", "Malaysian Ringgit"),
    CatalogCurrency("NOK", "Norwegian Krone"),
    CatalogCurrency("NZD", "New Zealand Dollar"),
    CatalogCurrency("PHP", "Philippine Peso"),
    CatalogCurrency("PLN", "Polish Złoty"),
    CatalogCurrency("RON", "Romanian Leu"),
    CatalogCurrency("SEK", "Swedish Krona"),
    CatalogCurrency("SGD", "Singapore Dollar"),
    CatalogCurrency("THB", "Thai Baht"),
    CatalogCurrency("TRY", "Turkish Lira"),
    CatalogCurrency("USD", "United States Dollar"),
    CatalogCurrency("ZAR", "South African Rand")
)

/**
 * The list as the picker shows it: [deviceCode] first (the likeliest answer),
 * everything else alphabetical by code.
 *
 * Pure so the ordering is testable; `sortedBy` is stable, so this is a partition
 * rather than a reshuffle, and a code the catalog does not carry is ignored.
 */
fun orderedCurrencies(deviceCode: String?): List<CatalogCurrency> {
    val normalized = deviceCode?.trim()?.uppercase(Locale.ROOT)
    return currencyCatalog.sortedBy { it.code }.sortedByDescending { it.code == normalized }
}

/** Preference key holding the chosen ISO code. Unset means "follow the device". */
const val PREF_CURRENCY_CODE = "currency_code"

/** Preference file the whole app reads its settings from. */
private const val PREF_FILE = "auraspend_prefs"

/**
 * The name to show for [code]: the platform's localized name, else the catalog's
 * English name, else the code itself.
 *
 * Never throws and never returns blank — a currency the platform does not know is
 * still renderable, which matters because this string is a Settings subtitle.
 */
fun currencyDisplayName(code: String, locale: Locale = Locale.getDefault()): String {
    val normalized = code.trim().uppercase(Locale.ROOT)
    if (normalized.isEmpty()) return ""
    val platformName = runCatching { Currency.getInstance(normalized).displayName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != normalized }
    if (platformName != null) return platformName
    return currencyCatalog.firstOrNull { it.code == normalized }?.name ?: normalized
}

/**
 * The symbol to show for [code], or the code when the platform has no symbol for
 * it. Same tolerance rule as [currencyDisplayName].
 */
fun currencySymbol(code: String, locale: Locale = Locale.getDefault()): String {
    val normalized = code.trim().uppercase(Locale.ROOT)
    if (normalized.isEmpty()) return ""
    return runCatching { Currency.getInstance(normalized).getSymbol(locale) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: normalized
}

/**
 * The stored currency code, or null when the user has never chosen one (in which
 * case the app follows the device locale — the pre-existing behaviour).
 *
 * Pure and tolerant: a blank or absent value reads as "not chosen".
 */
fun readStoredCurrencyCode(prefs: SharedPreferences): String? =
    prefs.getString(PREF_CURRENCY_CODE, null)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Stores [code]. Blank clears the choice, so "follow my device" stays reachable
 * rather than being a state only a fresh install can be in.
 */
fun writeStoredCurrencyCode(prefs: SharedPreferences, code: String?) {
    val normalized = code?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
    // The KTX extension, not `edit().apply {}.apply()`: same commit, and lint's
    // UseKtx check is right that the nested `apply` reads as a mistake.
    prefs.edit {
        if (normalized == null) remove(PREF_CURRENCY_CODE) else putString(PREF_CURRENCY_CODE, normalized)
    }
}

/** Opens the preference file the currency code lives in. */
fun currencyPreferences(context: android.content.Context): SharedPreferences =
    context.getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE)
