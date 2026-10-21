package com.awbuilds.auraspend.ui.core

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.designsystem.AuraMotion
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.softShadow
import com.awbuilds.auraspend.ui.theme.extendedColors

/**
 * Top-level destinations shown in the app chrome.
 *
 * The label is a string resource id, not a literal: it is the persistent bottom
 * bar's visible text *and* its accessibility label on every screen, so a
 * hardcoded English name left the whole app chrome untranslated.
 */
enum class TopLevelDestination(
    /**
     * The route the destination was registered with — what
     * `NavDestination.route` holds, so this is what selection compares against.
     */
    val route: String,
    /**
     * The route `navigate()` takes. Same as [route] unless the destination has
     * arguments, in which case [route] is a `{placeholder}` template and
     * navigating to it fills the arguments with those literal placeholders.
     */
    val navigateRoute: String = route,
    // `@param:` is explicit: the annotation is meant for the constructor
    // parameter only, and Kotlin warns that its default target will grow to
    // include the backing field. It is what makes lint verify each `R.string` id.
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    HOME("home", labelRes = R.string.home_title, icon = Icons.Outlined.Home),
    ACTIVITY(
        // The registered pattern — placeholders are a template, not arguments.
        "activity?categoryId={categoryId}&query={query}",
        // What `navigate()` takes. Both args have defaults, so the bare route
        // means "no filter". `Routes.ACTIVITY` is derived from this pair.
        "activity",
        R.string.activity_title,
        Icons.AutoMirrored.Outlined.ListAlt
    ),
    PLAN("plan", labelRes = R.string.plan_title, icon = Icons.Outlined.Adjust),
    INSIGHTS("insights", labelRes = R.string.insights_title, icon = Icons.Outlined.BarChart)
}

/**
 * Quiet Ledger app frame.
 *
 * Compact widths get a flat bottom bar with a hairline top edge and a centre
 * add button; medium and expanded widths get a navigation rail so tablets and
 * foldables feel native instead of stretched. Chrome is only shown on top-level
 * destinations.
 *
 * The bar used to be a floating rounded pill on a tonal surface, which read as a
 * second card stacked under the content. A hairline edge separates the chrome
 * from the page without competing with the cards above it.
 *
 * **`content()` has exactly one call site, and that is load-bearing.** It used to
 * be called from three branches (no chrome / rail / bottom bar), which meant that
 * every navigation between a top-level screen and a detail screen — Activity →
 * a transaction, and the back out of it — moved the whole `NavHost` subtree to a
 * different position in the composition. Compose disposes a subtree whose
 * position changes and rebuilds it from scratch, so every such crossing tore down
 * and recreated the entire navigation host, mid-transition. That is the hitch the
 * owner reported when going "deeper and deeper, mainly when returning back". The
 * chrome is now a sibling that appears or disappears around a stable slot.
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

        Row(modifier = Modifier.fillMaxSize()) {
            if (expanded && showChrome) {
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
                            onClick = { onNavigate(destination.navigateRoute) },
                            icon = { Icon(destination.icon, contentDescription = label) },
                            label = { Text(label) },
                            alwaysShowLabel = true
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                // The one and only `content()` call site.
                Box(modifier = Modifier.weight(1f)) { content() }
                if (!expanded && showChrome) {
                    AuraBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = onNavigate,
                        onAddClick = onAddClick
                    )
                }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column {
            // A hairline, not a shadow: the same edge the cards use, so the bar
            // reads as part of the page rather than a slab floating over it.
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = AuraSpacing.sm)
                    .heightIn(min = 62.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(
                    destination = TopLevelDestination.HOME,
                    selected = currentRoute == TopLevelDestination.HOME.route,
                    onClick = { onNavigate(TopLevelDestination.HOME.navigateRoute) },
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    destination = TopLevelDestination.ACTIVITY,
                    selected = currentRoute == TopLevelDestination.ACTIVITY.route,
                    onClick = { onNavigate(TopLevelDestination.ACTIVITY.navigateRoute) },
                    modifier = Modifier.weight(1f)
                )
                AddButton(onClick = onAddClick, modifier = Modifier.weight(1f))
                NavItem(
                    destination = TopLevelDestination.PLAN,
                    selected = currentRoute == TopLevelDestination.PLAN.route,
                    onClick = { onNavigate(TopLevelDestination.PLAN.navigateRoute) },
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    destination = TopLevelDestination.INSIGHTS,
                    selected = currentRoute == TopLevelDestination.INSIGHTS.route,
                    onClick = { onNavigate(TopLevelDestination.INSIGHTS.navigateRoute) },
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
    // Ink, not a brand sweep. The add button is the one control on every screen;
    // it should read as the darkest object on the page, not as decoration.
    val shape = RoundedCornerShape(19.dp)

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
                .background(MaterialTheme.colorScheme.onBackground)
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
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(26.dp)
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
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.extendedColors.faint,
        animationSpec = AuraMotion.standard(),
        label = "navTint"
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
        Icon(
            destination.icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(AuraSpacing.xs))
        Text(
            label,
            style = AuraType.navLabel,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
