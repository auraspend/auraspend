package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Brand color for a stored category ARGB value. */
fun categoryColor(colorInt: Int?): Color =
    Color(colorInt?.toLong() ?: 0xFF757575L)

/**
 * Time of day for a transaction, in the user's locale — "8:42 pm".
 *
 * The row used to print a relative day ("Today", "2d ago"), which duplicated the
 * group header directly above it in Activity ("Today" under "TODAY") while saying
 * nothing about the row itself. The header carries the day; the row carries the
 * time, which is the question a statement reader actually has. It resolves
 * through the locale so the time formatting follows the device, not this file.
 */
@Composable
private fun rowTime(date: LocalDateTime): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(date)
    }
}

/**
 * The transaction row used by Home and Activity. Merges semantics so TalkBack
 * reads merchant, category, date and amount as a single item.
 *
 * **Outgoing amounts are ink, not red.** Red used to mark every expense, which
 * made a list of thirty rows a wall of alarm and left nothing to distinguish a
 * real problem (an over-budget category, a failed write). The minus sign already
 * says "out"; green is reserved for money arriving, and red for status.
 */
@Composable
fun TransactionEntryRow(
    transaction: Transaction,
    categoryName: String,
    categoryColor: Color,
    categoryEmoji: String?,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val extended = MaterialTheme.extendedColors
    val amountColor =
        if (transaction.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.onSurface
        else extended.incomeAmount
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Opaque on purpose, and not a style choice.
            //
            // On Activity this row is the foreground of a SwipeToDismissBox whose
            // backgroundContent is a red errorContainer plus a delete icon. A
            // transparent foreground cannot hide its own background, so the delete
            // state showed through on every row at rest and the trash icon landed
            // on top of the amount. Found on-device 2026-09-29: all 32 rows dark
            // red with unreadable amounts.
            //
            // Both call sites sit directly on the page background (neither is
            // wrapped in a card), so painting it is correct in both. A new call
            // site inside a card must pass its own container colour via [modifier]
            // rather than relying on this being transparent.
            .background(MaterialTheme.colorScheme.background)
            .semantics(mergeDescendants = true) {}
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = AuraSpacing.gutter)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 11dp, the concept's row rhythm: 36dp avatar + 11 top and
                // bottom = the 58dp row the transaction lists are built on.
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryAvatar(
                icon = categoryEmoji,
                color = categoryColor,
                size = 36.dp,
                showRing = false
            )
            Spacer(modifier = Modifier.width(AuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.merchant ?: transaction.note.ifBlank { categoryName },
                    style = AuraType.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                // Read outside the string template: rowTime is @Composable, and
                // an explicit local keeps the subtitling obvious at a glance.
                val time = rowTime(transaction.date)
                Text(
                    "$categoryName · $time",
                    style = AuraType.rowMeta,
                    color = extended.textLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(AuraSpacing.sm))
            Text(
                formatSignedMoney(
                    if (transaction.type == TransactionType.EXPENSE) -transaction.amount
                    else transaction.amount
                ),
                style = AuraType.rowMoney,
                color = amountColor
            )
        }
        if (divider) {
            HorizontalDivider(
                thickness = 1.dp,
                // The concept's inner hairline (`--line-2`), a shade lighter than
                // a card's own border: thirty dividers at border weight turn a
                // list into a grid.
                color = MaterialTheme.extendedColors.lineSoft
            )
        }
    }
}
