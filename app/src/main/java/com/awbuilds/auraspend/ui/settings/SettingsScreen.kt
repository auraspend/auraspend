package com.awbuilds.auraspend.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.biometric.BiometricManager
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.BuildConfig
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.data.ai.AiModelState
import com.awbuilds.auraspend.data.classification.SmsNotifications
import com.awbuilds.auraspend.data.update.AppUpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.theme.AmoledBackground
import com.awbuilds.auraspend.ui.theme.AppThemeMode
import com.awbuilds.auraspend.ui.theme.AuroraCream
import com.awbuilds.auraspend.ui.theme.AuroraInk
import androidx.compose.ui.platform.LocalConfiguration
import com.awbuilds.auraspend.ui.designsystem.CurrencyPickerList
import com.awbuilds.auraspend.ui.designsystem.currencyDisplayName
import com.awbuilds.auraspend.ui.designsystem.rememberCurrencyStyle
import com.awbuilds.auraspend.ui.theme.AuroraLavenderLight
import com.awbuilds.auraspend.ui.theme.AuroraPurple
import com.awbuilds.auraspend.ui.theme.extendedColors

private const val GITHUB_URL = "https://github.com/auraspend/auraspend"

private const val TAG = "Settings"

// ─── Concept metrics ──────────────────────────────────────────────────────────
// Every value below is a measurement from the interface concept
// (docs/design/interface-concept/index.html), expressed on the Aurora scale where
// one exists. They are named rather than inlined because the settings row is used
// a dozen times and the row geometry has to be identical in all of them.

/** `.srow{padding:14px 0}` — 12 + 2, the nearest pair of scale steps. */
private val RowPadding = AuraSpacing.md + AuraSpacing.xxs

/** `.rowlink-ic{width:32px;height:32px}` — the rounded-square glyph tile. */
private val IconTileSize = AuraSpacing.xxxl

/** `.rowlink-ic{border-radius:10px}` — 8 + 2. */
private val IconTileRadius = AuraSpacing.sm + AuraSpacing.xxs

/** `.group{margin-top:22px}` — 20 + 2. */
private val GroupGap = AuraSpacing.xl + AuraSpacing.xxs

/** `.group-t{padding:0 2px 8px}`. */
private val GroupLabelStart = AuraSpacing.xxs

/** A concept row: a [IconTileSize] tile with [RowPadding] above and below it. */
private val RowHeight = IconTileSize + RowPadding * 2

/** `.backbtn{width:34px;height:34px}`. */
private val BackButtonSize = 34.dp

/** `.backbtn` sits on the 20dp gutter; its 48dp touch target overhangs by this. */
private val BackTouchOverhang = (48.dp - BackButtonSize) / 2

/** `.head{align-items:center;gap:14px}`, 14dp from the circle to the title. */
private val HeadGap = 14.dp

/**
 * `.head{padding:6px 0 18px}` plus `.backbtn{margin-top:2px}` puts the circle 8dp
 * under the status bar with 18dp below it, and makes the header 60dp tall.
 *
 * The button's 48dp touch target overhangs the 34dp circle by
 * [BackTouchOverhang] on each side, so the row takes that much less padding: the
 * header still measures 60dp and the circle still lands on the concept's 8dp.
 */
private val HeadTopPadding = 8.dp - BackTouchOverhang
private val HeadBottomPadding = 18.dp - BackTouchOverhang

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: AppThemeMode,
    onThemeChanged: (AppThemeMode) -> Unit,
    dynamicColor: Boolean = false,
    onDynamicColorChanged: (Boolean) -> Unit = {},
    /** The picked ISO code, or null while the app follows the device locale. */
    currencyCode: String? = null,
    onCurrencyChanged: (String?) -> Unit = {},
    onBack: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onBackupToDrive: () -> Unit = {},
    onRestoreFromDrive: () -> Unit = {},
    driveBusy: Boolean = false,
    onManageCategories: () -> Unit = {},
    onManageSubscriptions: () -> Unit = {},
    onManageBudgets: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    /** Messages the categoriser could not place, for the review-queue row. */
    reviewQueueCount: Int = 0,
    onOpenReviewQueue: () -> Unit = {},
    aiModelState: AiModelState = AiModelState.NotDownloaded,
    onDownloadModel: () -> Unit = {},
    onCancelModelDownload: () -> Unit = {},
    onDeleteModel: () -> Unit = {},
    autoDetectEnabled: Boolean = false,
    onAutoDetectChanged: (Boolean) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current
    // The currency row states the code and the symbol the app renders in — the
    // same "INR ₹" the concept draws. The full display name lives in the picker,
    // one tap away.
    val currencyStyle = rememberCurrencyStyle(currencyCode)
    val appUpdateController = remember(context) {
        if (BuildConfig.IN_APP_UPDATES) {
            (context.applicationContext as? AuraSpendApp)?.appUpdateController
        } else {
            null
        }
    }
    val fallbackUpdateState = remember { MutableStateFlow<AppUpdateState>(AppUpdateState.Idle) }
    val appUpdateState by (appUpdateController?.state ?: fallbackUpdateState)
        .collectAsStateWithLifecycle()
    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        appUpdateController?.onUpdateFlowResult(result.resultCode)
    }
    val updateSubtitle = when (appUpdateState) {
        AppUpdateState.Checking -> stringResource(R.string.settings_update_checking)
        AppUpdateState.UpToDate -> stringResource(R.string.settings_update_up_to_date)
        AppUpdateState.Downloading -> stringResource(R.string.settings_update_downloading)
        AppUpdateState.Downloaded -> stringResource(R.string.settings_update_ready)
        AppUpdateState.Failed -> stringResource(R.string.settings_update_failed)
        AppUpdateState.Idle, AppUpdateState.Unsupported ->
            stringResource(R.string.settings_update_subtitle)
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    val lockManager = remember(context) { (context.applicationContext as? AuraSpendApp)?.appLockManager }
    var lockEnabled by remember { mutableStateOf(lockManager?.isEnabled == true) }
    var showPinCreateDialog by remember { mutableStateOf(false) }
    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var pinVerifyPurpose by remember { mutableStateOf(PinVerifyPurpose.DISABLE) }
    val biometricAvailable = remember(context) {
        runCatching {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }
    val scope = rememberCoroutineScope()
    // SMS access gates everything the categoriser does: if it is missing the
    // toggle below silently does nothing, so the state is surfaced here with
    // the way to fix it.
    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Re-check instead of trusting the callback value: the grant can arrive
        // through system settings while the app was backgrounded.
        smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }
    var notificationsEnabled by remember { mutableStateOf(SmsNotifications.isEnabled(context)) }
    var showDeleteDataDialog by remember { mutableStateOf(false) }
    val deleteDoneMessage = stringResource(R.string.settings_delete_data_done)
    val deleteFailedMessage = stringResource(R.string.settings_delete_data_failed)

    // Restore is a replace, not a merge: the confirm step exists because one tap
    // would otherwise overwrite every local table with the Drive snapshot.
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.settings_drive_restore_confirm_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.settings_drive_restore_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        onRestoreFromDrive()
                    }
                ) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // A wipe is the one local action that can destroy everything; it asks like
    // the Drive restore does, then runs the same table sweep with nothing to
    // restore, re-seeds the default categories, and forgets the ingest cursors
    // so a fresh scan can re-import the inbox.
    if (showDeleteDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDataDialog = false },
            title = {
                Text(
                    stringResource(R.string.settings_delete_data_title),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = { Text(stringResource(R.string.settings_delete_data_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDataDialog = false
                        scope.launch {
                            val app = context.applicationContext as? AuraSpendApp
                            val done = app?.wipeAllData() ?: false
                            snackbarHostState.showSnackbar(
                                if (done) deleteDoneMessage else deleteFailedMessage
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDataDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Outside the wipe confirmation on purpose. These two were nested inside
    // `if (showDeleteDataDialog)`, so the app-lock row, Change PIN and the
    // biometric toggle did nothing at all unless the "Delete all data?" dialog
    // happened to be open — the PIN sheet was never composed.
    if (showPinCreateDialog) {
        PinCreateDialog(
            onDone = { pin ->
                lockManager?.setPin(pin)
                lockManager?.isEnabled = true
                lockEnabled = true
                showPinCreateDialog = false
            },
            onDismiss = { showPinCreateDialog = false }
        )
    }
    if (showPinVerifyDialog) {
        PinVerifyDialog(
            verify = { pin -> lockManager?.verifyPin(pin) == true },
            onVerified = {
                showPinVerifyDialog = false
                when (pinVerifyPurpose) {
                    PinVerifyPurpose.DISABLE -> {
                        lockManager?.clearPin()
                        lockEnabled = false
                    }
                    PinVerifyPurpose.CHANGE -> showPinCreateDialog = true
                }
            },
            onDismiss = { showPinVerifyDialog = false }
        )
    }

    if (showCurrencySheet) {
        val locale = LocalConfiguration.current.locales[0]
        val deviceCurrencyCode = remember(locale) {
            runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull()
        }
        ModalBottomSheet(
            onDismissRequest = { showCurrencySheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.gutter)
                    .padding(bottom = AuraSpacing.xxl)
            ) {
                Text(
                    stringResource(R.string.settings_currency),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(AuraSpacing.sm))
                // The reset lives here rather than in the shared list: onboarding
                // has nothing to reset, and "follow my device" is what a user
                // looks for after they have changed their mind.
                SettingsRow(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.currency_picker_device_default),
                    state = deviceCurrencyCode
                        ?.let { "${currencyDisplayName(it, locale)} ($it)" },
                    // An action, not a destination: the sheet closes on tap, so
                    // there is nowhere for a chevron to point.
                    chevron = false,
                    onClick = {
                        onCurrencyChanged(null)
                        showCurrencySheet = false
                    }
                )
                CurrencyPickerList(
                    selectedCode = currencyCode,
                    onSelect = { code ->
                        onCurrencyChanged(code)
                        showCurrencySheet = false
                    },
                    locale = locale,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }

    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.gutter)
                    .padding(bottom = AuraSpacing.xxl)
            ) {
                Text(
                    stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(AuraSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)) {
                    ThemePreviewTile(
                        label = stringResource(R.string.settings_theme_system),
                        background = AuroraCream,
                        accent = AuroraPurple,
                        selected = currentTheme == AppThemeMode.SYSTEM,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onThemeChanged(AppThemeMode.SYSTEM)
                            showThemeSheet = false
                        }
                    )
                    ThemePreviewTile(
                        label = stringResource(R.string.settings_theme_light),
                        background = AuroraCream,
                        accent = AuroraPurple,
                        selected = currentTheme == AppThemeMode.LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onThemeChanged(AppThemeMode.LIGHT)
                            showThemeSheet = false
                        }
                    )
                    ThemePreviewTile(
                        label = stringResource(R.string.settings_theme_dark),
                        background = AuroraInk,
                        accent = AuroraLavenderLight,
                        selected = currentTheme == AppThemeMode.DARK,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onThemeChanged(AppThemeMode.DARK)
                            showThemeSheet = false
                        }
                    )
                    ThemePreviewTile(
                        label = stringResource(R.string.settings_theme_amoled),
                        background = AmoledBackground,
                        accent = AuroraLavenderLight,
                        selected = currentTheme == AppThemeMode.AMOLED,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onThemeChanged(AppThemeMode.AMOLED)
                            showThemeSheet = false
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_ai_delete_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.settings_ai_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModel()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Box wrapper so the CSV import/export result can be surfaced to the user —
    // previously a failed import looked identical to a successful one.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                // Settings is a pushed screen with no AuraBottomBar of its own, so
                // nothing else kept the list clear of the system navigation bar —
                // the last row ("Categories") sat under it and could not be read or
                // tapped. Found on-device 2026-09-29. Applied before
                // verticalScroll so the viewport itself ends above the bar and the
                // final item can still be scrolled fully into view.
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
        SettingsHeader(onBack = onBack)

        SettingsGroup(stringResource(R.string.settings_money)) {
            // First in the group: it is the setting that changes how every other
            // screen reads, and until now it could not be changed at all.
            SettingsRow(
                icon = Icons.Default.Payments,
                title = stringResource(R.string.settings_currency),
                value = if (currencyCode == null) {
                    stringResource(R.string.settings_currency_device_default)
                } else {
                    // "INR ₹" — the concept's value, and the one place the symbol
                    // the rest of the app renders in is stated outright.
                    "${currencyStyle.code} ${currencyStyle.symbol}"
                },
                onClick = { showCurrencySheet = true }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Default.Category,
                title = stringResource(R.string.settings_categories),
                state = stringResource(R.string.settings_categories_subtitle),
                onClick = onManageCategories
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Default.Subscriptions,
                title = stringResource(R.string.settings_subscriptions),
                state = stringResource(R.string.settings_subscriptions_subtitle),
                onClick = onManageSubscriptions
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Default.AccountBalance,
                title = stringResource(R.string.settings_budgets),
                state = stringResource(R.string.settings_budgets_subtitle),
                onClick = onManageBudgets
            )
        }

        SettingsGroup(stringResource(R.string.settings_messages)) {
            SmsAccessRow(
                granted = smsGranted,
                onGrant = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) }
            )
            // The queue of messages the categoriser could not place: the same
            // count Home's rail shows, with the screen that answers it one tap
            // away.
            RowDivider()
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                title = stringResource(R.string.settings_review_queue),
                value = pluralStringResource(
                    R.plurals.settings_review_queue_waiting,
                    reviewQueueCount,
                    reviewQueueCount
                ),
                onClick = onOpenReviewQueue
            )
            RowDivider()
            AiModelSection(
                aiModelState = aiModelState,
                onDownload = onDownloadModel,
                onCancel = onCancelModelDownload,
                onDelete = { showDeleteDialog = true }
            )
            RowDivider()
            AutoDetectSection(
                enabled = autoDetectEnabled,
                onToggle = onAutoDetectChanged
            )
            RowDivider()
            SettingToggleRow(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notifications),
                state = stringResource(R.string.settings_notifications_subtitle),
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    notificationsEnabled = enabled
                    SmsNotifications.setEnabled(context, enabled)
                    if (!enabled) {
                        // Take the live one down now; publish() keeps future
                        // ones away while muted.
                        (context.applicationContext as? AuraSpendApp)?.smsProgressNotifier?.cancel()
                    }
                }
            )
        }

        SettingsGroup(stringResource(R.string.settings_appearance)) {
            // The four preview tiles live in a sheet now: the concept's settings
            // row carries the current value on one line, and the picker is one tap
            // deep — the same pattern as the currency picker.
            SettingsRow(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.settings_theme),
                value = when (currentTheme) {
                    AppThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                    AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                    AppThemeMode.AMOLED -> stringResource(R.string.settings_theme_amoled)
                },
                onClick = { showThemeSheet = true }
            )
            RowDivider()
            SettingToggleRow(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_dynamic_color),
                state = stringResource(R.string.settings_dynamic_color_subtitle),
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChanged
            )
            // The app lock lives here rather than in a group of its own: the
            // concept keeps these groups short, and a lock is how the app looks
            // when it opens.
            RowDivider()
            SettingToggleRow(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_app_lock),
                state = stringResource(R.string.settings_app_lock_subtitle),
                checked = lockEnabled,
                onCheckedChange = { enable ->
                    if (enable) {
                        showPinCreateDialog = true
                    } else {
                        pinVerifyPurpose = PinVerifyPurpose.DISABLE
                        showPinVerifyDialog = true
                    }
                }
            )
            if (lockEnabled) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Default.Pin,
                    title = stringResource(R.string.settings_app_lock_change),
                    state = stringResource(R.string.settings_app_lock_change_subtitle),
                    onClick = {
                        pinVerifyPurpose = PinVerifyPurpose.CHANGE
                        showPinVerifyDialog = true
                    }
                )
                if (biometricAvailable) {
                    RowDivider()
                    SettingToggleRow(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.settings_app_lock_biometric),
                        state = stringResource(R.string.settings_app_lock_biometric_subtitle),
                        checked = lockManager?.useBiometric == true,
                        onCheckedChange = { lockManager?.useBiometric = it }
                    )
                }
            }
        }

        SettingsGroup(stringResource(R.string.settings_data)) {
            // The F-Droid build has no Google Play Services and no Drive backup
            // (ADR 0012): the rows are absent, not dead buttons.
            if (BuildConfig.DRIVE_BACKUP) {
                SettingsRow(
                    icon = Icons.Default.Backup,
                    title = stringResource(R.string.settings_drive_backup),
                    state = stringResource(R.string.settings_drive_backup_subtitle),
                    busy = driveBusy,
                    onClick = onBackupToDrive
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Default.CloudDownload,
                    title = stringResource(R.string.settings_drive_restore),
                    state = stringResource(R.string.settings_drive_restore_subtitle),
                    busy = driveBusy,
                    onClick = { showRestoreDialog = true }
                )
                RowDivider()
            }
            SettingsRow(
                icon = Icons.Default.FileDownload,
                title = stringResource(R.string.settings_export_csv),
                state = stringResource(R.string.settings_export_csv_subtitle),
                onClick = onExportCsv
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Default.FileUpload,
                title = stringResource(R.string.settings_import_csv),
                state = stringResource(R.string.settings_import_csv_subtitle),
                onClick = onImportCsv
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Default.DeleteForever,
                title = stringResource(R.string.settings_delete_data),
                state = stringResource(R.string.settings_delete_data_subtitle),
                onClick = { showDeleteDataDialog = true }
            )
        }

        SettingsGroup(stringResource(R.string.settings_about)) {
            if (BuildConfig.IN_APP_UPDATES && appUpdateController?.isAvailable == true) {
                SettingsRow(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.settings_update_title),
                    // The updater's own state is the row's second line, not a value:
                    // "You're on the latest version" is a sentence, not a setting.
                    state = updateSubtitle,
                    busy = appUpdateState == AppUpdateState.Checking ||
                        appUpdateState == AppUpdateState.Downloading,
                    onClick = {
                        when (appUpdateState) {
                            AppUpdateState.Downloaded -> appUpdateController.completeUpdate()
                            AppUpdateState.Checking, AppUpdateState.Downloading -> Unit
                            else -> appUpdateController.checkForUpdate(updateLauncher)
                        }
                    }
                )
                RowDivider()
            }
            SettingsRow(
                icon = Icons.Default.Gavel,
                title = stringResource(R.string.settings_licenses),
                state = stringResource(R.string.settings_licenses_subtitle),
                onClick = onOpenLicenses
            )
            RowDivider()
            AboutFooter()
        }

        Spacer(modifier = Modifier.height(AuraSpacing.bottomContent))
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(AuraSpacing.gutter)
        )
    }
}

// ─── Building blocks ──────────────────────────────────────────────────────────

/**
 * The screen's own header: a 34dp circular back button, 14dp, then the title —
 * the concept's `.head` plus `.backbtn`.
 *
 * The circle is 34dp but its touch target is the 48dp minimum, so the row is
 * pulled in by the target's overhang on every side: the circle still lands on the
 * 20dp gutter with the title 14dp past it, and the header still measures the
 * concept's 60dp.
 */
@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AuraSpacing.gutter - BackTouchOverhang,
                end = AuraSpacing.gutter,
                top = HeadTopPadding,
                bottom = HeadBottomPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Box(
                modifier = Modifier
                    .size(BackButtonSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(HeadGap - BackTouchOverhang))
        Text(
            stringResource(R.string.settings_title),
            style = AuraType.screenTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/**
 * One labelled group: a 10.5sp uppercase label on the faint grey, then the card
 * that holds its rows.
 *
 * The card carries *no* vertical padding — every row supplies its own 14dp — and
 * 16dp horizontally, which is what puts the 32dp glyph tiles 36dp from the screen
 * edge instead of flush with the card.
 */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.gutter)
            .padding(top = GroupGap)
    ) {
        Text(
            title.uppercase(),
            style = AuraType.groupLabel,
            color = MaterialTheme.extendedColors.faint,
            modifier = Modifier.padding(start = GroupLabelStart, bottom = AuraSpacing.sm)
        )
        AuraCard(
            style = AuraCardStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AuraSpacing.lg),
            content = content
        )
    }
}

/**
 * The hairline between two rows. Drawn between rows, never above the first or
 * below the last, so it belongs to the row *below* it in the concept
 * (`.srow + .srow{border-top}`) and is written explicitly here for the same
 * reason: the Messages group mixes rows, state blocks and toggles, and only the
 * call site knows what precedes what.
 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.extendedColors.lineSoft
    )
}

/** `.rowlink-ic`: a 32dp rounded square at 6 % ink with a 16dp glyph inside. */
@Composable
private fun SettingsIconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(IconTileSize)
            .clip(RoundedCornerShape(IconTileRadius))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        // Faint, not violet: the tile already marks the row as a settings entry,
        // and the concept spends the accent only on what you can act on.
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.faint,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** The trailing mark on a row: the concept's 16dp chevron, or a busy spinner. */
@Composable
private fun RowTrailing(busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    } else {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.faint,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * A one-line settings row: glyph tile, label, optional value, chevron.
 *
 * The state rows below are the exception, not the rule — a settings row that
 * needs a sentence is a row whose label was not specific enough.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    busy: Boolean = false,
    state: String? = null,
    chevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
    ) {
        SettingsIconTile(icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AuraSpacing.xxs)) {
            Text(title, style = AuraType.settingsRowTitle, color = MaterialTheme.colorScheme.onSurface)
            if (state != null) {
                Text(
                    state,
                    style = AuraType.rowLinkMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = AuraType.settingsRowValue,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (chevron || busy) RowTrailing(busy = busy)
    }
}

/**
 * A row whose second line is the *state* the row exists to report — a missing
 * permission, a model that is not downloaded. These are kept from the app's own
 * design, not the concept's, so they wear the concept's meta line and let the
 * call site add the action underneath.
 */
@Composable
private fun SettingsStateRow(
    icon: ImageVector,
    title: String,
    state: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RowPadding)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            SettingsIconTile(icon)
            Spacer(modifier = Modifier.width(AuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AuraType.settingsRowTitle, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    state,
                    style = AuraType.rowLinkMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        content()
    }
}

/**
 * A toggle row. Same 60dp rhythm as every other row: a switch brings its own
 * 48dp touch target, so it needs less padding than a 32dp tile does.
 */
@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    state: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row toggles, not just the switch. Every other row in
            // this screen opens something when it is tapped, and a row whose
            // title does nothing is a surprise. The Switch consumes its own
            // touches, so tapping the switch does not toggle it twice.
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = (RowHeight - 48.dp) / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
    ) {
        SettingsIconTile(icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AuraSpacing.xxs)) {
            Text(title, style = AuraType.settingsRowTitle, color = MaterialTheme.colorScheme.onSurface)
            if (state != null) {
                Text(
                    state,
                    style = AuraType.rowLinkMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // The switch carries the row's own title as its accessible name. A
        // bare Switch is a "switch, off" with nothing after it, and the row's
        // title and subtitle are siblings of it rather than its label — so a
        // screen-reader user was told a switch existed and not what it did.
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title }
        )
    }
}

/**
 * The concept's one filled button: ink on paper, 52dp tall, 16dp radius.
 *
 * Not violet. Violet marks the things you can *navigate* to and the switches you
 * can act on; the concept keeps the single darkest object on a screen for the one
 * action it wants taken.
 */
@Composable
private fun SettingsPrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(AuraSpacing.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
            disabledContainerColor = MaterialTheme.extendedColors.track,
            disabledContentColor = MaterialTheme.extendedColors.faint
        )
    ) {
        Text(label, style = AuraType.buttonPrimary)
    }
}

/** The paired secondary action ("Cancel", "Delete model"): hairline, same box. */
@Composable
private fun SettingsSecondaryButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(AuraSpacing.lg)
    ) {
        Text(label, style = AuraType.buttonPrimary)
    }
}

/**
 * The app's identity block at the foot of the About card. The concept has no
 * equivalent — it stands in for the version row and keeps the GitHub link, which
 * is the only way to reach the project from inside the app.
 */
@Composable
private fun AboutFooter() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RowPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.app_name),
            style = AuraType.settingsRowTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(AuraSpacing.xxs))
        Text(
            stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            style = AuraType.rowLinkMeta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AuraSpacing.xs))
        Text(
            stringResource(R.string.settings_about_made_by),
            style = AuraType.rowLinkMeta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AuraSpacing.lg))
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                )
            },
            shape = RoundedCornerShape(AuraSpacing.lg),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(AuraSpacing.sm))
            Text(stringResource(R.string.settings_view_github), style = AuraType.buttonPrimary)
        }
    }
}

/**
 * SMS access is the one permission the app cannot work without: without it the
 * toggle below does nothing at all, so the row states the consequence and offers
 * the fix rather than leaving the user to find it in system settings.
 */
@Composable
private fun SmsAccessRow(granted: Boolean, onGrant: () -> Unit) {
    SettingsStateRow(
        icon = Icons.Default.Sms,
        title = stringResource(R.string.settings_sms_access),
        state = stringResource(
            if (granted) R.string.settings_sms_access_granted
            else R.string.settings_sms_access_missing
        )
    ) {
        if (!granted) {
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            SettingsPrimaryButton(
                label = stringResource(R.string.classification_grant_permission),
                onClick = onGrant
            )
        }
    }
}

@Composable
private fun ThemePreviewTile(
    label: String,
    background: Color,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        label = "tileBorder"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Theme preview tiles hold a label; a fixed height clips at 200% scale.
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(AuraSpacing.sm)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent)
                )
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent.copy(alpha = 0.55f))
                )
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent.copy(alpha = 0.35f))
                )
            }
        }
        Spacer(modifier = Modifier.height(AuraSpacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AiModelSection(
    aiModelState: AiModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val (title, stateText) = when (aiModelState) {
        is AiModelState.Ready -> stringResource(R.string.settings_ai_ready) to
                stringResource(R.string.settings_ai_ready_subtitle)
        is AiModelState.Downloading -> stringResource(R.string.settings_ai_downloading) to
                stringResource(R.string.settings_ai_downloading_progress, (aiModelState.progress * 100).toInt())
        AiModelState.Failed -> stringResource(R.string.settings_ai_failed) to
                stringResource(R.string.settings_ai_failed_subtitle)
        else -> stringResource(R.string.settings_ai_missing) to
                stringResource(R.string.settings_ai_missing_subtitle)
    }
    // The tile rides the title's line, not the middle of the whole block: the
    // state line can run to three lines (see settings_ai_missing_subtitle), and
    // centring the glyph against that left it hanging below the words "Local AI
    // not downloaded". Caught on-device 2026-09-29.
    SettingsStateRow(
        // AutoAwesome, not the stock Android robot. A platform logo is the wrong
        // mark for a feature the user switches on, and AGENTS.md keeps chrome on
        // the Aurora token set rather than default Material glyphs. Matches the
        // Smart Add quick action.
        icon = Icons.Default.AutoAwesome,
        title = title,
        state = stateText
    ) {
        Spacer(modifier = Modifier.height(AuraSpacing.md))
        when (aiModelState) {
            is AiModelState.Downloading -> {
                val animatedProgress by animateFloatAsState(
                    targetValue = aiModelState.progress,
                    label = "modelProgress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.extendedColors.track
                )
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                SettingsSecondaryButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = onCancel
                )
            }
            AiModelState.Ready -> SettingsSecondaryButton(
                label = stringResource(R.string.settings_ai_delete_action),
                onClick = onDelete
            )
            else -> SettingsPrimaryButton(
                label = stringResource(R.string.settings_ai_download_action),
                onClick = onDownload
            )
        }
    }
}

@Composable
private fun AutoDetectSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    // Optimistic: the switch flips immediately; side effects run afterwards.
    var checked by remember(enabled) { mutableStateOf(enabled) }
    SettingToggleRow(
        icon = Icons.Default.Sms,
        title = stringResource(R.string.settings_auto_detect),
        state = stringResource(R.string.settings_auto_detect_subtitle),
        checked = checked,
        onCheckedChange = { next ->
            checked = next
            onToggle(next)
        }
    )
}
