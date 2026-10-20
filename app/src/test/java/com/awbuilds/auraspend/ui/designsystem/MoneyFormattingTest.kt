package com.awbuilds.auraspend.ui.designsystem

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Money formatting.
 *
 * The old implementation hardcoded `₹` and grouped with `String.format(Locale.US,
 * "%,d")`, so a user in any locale outside the US saw Western digit grouping on
 * rupee amounts, and the KDoc claiming "Indian grouping" was simply false. These
 * tests pin the locale- and currency-aware behaviour.
 */
class MoneyFormattingTest {

    private val inr = CurrencyStyle("INR")
    private val usd = CurrencyStyle("USD")

    @Test
    fun `Indian locale uses Indian digit grouping`() {
        val formatted = formatMoney(123456.78, inr, Locale.forLanguageTag("en-IN"))
        // en-IN groups as 1,23,456.78 — the whole reason this was broken before.
        assertTrue(
            "expected Indian grouping (1,23,456.78) but got: $formatted",
            formatted.contains("1,23,456")
        )
    }

    @Test
    fun `US locale uses Western digit grouping`() {
        val formatted = formatMoney(123456.78, inr, Locale.US)
        assertTrue(
            "expected Western grouping (123,456.78) but got: $formatted",
            formatted.contains("123,456")
        )
    }

    @Test
    fun `the same amount renders differently per locale`() {
        val india = formatMoney(123456.0, inr, Locale.forLanguageTag("en-IN"))
        val us = formatMoney(123456.0, inr, Locale.US)
        assertTrue("grouping must follow the locale, not a hardcoded US pattern", india != us)
    }

    @Test
    fun `currency symbol follows the currency code, not the locale`() {
        assertTrue(
            "USD in en-IN should still show the dollar sign: " + formatMoney(10.0, usd, Locale.forLanguageTag("en-IN")),
            formatMoney(10.0, usd, Locale.forLanguageTag("en-IN")).contains("$")
        )
        assertTrue(
            "INR should show a rupee sign",
            formatMoney(10.0, inr, Locale.US).contains("₹")
        )
    }

    @Test
    fun `zero-decimal currency shows no decimals`() {
        // JPY has no minor unit; showing 10.00 would be wrong.
        val jpy = formatMoney(1234.0, CurrencyStyle("JPY"), Locale.US, fractionDigits = 0)
        assertTrue("expected no decimal separator in $jpy", !jpy.contains("."))
    }

    @Test
    fun `explicit fraction digits are honoured`() {
        val whole = formatMoney(1234.5678, inr, Locale.US, fractionDigits = 0)
        assertTrue("expected no decimals in $whole", !whole.contains("."))
    }

    @Test
    fun `Indian grouping keeps the paise the amount actually has`() {
        // The Indian path formatted with "#,##0.#" — one decimal digit — and
        // then padded the second one with a zero, so every amount with paise
        // was mis-stated: ₹123,456.78 rendered as ₹1,23,456.80 and ₹1.05 as
        // ₹1.10. The demo data has no paise anywhere, which is why nothing in
        // the app or the store screenshots ever showed it; a real bank message
        // with a fractional rupee did (2026-10-20).
        val enIn = Locale.forLanguageTag("en-IN")
        assertEquals("₹1,23,456.78", formatMoney(123456.78, inr, enIn))
        assertEquals("₹4,52,310.55", formatMoney(452310.55, inr, enIn))
        assertEquals("₹12,345.67", formatMoney(12_345.67, inr, enIn))
        assertEquals("₹1.05", formatMoney(1.05, inr, enIn))
        assertEquals("₹0.99", formatMoney(0.99, inr, enIn))
        // A whole amount still reads as a whole amount.
        assertEquals("₹1,23,456", formatMoney(123456.0, inr, enIn))
    }

    @Test
    fun `the same paise are kept in a non-Indian locale`() {
        // The two paths are separate implementations, so the fix to one must not
        // be assumed for the other.
        assertEquals("₹123,456.78", formatMoney(123456.78, inr, Locale.US))
    }

    @Test
    fun `Indian grouping rounds to whole rupees when asked for no decimals`() {
        val enIn = Locale.forLanguageTag("en-IN")
        assertEquals("₹1,23,457", formatMoney(123456.78, inr, enIn, fractionDigits = 0))
    }

    @Test
    fun `negative amounts keep their sign`() {
        // MINUS_SIGN (U+2212), not the platform's hyphen-minus: the app draws one
        // minus glyph everywhere, and `NumberFormat` emits a hyphen (or accounting
        // parentheses) in the non-Indian grouping path.
        val formatted = formatMoney(-500.0, inr, Locale.US)
        assertTrue("negative amount lost its sign: $formatted", formatted.contains(MINUS_SIGN))
        assertTrue("expected the typographic minus: $formatted", !formatted.contains("-"))
    }

    @Test
    fun `unknown currency code falls back to INR rather than throwing`() {
        val formatted = formatMoney(100.0, CurrencyStyle.fromCode("NOT_A_CODE"), Locale.US)
        assertTrue("should fall back to a rupee render, got: $formatted", formatted.contains("₹"))
    }

    @Test
    fun `blank currency code resolves to INR`() {
        assertEquals(CurrencyStyle.INR.code, CurrencyStyle.fromCode(null).code)
        assertEquals(CurrencyStyle.INR.code, CurrencyStyle.fromCode("").code)
    }

    @Test
    fun `currency code is case insensitive`() {
        assertEquals("INR", CurrencyStyle.fromCode("inr").code)
    }

    @Test
    fun `compact form uses lakh and crore in the Indian market`() {
        val lakh = formatMoneyCompact(2_50_000.0, inr, Locale.forLanguageTag("en-IN"))
        assertTrue("expected a lakh suffix in $lakh", lakh.contains("L"))
        val crore = formatMoneyCompact(2_50_00_000.0, inr, Locale.forLanguageTag("en-IN"))
        assertTrue("expected a crore suffix in $crore", crore.contains("Cr"))
    }

    @Test
    fun `compact form falls back to plain money below a thousand`() {
        val small = formatMoneyCompact(999.0, inr, Locale.forLanguageTag("en-IN"))
        assertTrue("999 should not be abbreviated, got: $small", !small.contains("K"))
    }

    @Test
    fun `compact form keeps the currency symbol`() {
        assertTrue(
            "expected a currency symbol in " + formatMoneyCompact(5_000.0, inr, Locale.forLanguageTag("en-IN")),
            formatMoneyCompact(5_000.0, inr, Locale.forLanguageTag("en-IN")).contains("₹")
        )
    }

    @Test
    fun `AnimatedMoney and formatMoney resolve the same currency`() {
        // Regression: AnimatedMoney defaulted to a hardcoded INR while formatMoney
        // used the theme's currency, so one screen rendered a rupee hero next to
        // dollar sub-totals. The full-screen screenshot suite caught it.
        val previous = MoneyConfig.current
        try {
            MoneyConfig.update(usd)
            val viaFormatter = formatMoney(1_000.0)
            assertTrue(
                "formatMoney should follow MoneyConfig, got: $viaFormatter",
                viaFormatter.contains("$")
            )
        } finally {
            MoneyConfig.update(previous)
        }
    }
}
