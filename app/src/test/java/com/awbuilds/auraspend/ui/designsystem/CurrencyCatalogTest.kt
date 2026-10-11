package com.awbuilds.auraspend.ui.designsystem

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the hardcoded currency catalog.
 *
 * The list came from `GET https://api.frankfurter.dev/v1/currencies` and is
 * captured in source so the picker never needs the network. That makes it data
 * that can silently rot: a code typed wrong, a duplicate added, a currency the
 * platform cannot resolve. So the codes are pinned exactly — changing the list is
 * a deliberate edit to this test as well, the same way the centroid asset's
 * digest is pinned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class CurrencyCatalogTest {

    /** The 30 codes the API returned on 2026-10-11, in its own order. */
    private val apiCodes = listOf(
        "AUD", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD",
        "HUF", "IDR", "ILS", "INR", "ISK", "JPY", "KRW", "MXN", "MYR", "NOK",
        "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB", "TRY", "USD", "ZAR"
    )

    @Test
    fun `the catalog carries exactly the currencies the API returned`() {
        assertEquals(
            "the catalog drifted from the captured API response; if that is " +
                "deliberate, update this list and the comment in CurrencyCatalog.kt",
            apiCodes.sorted(),
            currencyCatalog.map { it.code }.sorted()
        )
    }

    @Test
    fun `the catalog is well formed`() {
        assertEquals(
            "no duplicates",
            currencyCatalog.size,
            currencyCatalog.map { it.code }.toSet().size
        )
        currencyCatalog.forEach { currency ->
            assertTrue(
                "${currency.code} is not a three-letter uppercase code",
                currency.code.matches(Regex("[A-Z]{3}"))
            )
            assertTrue("${currency.code} has no name", currency.name.isNotBlank())
        }
    }

    @Test
    fun `every catalog code is one the platform can resolve`() {
        // If the platform cannot resolve a code, `CurrencyStyle.fromCode` still
        // works (it falls back to the code as the symbol) but the amount would
        // render without a currency symbol at all — a formatting bug the user
        // would see on every screen. All 30 are real ISO codes; this proves it.
        currencyCatalog.forEach { currency ->
            val resolved = runCatching { Currency.getInstance(currency.code) }.getOrNull()
            assertTrue("the platform cannot resolve ${currency.code}", resolved != null)
        }
    }

    @Test
    fun `the display name is the platform's localized one`() {
        // Localized by Android, which is why 30 currency names did not become 60
        // translated strings.
        assertEquals("Indian Rupee", currencyDisplayName("INR", Locale.US))
        assertEquals("Euro", currencyDisplayName("EUR", Locale.US))
        // Case and padding are normalized rather than trusted.
        assertEquals("Indian Rupee", currencyDisplayName(" inr ", Locale.US))
        // Any locale must produce something: never blank, never a crash.
        listOf(Locale.US, Locale.UK, Locale.forLanguageTag("hi-IN"), Locale.forLanguageTag("de-DE"))
            .forEach { locale ->
                assertTrue(
                    "blank name for INR in $locale",
                    currencyDisplayName("INR", locale).isNotBlank()
                )
            }
    }

    @Test
    fun `an unknown code degrades to itself rather than blank or throwing`() {
        // Reachable: `currency_code` is a preference, and a preference can hold
        // anything a previous build or a restored backup wrote.
        assertEquals("ZZZ", currencyDisplayName("ZZZ", Locale.US))
        assertEquals("", currencyDisplayName("", Locale.US))
        assertEquals("ZZZ", currencySymbol("ZZZ", Locale.US))
        assertEquals("", currencySymbol("", Locale.US))
    }

    @Test
    fun `the device currency is offered first and the rest stay alphabetical`() {
        val ordered = orderedCurrencies("INR")
        assertEquals("INR", ordered.first().code)
        assertEquals(currencyCatalog.size, ordered.size)
        assertEquals(
            "everything after the device currency must stay alphabetical",
            ordered.drop(1).map { it.code }.sorted(),
            ordered.drop(1).map { it.code }
        )

        // No device currency (or one the catalog does not carry): plain A-Z.
        assertEquals(currencyCatalog.map { it.code }.sorted(), orderedCurrencies(null).map { it.code })
        assertEquals(currencyCatalog.map { it.code }.sorted(), orderedCurrencies("ZZZ").map { it.code })
    }

    @Test
    fun `the stored preference round trips, normalizes and clears`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = currencyPreferences(context)
        writeStoredCurrencyCode(prefs, null)

        assertNull("an install that never chose follows the device", readStoredCurrencyCode(prefs))

        writeStoredCurrencyCode(prefs, "usd")
        assertEquals("stored codes are normalized", "USD", readStoredCurrencyCode(prefs))

        writeStoredCurrencyCode(prefs, "  eur ")
        assertEquals("EUR", readStoredCurrencyCode(prefs))

        // Blank clears rather than storing an unusable code, so "follow my device"
        // stays reachable instead of being a fresh-install-only state.
        writeStoredCurrencyCode(prefs, "")
        assertNull(readStoredCurrencyCode(prefs))
        writeStoredCurrencyCode(prefs, null)
        assertNull(readStoredCurrencyCode(prefs))
    }
}
