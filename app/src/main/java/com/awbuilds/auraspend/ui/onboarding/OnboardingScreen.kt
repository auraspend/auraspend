package com.awbuilds.auraspend.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import androidx.compose.ui.platform.LocalConfiguration
import com.awbuilds.auraspend.ui.designsystem.AuraGradients
import com.awbuilds.auraspend.ui.designsystem.CurrencyPickerList
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class OnboardingPage(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector
)

private val onboardingPages = listOf(
    OnboardingPage(
        R.string.onboarding_page1_title,
        R.string.onboarding_page1_description,
        Icons.Default.Wallet
    ),
    OnboardingPage(
        R.string.onboarding_page2_title,
        R.string.onboarding_page2_description,
        Icons.Default.AutoAwesome
    ),
    OnboardingPage(
        R.string.onboarding_page3_title,
        R.string.onboarding_page3_description,
        Icons.Default.Savings
    ),
    // Last, and interactive: the user should know what the app will do before
    // granting SMS access on the button below, and currency is the one setting
    // whose default (their device's) they may actively want to change.
    OnboardingPage(
        R.string.onboarding_page4_title,
        R.string.onboarding_page4_description,
        Icons.Default.Payments
    )
)

/** The page that hosts the currency picker instead of only text. */
private val currencyPageIndex = onboardingPages.lastIndex

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    /**
     * The picked ISO code, or null while the user has not chosen (in which case
     * the app follows the device locale — the preselection shown on the currency
     * page). Hoisted so the choice reaches the theme immediately.
     */
    currencyCode: String? = null,
    onCurrencyChanged: (String) -> Unit = {},
    onRestoreFromDrive: () -> Unit = {},
    isRestoring: Boolean = false,
    restoreError: String? = null,
    onRestoreErrorDismissed: () -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onFinished() }

    // What the picker shows as chosen: the user's pick, or the device's own
    // currency — the same fallback `rememberCurrencyStyle` uses, so the highlighted
    // row is the currency the app is actually rendering in.
    val locale = LocalConfiguration.current.locales[0]
    val deviceCurrencyCode = remember(locale) {
        runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull()
    }
    val effectiveCurrencyCode = currencyCode ?: deviceCurrencyCode

    LaunchedEffect(restoreError) {
        restoreError?.let {
            snackbarHostState.showSnackbar(it)
            onRestoreErrorDismissed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = AuraSpacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinished, enabled = !isRestoring) { Text(stringResource(R.string.action_skip)) }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val pageOffset =
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val isCurrencyPage = page == currencyPageIndex
                OnboardingPageContent(
                    page = onboardingPages[page],
                    pageOffset = pageOffset,
                    interactive = isCurrencyPage
                ) {
                    if (isCurrencyPage) {
                        CurrencyPickerList(
                            selectedCode = effectiveCurrencyCode,
                            onSelect = onCurrencyChanged,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            PageIndicator(
                currentPage = pagerState.currentPage,
                totalPages = onboardingPages.size
            )

            Spacer(modifier = Modifier.height(AuraSpacing.xxl))

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (pagerState.currentPage < onboardingPages.size - 1) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onFinished()
                            }
                        }
                    }
                },
                enabled = !isRestoring,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    // heightIn, not height: a fixed 52dp clips the label at 200% font scale.
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    if (pagerState.currentPage == onboardingPages.size - 1) stringResource(R.string.action_get_started)
                    else stringResource(R.string.action_continue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (pagerState.currentPage == onboardingPages.size - 1) {
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                AuraCard(
                    style = AuraCardStyle.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(AuraSpacing.lg),
                    onClick = { if (!isRestoring) onRestoreFromDrive() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRestoring) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(AuraSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.onboarding_restore_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.onboarding_restore_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.xxl))
        }

        if (isRestoring) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                AuraCard(style = AuraCardStyle.Filled, contentPadding = PaddingValues(AuraSpacing.xxl)) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(AuraSpacing.lg))
                    Text(stringResource(R.string.onboarding_restoring))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    pageOffset: Float,
    interactive: Boolean = false,
    extra: @Composable () -> Unit = {}
) {
    val alpha by animateFloatAsState(
        targetValue = (1f - abs(pageOffset) * 0.6f).coerceIn(0f, 1f),
        label = "pageAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = (1f - abs(pageOffset) * 0.08f).coerceIn(0.85f, 1f),
        label = "pageScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationX = pageOffset * 120f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        // The interactive page lays out from the top: it has to hand the picker
        // the remaining height so the list scrolls instead of being clipped.
        verticalArrangement = if (interactive) Arrangement.Top else Arrangement.Center
    ) {
        if (interactive) Spacer(modifier = Modifier.height(AuraSpacing.md))

        // A smaller badge on the interactive page, to leave the 30 rows room.
        val badgeSize = if (interactive) 72.dp else 148.dp
        val iconSize = if (interactive) 32.dp else 60.dp
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(AuraGradients.aurora),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = AuraGradients.onAurora,
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(modifier = Modifier.height(if (interactive) AuraSpacing.lg else AuraSpacing.xxxl))

        Text(
            stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(modifier = Modifier.height(AuraSpacing.md))

        Text(
            stringResource(page.descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AuraSpacing.lg)
        )

        if (interactive) {
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            // Bounded height, so the list scrolls inside the page rather than
            // pushing the button off-screen.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { extra() }
        }
    }
}

@Composable
private fun PageIndicator(currentPage: Int, totalPages: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
        repeat(totalPages) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 26.dp else 8.dp,
                label = "dotWidth"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
