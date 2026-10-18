package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The Quiet Ledger segmented control — a quiet range selector, not a second
 * navigation bar.
 *
 * It changes *data*, never the screen, so it is drawn as a recessed track with a
 * raised white pill rather than as tabs: a 12 dp track on a 6 % ink wash, a 9 dp
 * pill on `surface` with a hairline shadow, and the same 12 sp/600 label as
 * [AuraChip] so the two controls read as siblings.
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
    val trackShape = RoundedCornerShape(12.dp)
    val pillShape = RoundedCornerShape(9.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            // color-mix(in srgb, var(--ink) 6%, transparent) over the page: an
            // alpha wash, so it sits correctly on paper and on a card alike.
            .background(scheme.onBackground.copy(alpha = 0.06f))
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = AuraMotion.expressive(),
            label = "segmentedIndicator"
        )

        // `matchParentSize` reports the track's resolved height without
        // influencing it, but it also hands the child *fixed* constraints — and a
        // fixed width silently wins over `.width()`, which made the pill paint the
        // whole track instead of one segment. `wrapContentSize` loosens the
        // minimums again while keeping the maximums, so the pill can be one
        // segment wide and still be as tall as the labels resolved to.
        Box(
            modifier = Modifier
                .matchParentSize()
                .wrapContentSize(Alignment.TopStart)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorX)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .softShadow(pillShape, elevation = 2.dp)
                    .clip(pillShape)
                    .background(scheme.surface)
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    animationSpec = AuraMotion.standard(),
                    label = "segmentedText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // heightIn, not height: these boxes hold the option labels,
                        // and a fixed height clips them at the largest system font
                        // scale. 32 dp is the concept's pill and the floor here.
                        .heightIn(min = 32.dp)
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
                        style = AuraType.chipLabel,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
