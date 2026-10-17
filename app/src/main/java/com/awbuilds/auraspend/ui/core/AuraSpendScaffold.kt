package com.awbuilds.auraspend.ui.core

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.designsystem.AuraGradients
import com.awbuilds.auraspend.ui.designsystem.AuraMotion
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.softShadow

/**
 * Top-level destinations shown in the app chrome.
 *
 * The label is a string resource id, not a literal: it is the persistent bottom
 * bar's visible text *and* its accessibility label on every screen, so a
 * hardcoded English name left the whole app chrome untranslated.
 */
enum class TopLevelDestination(
    val route: String,
    // `@param:` is explicit: the annotation is meant for the constructor
    // parameter only, and Kotlin warns that its default target will grow to
    // include the backing field. It is what makes lint verify each `R.string` id.
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    HOME("home", R.string.home_title, Icons.Filled.Home),
    ACTIVITY("activity", R.string.activity_title, Icons.AutoMirrored.Filled.ListAlt),
    PLAN("plan", R.string.plan_title, Icons.Filled.Savings),
    INSIGHTS("insights", R.string.insights_title, Icons.Filled.BarChart)
}

/**
 * Aurora app frame.
 *
 * Compact widths get a bottom navigation bar with a center add button; medium
 * and expanded widths get a navigation rail so tablets and foldables feel
 * native instead of stretched. Chrome is only shown on top-level destinations.
 */
@Composable
fun AuraAppChrome(
    showChrome: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onAddClick: () -> Unit,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp

        if (!showChrome) {
            content()
            return@BoxWithConstraints
        }

        if (expanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    header = {
                        AddButton(
                            onClick = onAddClick,
                            modifier = Modifier.padding(top = AuraSpacing.md, bottom = AuraSpacing.sm)
                        )
                    }
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    TopLevelDestination.entries.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { onNavigate(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = label) },
                            label = { Text(label) },
                            alwaysShowLabel = true
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                Box(modifier = Modifier.weight(1f)) { content() }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) { content() }
                AuraBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    onAddClick = onAddClick
                )
            }
        }
    }
}

@Composable
private fun AuraBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val barShape = RoundedCornerShape(30.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AuraSpacing.lg, vertical = AuraSpacing.sm)
                    .heightIn(min = 66.dp)
                    .softShadow(barShape, elevation = 10.dp)
                    .clip(barShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        barShape
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(
                    destination = TopLevelDestination.HOME,
                    selected = currentRoute == TopLevelDestination.HOME.route,
                    onClick = { onNavigate(TopLevelDestination.HOME.route) },
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    destination = TopLevelDestination.ACTIVITY,
                    selected = currentRoute == TopLevelDestination.ACTIVITY.route,
                    onClick = { onNavigate(TopLevelDestination.ACTIVITY.route) },
                    modifier = Modifier.weight(1f)
                )
                AddButton(onClick = onAddClick, modifier = Modifier.weight(1f))
                NavItem(
                    destination = TopLevelDestination.PLAN,
                    selected = currentRoute == TopLevelDestination.PLAN.route,
                    onClick = { onNavigate(TopLevelDestination.PLAN.route) },
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    destination = TopLevelDestination.INSIGHTS,
                    selected = currentRoute == TopLevelDestination.INSIGHTS.route,
                    onClick = { onNavigate(TopLevelDestination.INSIGHTS.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AuraMotion.snappy(),
        label = "addButtonScale"
    )
    val shape = RoundedCornerShape(20.dp)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .softShadow(shape, elevation = 8.dp)
                .size(54.dp)
                .clip(shape)
                .background(AuraGradients.aurora)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.action_add_transaction),
                tint = AuraGradients.onAurora,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    // The gradient pill only wraps the icon, so the icon goes white on it while
    // the label — which sits on the bar's own surface — takes the terracotta.
    // Both used to be onAurora, which left the selected label white-on-cream.
    val iconTint by animateColorAsState(
        targetValue = if (selected) AuraGradients.onAurora
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = AuraMotion.standard(),
        label = "navIconTint"
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = AuraMotion.standard(),
        label = "navLabelTint"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = AuraMotion.standard(),
        label = "navPill"
    )
    val label = stringResource(destination.labelRes)

    Column(
        modifier = modifier
            // NOT fillMaxHeight(): this Row is a non-weighted child of the frame's
            // Column, so it is handed the full viewport height as its max constraint.
            // fillMaxHeight() here made every nav item screen-tall, the bar swallowed
            // the whole window and the content slot collapsed to zero height — the
            // dashboard rendered as an empty void. Wrap the content instead, with a
            // floor that keeps the touch target at 48dp.
            .heightIn(min = 48.dp)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                // Selected items sit on the terracotta gradient; the alpha fade
                // keeps the unselected state weightless.
                .background(AuraGradients.aurora, alpha = pillAlpha)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(destination.icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(AuraSpacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = labelTint,
            maxLines = 1
        )
    }
}
