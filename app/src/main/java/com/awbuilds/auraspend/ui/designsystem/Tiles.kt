package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.theme.extendedColors
import androidx.compose.ui.res.stringResource

/**
 * The tiles Home carries above the fold: a quick action and a budget with a ring.
 *
 * Both existed in the Aurora home (0.1.71) and were dropped by the Quiet Ledger
 * pass, which reduced Home to a glance. They come back here in the concept's
 * language — flat paper, hairline edges, ink figures, colour only where it is
 * data — because a glance can still be a *rich* glance, and these two are the
 * elements a money app is opened for: a way to record something, and how the
 * budgets are doing.
 *
 * They live in the design system rather than in the screen so the shape, the
 * tokens and the 200 % font-scale behaviour are decided once.
 */

/**
 * A quick action: an icon in a tinted disc with a caption under it.
 *
 * The disc is the only tint on the screen that is not data, and it is the
 * concept's own `onBackground` at 6 % — the same quiet disc the first-run
 * screens use for their medallion, so the row does not read as a second brand.
 */
@Composable
fun AuraQuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = AuraSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        // Two lines, so a label that is longer than the tile wraps instead of
        // being cut. `heightIn`, never a fixed height: at 200 % the caption must
        // be allowed to grow.
        //
        // The caption is the nav-label size, not the chip size: four tiles
        // share one screen width, and at a 200 % font scale a 12 sp caption
        // breaks "Expense" across two lines mid-word ("Expen / se"). The icon
        // carries the meaning; the caption only names it.
        Text(
            label,
            style = AuraType.navLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(
                min = with(LocalDensity.current) { AuraType.navLabel.lineHeight.toDp() } * 2
            )
        )
    }
}

/**
 * One budget in the Home carousel: a ring, the category's name, and what it has
 * spent of its limit.
 *
 * The ring is ink, not the category's colour: the concept spends colour on data
 * and the number of a budget is not a category. Brick appears **only** when the
 * budget is over, because that is a status, not a decoration.
 */
@Composable
fun AuraBudgetRingCard(
    name: String,
    emoji: String?,
    color: Color,
    spent: Double,
    limit: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val progress = if (limit > 0) (spent / limit).toFloat() else 0f
    val overBudget = limit > 0 && spent > limit
    val statusColor = MaterialTheme.extendedColors.expenseAmount

    AuraCard(
        // 188dp, not Aurora's 168dp: the category name sits beside the ring,
        // and at a 200 % font scale a 13sp name needs ~100 dp — the narrower
        // card broke "Grocery" across two lines ("Grocer / y"). The wider card
        // holds the name, and the carousel still shows a peek of the next one.
        modifier = modifier.width(188.dp),
        style = AuraCardStyle.Outlined,
        contentPadding = PaddingValues(AuraSpacing.lg),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuraProgressRing(
                progress = progress,
                color = if (overBudget) statusColor else MaterialTheme.colorScheme.onBackground,
                trackColor = MaterialTheme.extendedColors.track,
                modifier = Modifier.size(36.dp),
                stroke = 3.dp,
                contentDescription = stringResource(
                    R.string.home_budget_percent_used,
                    name,
                    // The true percentage, not the ring's capped sweep: a budget
                    // that is 128 % used must not be announced as "100 percent".
                    (progress * 100).toInt()
                )
            ) {
                CategoryAvatar(icon = emoji, color = color, size = 22.dp)
            }
            Spacer(modifier = Modifier.width(AuraSpacing.sm))
            // Category names are user-entered and routinely longer than the ring's
            // row allows ("Food & dining" was clipped to "Food &"). The name gets
            // its own line so it is never silently truncated, and two lines are
            // reserved so every card in the carousel is the same height — a card
            // whose name wrapped put its amount a line lower than its neighbour.
            val nameLineHeight = AuraType.budgetName.lineHeight
            Text(
                name,
                style = AuraType.budgetName,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(
                    min = with(LocalDensity.current) { nameLineHeight.toDp() } * 2
                )
            )
        }
        Spacer(modifier = Modifier.height(AuraSpacing.md))
        Text(
            formatMoney(spent),
            style = AuraType.budgetAmount,
            color = if (overBudget) statusColor else MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(R.string.home_budget_of, formatMoney(limit)),
            style = AuraType.budgetSub,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AuraSpacing.xs))
        if (overBudget) {
            Text(
                stringResource(
                    R.string.home_over,
                    formatMoney(spent - limit)
                ),
                style = AuraType.budgetSub,
                color = statusColor
            )
        }
    }
}
