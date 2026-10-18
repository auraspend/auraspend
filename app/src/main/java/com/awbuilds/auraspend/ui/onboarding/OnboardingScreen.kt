package com.awbuilds.auraspend.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Wallet
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
import com.awbuilds.auraspend.BuildConfig
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import androidx.compose.ui.platform.LocalConfiguration
import com.awbuilds.auraspend.ui.designsystem.CurrencyPickerList
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.theme.extendedColors
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
        Icons.Outlined.Wallet
    ),
    OnboardingPage(
        R.string.onboarding_page2_title,
        R.string.onboarding_page2_description,
        Icons.Outlined.AutoAwesome
    ),
    OnboardingPage(
        R.string.onboarding_page3_title,
        R.string.onboarding_page3_description,
        Icons.Outlined.Savings
    ),
    // Last, and interactive: the user should know what the app will do before
    // granting SMS access on the button below, and currency is the one setting
    // whose default (their device's) they may actively want to change.
    OnboardingPage(
        R.string.onboarding_page4_title,
        R.string.onboarding_page4_description,
        Icons.Outlined.Payments
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AuraSpacing.sm),
                horizontalArrangement = Arrangement.End
            ) {
                // The concept's `.link`: 12sp/600 in the one accent, on a 48dp
                // target. It is the only violet on the page — everything else is
                // ink, paper and hairlines.
                Text(
                    stringResource(R.string.action_skip),
                    style = AuraType.sectionAction,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !isRestoring, onClick = onFinished)
                        .padding(horizontal = AuraSpacing.md, vertical = AuraSpacing.lg)
                )
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
                // The concept's `.btn-primary`, and the only filled button in the
                // app: ink, 16dp radius, paper label. It was a violet pill, which
                // spent the accent on the one thing that did not need marking —
                // and left nothing to mark "Skip" with.
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                    disabledContainerColor = MaterialTheme.extendedColors.track,
                    disabledContentColor = MaterialTheme.extendedColors.faint
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    // heightIn, not height: a fixed 52dp clips the label at 200% font scale.
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    if (pagerState.currentPage == onboardingPages.size - 1) stringResource(R.string.action_get_started)
                    else stringResource(R.string.action_continue),
                    style = AuraType.buttonPrimary
                )
            }

            // The F-Droid build has no Google Play Services, so no Drive restore
            // (ADR 0012) — the card is absent rather than dead.
            if (BuildConfig.DRIVE_BACKUP && pagerState.currentPage == onboardingPages.size - 1) {
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                AuraCard(
                    style = AuraCardStyle.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = AuraSpacing.lg,
                        vertical = 14.dp
                    ),
                    onClick = { if (!isRestoring) onRestoreFromDrive() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
                    ) {
                        // The concept's `.rowlink-ic`: a 32dp rounded-square tile at
                        // 6 % ink with a 16dp glyph. It was a bare violet glyph,
                        // which spent the accent on a row that already reads as
                        // tappable from its chevron.
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Outlined.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.extendedColors.inkSoft,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.onboarding_restore_title),
                                style = AuraType.rowLinkTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.onboarding_restore_subtitle),
                                style = AuraType.rowLinkMeta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.extendedColors.faint,
                            modifier = Modifier.size(16.dp)
                        )
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
    // Alpha only. The page used to scale and translate as well, which is a
    // second and third animation on top of the pager's own slide — three
    // graphics layers per page for one gesture, and a zoom the concept's motion
    // rules do not have ("220ms content", no overshoot, nothing decorative).
    val alpha by animateFloatAsState(
        targetValue = (1f - abs(pageOffset) * 0.6f).coerceIn(0f, 1f),
        label = "pageAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
        // The interactive page lays out from the top: it has to hand the picker
        // the remaining height so the list scrolls instead of being clipped.
        verticalArrangement = if (interactive) Arrangement.Top else Arrangement.Center
    ) {
        if (interactive) Spacer(modifier = Modifier.height(AuraSpacing.md))

        // The concept's badge, scaled up: a tonal disc with an ink glyph
        // (`.alert-ico`, `.rowlink-ic`), never the brand sweep. The gradient
        // medallion was the loudest thing in the app and it meant nothing — the
        // glyph says which page you are on, the disc only has to hold it.
        // A smaller disc on the interactive page, to leave the 30 rows room.
        val badgeSize = if (interactive) 64.dp else 120.dp
        val iconSize = if (interactive) 26.dp else 44.dp
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(modifier = Modifier.height(if (interactive) AuraSpacing.lg else AuraSpacing.section))

        Text(
            stringResource(page.titleRes),
            style = AuraType.screenTitle,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            stringResource(page.descriptionRes),
            style = AuraType.bodyCopy,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AuraSpacing.sm)
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
                    // Ink, not violet: violet is the accent for the one thing you
                    // can act on, and a progress dot is not actionable. The same
                    // rule the chips and the segmented control follow.
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
