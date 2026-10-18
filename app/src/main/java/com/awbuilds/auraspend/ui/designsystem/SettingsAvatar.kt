package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R

/** The concept's `.avatar{width:38px;height:38px}`. */
private val AvatarSize = 38.dp

/** AGENTS.md's touch-target floor, which the visible art does not meet. */
private val AvatarTouchTarget = 48.dp

/**
 * Header avatar: the single entry point to Settings on every top-level tab.
 * Kept in the design system so all four tabs stay identical.
 *
 * 38 dp of visible art inside a 48 dp touch target — the AGENTS.md floor. The
 * target is wider and taller than the art without being taller than the header it
 * sits in: [avatarSlot] reports only the art's height to the row, so a header
 * whose text column is shorter than 48 dp (Insights' title-only head, for one) is
 * still exactly as tall as the concept draws it, and the circle lines up with the
 * eyebrow beside it instead of being pushed 5 dp down by its own padding. Without
 * that, the shared target added 10–19 dp to those heads.
 *
 * The fill is the violet container with a violet hairline and no shadow — the one
 * accent the header is allowed: chrome is monochrome, but this is the one thing
 * on the header you can act on.
 */
@Composable
fun SettingsAvatarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            // 16dp of end inset: 16 + the 4dp the touch target adds around the
            // art = the concept's 20dp gutter.
            .padding(end = AuraSpacing.lg)
            .avatarSlot()
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.sizeIn(
                minWidth = AvatarTouchTarget,
                minHeight = AvatarTouchTarget
            ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = stringResource(R.string.action_settings),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Occupies the art's 38 dp in the layout while still allowing a 48 dp tap.
 *
 * The child is measured with the constraints it was given (so it can be 48 dp
 * tall) but the slot reports 38 dp to the parent, and the child is centred on it —
 * its extra 5 dp of target hang above and below the slot, where they overlap the
 * header's own padding rather than stretching it.
 */
private fun Modifier.avatarSlot(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val slot = AvatarSize.roundToPx()
    layout(placeable.width, slot) {
        placeable.place(0, (slot - placeable.height) / 2)
    }
}
