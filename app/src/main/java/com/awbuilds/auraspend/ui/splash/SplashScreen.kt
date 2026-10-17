package com.awbuilds.auraspend.ui.splash

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.designsystem.AuraGradients
import com.awbuilds.auraspend.ui.designsystem.AuraMarkMedallion
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import kotlinx.coroutines.delay

/**
 * Brand splash: the terracotta mark settles in with a spring, the wordmark fades
 * up, then we hand off to the app. Kept under two seconds — first impressions
 * are quick.
 */
@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }
    var footerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(250)
        textVisible = true
        delay(250)
        footerVisible = true
        delay(1100)
        onAnimationFinished()
    }

    val markScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "markScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Soft terracotta glow behind the mark.
        Box(
            modifier = Modifier
                .size(340.dp)
                .clip(RoundedCornerShape(170.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0f)
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.statusBarsPadding()
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = tween(400)
                )
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = markScale
                        scaleY = markScale
                    }
                ) {
                    AuraMarkMedallion(size = 156.dp)
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.xl))

            AnimatedVisibility(
                visible = textVisible,
                enter = fadeIn(animationSpec = tween(400)) +
                        slideInVertically(animationSpec = tween(400)) { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The wordmark is the app name resource (so a flavour that
                    // renames the app keeps the splash consistent) and the tagline
                    // is translated, not a literal — the literal was invisible to
                    // the EN/hi parity check, which only compares two XML files.
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.xs))
                    Text(
                        stringResource(R.string.splash_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = footerVisible,
            enter = fadeIn(animationSpec = tween(400)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = AuraSpacing.xxxl)
        ) {
            Text(
                stringResource(R.string.splash_open_source_license),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
