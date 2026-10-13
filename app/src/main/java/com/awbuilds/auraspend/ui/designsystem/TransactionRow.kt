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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Brand color for a stored category ARGB value. */
fun categoryColor(colorInt: Int?): Color =
    Color(colorInt?.toLong() ?: 0xFF757575L)

/** One day in milliseconds, named so the day boundaries below stay readable. */
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Human date for a transaction timestamp.
 *
 * Resolves through resources, because this feeds the date line of every row in
 * Home and Activity — the two highest-traffic lists in the app. It used to return
 * literal `"Today"` / `"Yesterday"` / `"3d ago"` from inside the shared design
 * system, so those lists read English in every locale. Dates older than a week
 * keep the locale-aware `dd MMM` format.
 */
@Composable
fun formatRelativeDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val days = (diff / DAY_MILLIS).toInt()
    return when {
        diff < DAY_MILLIS -> stringResource(R.string.activity_group_today)
        diff < 2 * DAY_MILLIS -> stringResource(R.string.activity_group_yesterday)
        diff < 7 * DAY_MILLIS -> pluralStringResource(R.plurals.activity_days_ago, days, days)
        else -> {
            // LocalConfiguration rather than Locale.getDefault(): this is a
            // composable, and the default locale is not observable, so the month
            // name would keep the language the screen was first composed in
            // (lint's NonObservableLocale). Same reasoning as `Money.kt`.
            val locale = LocalConfiguration.current.locales[0]
            SimpleDateFormat("dd MMM", locale).format(Date(timestamp))
        }
    }
}

/**
 * The transaction row used by Home and Activity. Merges semantics so TalkBack
 * reads merchant, category, date and amount as a single item.
 */
@Composable
fun TransactionEntryRow(
    transaction: Transaction,
    categoryName: String,
    categoryColor: Color,
    categoryEmoji: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val extended = MaterialTheme.extendedColors
    val amountColor =
        if (transaction.type == TransactionType.EXPENSE) extended.expenseAmount
        else extended.incomeAmount

    Row(
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
            .padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.sm + AuraSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(
            icon = categoryEmoji,
            color = categoryColor,
            size = 46.dp,
            showRing = true,
            ringStroke = 1.5.dp
        )
        Spacer(modifier = Modifier.width(AuraSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                transaction.merchant ?: transaction.note.ifBlank { categoryName },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            // Read outside the string template: stringResource is @Composable, and
            // an explicit local keeps the subtitling obvious at a glance.
            val relativeDate = formatRelativeDate(
                transaction.date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            Text(
                "$categoryName · $relativeDate",
                style = MaterialTheme.typography.bodySmall,
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
            style = AuraType.moneySmall,
            color = amountColor
        )
    }
}
