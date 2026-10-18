package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.ui.theme.extendedColors

/**
 * The Quiet Ledger chip: the one selection control the whole app shares.
 *
 * A 32 dp pill that fills with ink when selected, so "this is the choice" reads
 * the same on the Activity filter row, in the Quick Add sheet and in the
 * transaction editor. A categorical chip carries its colour as a dot — the dot is
 * the only place a category colour appears in chrome; the row itself stays
 * monochrome (concept rule 1: chrome is monochrome, data is colour).
 *
 * The pill sits inside a 48 dp touch target so the row stays compact while the
 * target stays finger-sized. `heightIn`, never `height`: at a 200 % font scale the
 * pill grows with its label instead of clipping it.
 */
@Composable
fun AuraChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.onBackground else scheme.surface,
        animationSpec = AuraMotion.standard(),
        label = "chipContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) scheme.onBackground else scheme.outlineVariant,
        animationSpec = AuraMotion.standard(),
        label = "chipBorder"
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(CircleShape)
                .background(container)
                .border(1.dp, border, CircleShape)
                .padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dotColor != null) {
                ChipDot(color = dotColor, onInk = selected)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                label,
                style = AuraType.chipLabel,
                // `inkSoft`, not `onSurfaceVariant`: an unselected chip is a
                // label you can still read, not a disabled control. The concept
                // reserves the muted grey for meta text.
                color = if (selected) scheme.background else MaterialTheme.extendedColors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Category dot. On an ink-filled chip the dot gets a paper halo, the same trick
 * the concept's CSS plays with a box-shadow, so a dark dot (Rent, Transfer,
 * Subscriptions) does not disappear into the fill.
 */
@Composable
private fun ChipDot(color: Color, onInk: Boolean) {
    Box(
        modifier = Modifier
            .size(if (onInk) 11.dp else 7.dp)
            .clip(CircleShape)
            .background(
                if (onInk) MaterialTheme.colorScheme.background.copy(alpha = 0.25f)
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}
