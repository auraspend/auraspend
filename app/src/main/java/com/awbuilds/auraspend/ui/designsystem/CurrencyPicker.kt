package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import java.util.Locale

/**
 * The currency list, shared by onboarding and Settings.
 *
 * One widget in two hosts on purpose: onboarding shows it inline and Settings
 * shows it in a sheet, and a second implementation would drift from this one the
 * first time either changed.
 *
 * Rows are `heightIn(min = 48.dp)` rather than a fixed height, and the whole list
 * scrolls, so it survives 200 % font scale. The device's own currency is offered
 * first (the likeliest answer), then everything alphabetically by code.
 */
@Composable
fun CurrencyPickerList(
    selectedCode: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault()
) {
    val deviceCode = runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull()
    val ordered = orderedCurrencies(deviceCode)

    Column(modifier = modifier.fillMaxWidth()) {
        // Said out loud rather than discovered: the app formats amounts, it does
        // not convert them. Picking USD on a ₹1,500 transaction renders
        // "$1,500.00", and a user who assumed an exchange rate would be badly
        // misled about their money.
        //
        // It sits ABOVE the list, and that placement is load-bearing. A Column
        // measures its children in order and hands each the space the previous
        // ones left, so a non-weighted LazyColumn placed *first* takes the entire
        // bounded height and this note is then measured against zero remaining —
        // it renders as nothing at all. That is exactly what the first on-device
        // run showed (2026-10-11): the caveat was missing from the accessibility
        // tree while the list and the button were fine. Do not move it below.
        Text(
            text = stringResource(R.string.currency_picker_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AuraSpacing.xs, vertical = AuraSpacing.sm)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = AuraSpacing.sm)
        ) {
            items(ordered, key = { it.code }) { currency ->
                CurrencyRow(
                    currency = currency,
                    locale = locale,
                    selected = currency.code == selectedCode,
                    onClick = { onSelect(currency.code) }
                )
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    currency: CatalogCurrency,
    locale: Locale,
    selected: Boolean,
    onClick: () -> Unit
) {
    val name = currencyDisplayName(currency.code, locale)
    val symbol = currencySymbol(currency.code, locale)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = AuraSpacing.xs, vertical = AuraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
    ) {
        // The symbol is the fastest way to recognise a currency; the code is the
        // unambiguous one. Show both, name in between.
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = currency.code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                // The row already announces its state through `selected`; a
                // content description here would make TalkBack say it twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
