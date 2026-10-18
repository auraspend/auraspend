package com.awbuilds.auraspend.ui.splash

import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.theme.extendedColors
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import kotlinx.coroutines.delay

/**
 * Brand splash: the mark fades in, the wordmark fades up, then we hand off to
 * the app. Kept under two seconds — first impressions are quick.
 *
 * It used to sit in a 320dp radial violet glow and spring the mark in with
 * overshoot. The concept retires both moves: surfaces stay flat and type does
 * the work a gradient used to ("the gradient hero is gone"), and its motion has
 * no overshoot outside selection. What is left is the app's own icon on paper,
 * its name, and a line saying what it is.
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
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
                Icon(
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(32.dp)),
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )
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
                        style = AuraType.wordmark,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.splash_tagline),
                        style = AuraType.bodyCopy,
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
                // `faint` is the token for a line that is present but not
                // competing — an alpha on `muted` was a fourth grey with no name.
                style = AuraType.budgetSub,
                color = MaterialTheme.extendedColors.faint
            )
        }
    }
}
