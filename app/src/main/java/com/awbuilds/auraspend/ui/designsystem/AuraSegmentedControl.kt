package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Aurora segmented control — the app-wide replacement for `TabRow` and
 * hand-rolled "sliding selectors".
 *
 * The indicator glides with an expressive spring and taps give a light haptic
 * tick, so switching ranges or filters feels physical.
 */
@Composable
fun AuraSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // heightIn, not height: these boxes hold the option labels, and a fixed
            // 44dp clipped them at the largest system font scale on every screen
            // that uses this control. The 44dp floor keeps 100% identical.
            .heightIn(min = 46.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = AuraMotion.expressive(),
            label = "segmentedIndicator"
        )

        Box(
            modifier = Modifier
                // matchParentSize, not fillMaxHeight: the indicator then takes the
                // height the labels resolved (so it grows with them) without
                // influencing that measurement. A size-determining fillMaxHeight
                // here would balloon the control to whatever height the parent
                // offers.
                .matchParentSize()
                .offset(x = indicatorX)
                .padding(AuraSpacing.xs)
                .width(segmentWidth - AuraSpacing.sm)
                .fillMaxHeight()
                .clip(CircleShape)
                // The selected segment is the terracotta moment of the control:
                // a solid brand fill instead of a white pill on a light track.
                .background(AuraGradients.aurora)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) AuraGradients.onAurora else scheme.onSurfaceVariant,
                    animationSpec = AuraMotion.standard(),
                    label = "segmentedText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // The label decides the height; the floor keeps the touch
                        // target at the token size.
                        .heightIn(min = 44.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(index)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
