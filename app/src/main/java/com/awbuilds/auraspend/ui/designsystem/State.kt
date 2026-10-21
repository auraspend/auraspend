package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.theme.extendedColors

/**
 * Failure counterpart to [AuraEmptyState].
 *
 * The design system had an empty state but no error state, so a failed load was
 * indistinguishable from "you have no data" — the worst possible thing to show a
 * user who is looking at their bank balance. Every recoverable failure should
 * render this with a retry action.
 */
@Composable
fun AuraErrorState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.xxl, vertical = AuraSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(scheme.error.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(AuraSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(AuraSpacing.lg))
            Button(onClick = onAction) {
                Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AuraSpacing.sm))
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Compact metric tile: a label, a value, and an optional trailing delta or slot.
 *
 * The analytics and plan screens each hand-rolled this shape a dozen times, with
 * drifting padding and font sizes. One component keeps the grid honest.
 *
 * The label/value pair is collapsed into a single accessibility node with a
 * [stateDescription], so TalkBack reads "Total spent, ₹4,200" as one utterance
 * instead of two disconnected fragments.
 */
@Composable
fun AuraStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    /**
     * Draw the tile's own tonal container. Set false when the tile already sits
     * inside an [AuraCard] — otherwise you get a container inside a container,
     * which reads as a rendering bug rather than a metric.
     */
    contained: Boolean = true
) {
    val shape = MaterialTheme.shapes.large
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(clickModifier)
            .then(if (contained) Modifier.clip(shape) else Modifier)
            // Tonal container rather than a shadow: matches the card language and
            // keeps the tile readable on both light and true-black surfaces.
            .then(
                if (contained) Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                else Modifier
            )
            // heightIn, not height: the tile must grow with the user's font scale
            // instead of clipping its own contents at 200%.
            .heightIn(min = if (contained) 56.dp else 0.dp)
            .padding(
                horizontal = if (contained) AuraSpacing.lg else 0.dp,
                vertical = if (contained) AuraSpacing.md else 0.dp
            )
            .semantics(mergeDescendants = true) {
                contentDescription = supporting?.let { "$label, $value, $it" } ?: "$label, $value"
            },
        verticalArrangement = Arrangement.spacedBy(AuraSpacing.xs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = AuraType.moneyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A labelled progress readout (budget used, goal progress) that stays legible to
 * screen readers: the percentage is exposed as a [stateDescription] rather than
 * being conveyed by the ring alone.
 */
@Composable
fun AuraProgressLabel(
    label: String,
    progress: Float,
    modifier: Modifier = Modifier,
    percent: Int = (progress.coerceIn(0f, 1f) * 100).toInt(),
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier.widthIn(min = 0.dp),
        verticalArrangement = Arrangement.spacedBy(AuraSpacing.xxs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$percent%",
                style = AuraType.moneySmall,
                color = valueColor,
                modifier = Modifier.clearAndSetSemantics {
                    stateDescription = "$percent percent"
                }
            )
        }
    }
}

/**
 * Inline, dismissible failure banner for an action that failed inside an otherwise
 * usable screen (a save, a delete, a classification).
 *
 * Distinct from [AuraErrorState], which replaces the whole screen because there is
 * nothing to show. Here the user can see their data *and* the failure, so the banner
 * must not hide it.
 */
@Composable
fun AuraErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.errorContainer)
            .padding(start = AuraSpacing.md, top = AuraSpacing.md, bottom = AuraSpacing.md)
            // Announced when it appears, so a screen-reader user is told the save failed
            // rather than silently losing the feedback.
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = scheme.onErrorContainer,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(AuraSpacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(AuraSpacing.sm))
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = scheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * A quiet strip that says something changed and offers the one action it
 * implies.
 *
 * Deliberately not [AuraErrorBanner]: an available app update is information,
 * not a failure, and painting it in `errorContainer` teaches people to read red
 * as "ignore me". The tonal container keeps it above the canvas without
 * raising its voice, and the dismiss is optional so a notice the user cannot
 * clear (an update mid-download, say) does not offer a dead tap target.
 */
@Composable
fun AuraNoticeBanner(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainer)
            .padding(start = AuraSpacing.md, top = AuraSpacing.sm, bottom = AuraSpacing.sm)
            // Announced politely: this is news, not an alarm.
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(AuraSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.inkSoft
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = MaterialTheme.extendedColors.inkSoft
                )
            }
        }
    }
}
