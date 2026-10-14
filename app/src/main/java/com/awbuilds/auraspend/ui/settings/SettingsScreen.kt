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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.material3.*
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.BuildConfig
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.data.ai.AiModelState
import com.awbuilds.auraspend.data.classification.SmsIngestor
import com.awbuilds.auraspend.data.classification.SmsNotifications
import com.awbuilds.auraspend.data.local.BackupData
import kotlinx.coroutines.launch
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.theme.AmoledBackground
import com.awbuilds.auraspend.ui.theme.AppThemeMode
import com.awbuilds.auraspend.ui.theme.AuroraCream
import com.awbuilds.auraspend.ui.theme.AuroraInk
import androidx.compose.ui.platform.LocalConfiguration
import com.awbuilds.auraspend.ui.designsystem.CurrencyPickerList
import com.awbuilds.auraspend.ui.designsystem.currencyDisplayName
import com.awbuilds.auraspend.ui.theme.AuroraLavenderLight
import com.awbuilds.auraspend.ui.theme.AuroraPurple
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.util.Locale

private const val GITHUB_URL = "https://github.com/auraspend/auraspend"

private const val TAG = "Settings"

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
    aiModelState: AiModelState = AiModelState.NotDownloaded,
    onDownloadModel: () -> Unit = {},
    onCancelModelDownload: () -> Unit = {},
    onDeleteModel: () -> Unit = {},
    autoDetectEnabled: Boolean = false,
    onAutoDetectChanged: (Boolean) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
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
                            val done = boundary(TAG, false) {
                                val summary = app?.backupRestoreManager?.restore(
                                    BackupData(
                                        transactions = emptyList(),
                                        categories = emptyList(),
                                        budgets = emptyList(),
                                        subscriptions = emptyList()
                                    )
                                )
                                if (app != null && summary != null) {
                                    app.seedDefaultCategories()
                                    SmsIngestor.resetIngestCursors(context)
                                }
                                app != null && summary != null
                            }
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
                    subtitle = deviceCurrencyCode
                        ?.let { "${currencyDisplayName(it, locale)} ($it)" }
                        .orEmpty(),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = AuraSpacing.gutter, top = AuraSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
        }

        SettingsSectionHeader(stringResource(R.string.settings_appearance))
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(AuraSpacing.md))
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)) {
                ThemePreviewTile(
                    label = stringResource(R.string.settings_theme_system),
                    background = AuroraCream,
                    accent = AuroraPurple,
                    selected = currentTheme == AppThemeMode.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { onThemeChanged(AppThemeMode.SYSTEM) }
                )
                ThemePreviewTile(
                    label = stringResource(R.string.settings_theme_light),
                    background = AuroraCream,
                    accent = AuroraPurple,
                    selected = currentTheme == AppThemeMode.LIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = { onThemeChanged(AppThemeMode.LIGHT) }
                )
                ThemePreviewTile(
                    label = stringResource(R.string.settings_theme_dark),
                    background = AuroraInk,
                    accent = AuroraLavenderLight,
                    selected = currentTheme == AppThemeMode.DARK,
                    modifier = Modifier.weight(1f),
                    onClick = { onThemeChanged(AppThemeMode.DARK) }
                )
                ThemePreviewTile(
                    label = stringResource(R.string.settings_theme_amoled),
                    background = AmoledBackground,
                    accent = AuroraLavenderLight,
                    selected = currentTheme == AppThemeMode.AMOLED,
                    modifier = Modifier.weight(1f),
                    onClick = { onThemeChanged(AppThemeMode.AMOLED) }
                )
            }
            Spacer(modifier = Modifier.height(AuraSpacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            SettingToggleRow(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChanged
            )
        }

        SettingsSectionHeader(stringResource(R.string.settings_intelligent_features))
        SettingsCard {
            SmsAccessRow(
                granted = smsGranted,
                onGrant = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(AuraSpacing.sm))
            AiModelSection(
                aiModelState = aiModelState,
                onDownload = onDownloadModel,
                onCancel = onCancelModelDownload,
                onDelete = { showDeleteDialog = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(AuraSpacing.sm))
            AutoDetectSection(
                enabled = autoDetectEnabled,
                onToggle = onAutoDetectChanged
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(AuraSpacing.sm))
            SettingToggleRow(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_subtitle),
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

        SettingsSectionHeader(stringResource(R.string.settings_data))
        SettingsCard {
            // The F-Droid build has no Google Play Services and no Drive backup
            // (ADR 0012): the rows are absent, not dead buttons.
            if (BuildConfig.DRIVE_BACKUP) {
                SettingsRow(
                    icon = Icons.Default.Backup,
                    title = stringResource(R.string.settings_drive_backup),
                    subtitle = stringResource(R.string.settings_drive_backup_subtitle),
                    busy = driveBusy,
                    onClick = onBackupToDrive
                )
                DividerSpacer()
                SettingsRow(
                    icon = Icons.Default.CloudDownload,
                    title = stringResource(R.string.settings_drive_restore),
                    subtitle = stringResource(R.string.settings_drive_restore_subtitle),
                    busy = driveBusy,
                    onClick = { showRestoreDialog = true }
                )
                DividerSpacer()
            }
            SettingsRow(
                icon = Icons.Default.FileDownload,
                title = stringResource(R.string.settings_export_csv),
                subtitle = stringResource(R.string.settings_export_csv_subtitle),
                onClick = onExportCsv
            )
            DividerSpacer()
            SettingsRow(
                icon = Icons.Default.FileUpload,
                title = stringResource(R.string.settings_import_csv),
                subtitle = stringResource(R.string.settings_import_csv_subtitle),
                onClick = onImportCsv
            )
            DividerSpacer()
            SettingsRow(
                icon = Icons.Default.DeleteForever,
                title = stringResource(R.string.settings_delete_data),
                subtitle = stringResource(R.string.settings_delete_data_subtitle),
                onClick = { showDeleteDataDialog = true }
            )
        }

        SettingsSectionHeader(stringResource(R.string.settings_finance))
        SettingsCard {
            // First in the section: it is the setting that changes how every other
            // screen reads, and until now it could not be changed at all.
            SettingsRow(
                icon = Icons.Default.Payments,
                title = stringResource(R.string.settings_currency),
                subtitle = if (currencyCode == null) {
                    stringResource(R.string.settings_currency_device_default)
                } else {
                    "${currencyDisplayName(currencyCode)} (${currencyCode.uppercase(Locale.ROOT)})"
                },
                onClick = { showCurrencySheet = true }
            )
            DividerSpacer()
            SettingsRow(
                icon = Icons.Default.Category,
                title = stringResource(R.string.settings_categories),
                subtitle = stringResource(R.string.settings_categories_subtitle),
                onClick = onManageCategories
            )
            DividerSpacer()
            SettingsRow(
                icon = Icons.Default.Subscriptions,
                title = stringResource(R.string.settings_subscriptions),
                subtitle = stringResource(R.string.settings_subscriptions_subtitle),
                onClick = onManageSubscriptions
            )
            DividerSpacer()
            SettingsRow(
                icon = Icons.Default.AccountBalance,
                title = stringResource(R.string.settings_budgets),
                subtitle = stringResource(R.string.settings_budgets_subtitle),
                onClick = onManageBudgets
            )
        }

        SettingsSectionHeader(stringResource(R.string.settings_about))
        SettingsCard {
            SettingsRow(
                icon = Icons.Default.Gavel,
                title = stringResource(R.string.settings_licenses),
                subtitle = stringResource(R.string.settings_licenses_subtitle),
                onClick = onOpenLicenses
            )
            Spacer(modifier = Modifier.height(AuraSpacing.sm))
            DividerSpacer()
            Spacer(modifier = Modifier.height(AuraSpacing.sm))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textLight
                )
                Spacer(modifier = Modifier.height(AuraSpacing.sm))
                Text(
                    stringResource(R.string.settings_about_made_by),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textLight
                )
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                        )
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(AuraSpacing.sm))
                    Text(stringResource(R.string.settings_view_github))
                }
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.xxxl))
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

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.extendedColors.textLight,
        modifier = Modifier.padding(start = AuraSpacing.gutter, top = AuraSpacing.xxl, bottom = AuraSpacing.sm)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.padding(horizontal = AuraSpacing.gutter)) {
        AuraCard(
            style = AuraCardStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun DividerSpacer() {
    Spacer(modifier = Modifier.height(AuraSpacing.sm))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(modifier = Modifier.height(AuraSpacing.sm))
}

@Composable
private fun SmsAccessRow(granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AuraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Sms,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(AuraSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_sms_access),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(
                    if (granted) R.string.settings_sms_access_granted
                    else R.string.settings_sms_access_missing
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textLight
            )
        }
        if (!granted) {
            TextButton(onClick = onGrant, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.classification_grant_permission))
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = AuraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(AuraSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textLight)
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(AuraSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textLight)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    val (title, subtitle) = when (aiModelState) {
        is AiModelState.Ready -> stringResource(R.string.settings_ai_ready) to
                stringResource(R.string.settings_ai_ready_subtitle)
        is AiModelState.Downloading -> stringResource(R.string.settings_ai_downloading) to
                stringResource(R.string.settings_ai_downloading_progress, (aiModelState.progress * 100).toInt())
        AiModelState.Failed -> stringResource(R.string.settings_ai_failed) to
                stringResource(R.string.settings_ai_failed_subtitle)
        else -> stringResource(R.string.settings_ai_missing) to
                stringResource(R.string.settings_ai_missing_subtitle)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = AuraSpacing.sm)) {
        // Alignment.Top, not CenterVertically: `title` is one or two lines and
        // the subtitle can be three (see settings_ai_missing_subtitle), so
        // vertically centring the glyph floated it beside whichever line happened
        // to be in the middle instead of sitting on the title. Caught on-device
        // 2026-09-29, where the icon hung below the words "Local AI not
        // downloaded".
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                // AutoAwesome, not the stock Android robot. A platform logo is
                // the wrong mark for a feature the user switches on, and AGENTS.md
                // keeps chrome on the Aurora token set rather than default
                // Material glyphs. Matches the Smart Add quick action.
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    // Nudge to the optical centre of a titleSmall line box.
                    .padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(AuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textLight)
            }
        }
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
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.action_cancel)) }
            }
            AiModelState.Ready -> {
                OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.settings_ai_delete_action)) }
            }
            else -> {
                Button(onClick = onDownload, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.settings_ai_download_action)) }
            }
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
        subtitle = stringResource(R.string.settings_auto_detect_subtitle),
        checked = checked,
        onCheckedChange = { next ->
            checked = next
            onToggle(next)
        }
    )
}
