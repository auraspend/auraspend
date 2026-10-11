package com.awbuilds.auraspend.ui.classification

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.data.ai.AiModelState
import com.awbuilds.auraspend.data.classification.ClassifiedSms
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.core.isNotificationPermissionNeeded
import com.awbuilds.auraspend.ui.designsystem.AnimatedMoney
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSkeleton
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.CategoryAvatar
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)

// Main Smart Add screen: message input, classification result, and save.

@Composable
fun ClassificationScreen(
    viewModel: ClassificationViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current


    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.handleIntent(ClassificationViewIntent.SmsPermissionResult(granted))
    }

    LaunchedEffect(Unit) {
        if (state.smsPermissionGranted) {
            viewModel.handleIntent(ClassificationViewIntent.LoadSmsMessages)
        }
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            onBack()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Undo surface for the triage swipes. Held here rather than in the tab so
    // the tab can stay a pure renderer, and so the SnackbarHost lives in the
    // Scaffold that owns the content.
    val triageSnackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Read in composition scope: `stringResource` is @Composable, and the success
    // snackbar is now driven by a flow collector outside the tab's scope.
    val savedMessage = stringResource(R.string.classification_sms_saved)
    val dismissedMessage = stringResource(R.string.classification_sms_dismissed)
    val undoActionLabel = stringResource(R.string.action_undo)

    // "Saved" is shown when the save reports that it happened, not when the swipe
    // is read. The two used to be the same moment, so swiping a message the
    // classifier could not resolve — or a write that failed — still produced a
    // green "Saved" with an undo action for a transaction that was never created.
    // Failures surface through `state.error` (an AuraErrorBanner in the tab).
    LaunchedEffect(viewModel) {
        viewModel.triageOutcomes.collect { outcome ->
            val result = triageSnackbarHostState.showSnackbar(
                message = savedMessage,
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.handleIntent(
                    ClassificationViewIntent.UndoSaveClassifiedSms(outcome.smsId)
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(triageSnackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.classification_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            AuraSegmentedControl(
                options = listOf(
                    stringResource(R.string.classification_tab_paste),
                    stringResource(R.string.classification_tab_sms),
                    stringResource(R.string.classification_tab_auto)
                ),
                selectedIndex = selectedTab,
                onSelect = { index ->
                    selectedTab = index
                    when (index) {
                        1 -> if (!state.smsPermissionGranted) {
                            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                        } else {
                            viewModel.handleIntent(ClassificationViewIntent.LoadSmsMessages)
                        }
                        2 -> if (!state.smsPermissionGranted) {
                            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                        } else {
                            viewModel.handleIntent(ClassificationViewIntent.LoadAndClassifyAll)
                        }
                    }
                },
                modifier = Modifier.padding(
                    horizontal = AuraSpacing.gutter,
                    vertical = AuraSpacing.md
                )
            )

        // The consent *modal* was removed when the model shrank from 468 MB to
        // 22 MB (see handoff 0012). A blocking dialog is not warranted for a
        // download the size of a photo, and the app is fully functional without the
        // model — so the offer is an inline card on the classification tab, and the
        // notification permission is requested from the accept action rather than
        // from a modal the user cannot decline without reading 468 MB of preamble.
        //
        // It used to be a `LaunchedEffect` that fired `ConsentResult(true)` the
        // instant this state became true, rendering nothing at all. On a fresh
        // install that meant one tap on Smart Add silently downloaded the 22 MB
        // model, enabled the auto-read toggle and ran an inbox scan — verified on
        // device 2026-09-29: `files/models/minilm-l6-v2-q8.onnx`, 23,026,053 bytes,
        // with no prompt on screen. It also contradicted the app's own promise
        // (ADR 0008) that nothing happens without the user choosing it. Consent now
        // requires the tap below.
        if (state.consentRequired) {
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* Accepted either way; the download proceeds regardless. */ }

            AuraCard(
                style = AuraCardStyle.Tonal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.sm),
                contentPadding = PaddingValues(AuraSpacing.lg)
            ) {
                Text(
                    stringResource(R.string.classification_local_ai_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                Text(
                    stringResource(R.string.classification_local_ai_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textLight
                )
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.handleIntent(
                                ClassificationViewIntent.DismissModelConsent
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_not_now))
                    }
                    Button(
                        onClick = {
                            if (isNotificationPermissionNeeded(context)) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                            viewModel.handleIntent(
                                ClassificationViewIntent.ConsentResult(true)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.classification_download_model_short))
                    }
                }
            }
        }

            when (selectedTab) {
                0 -> PasteMessageTab(state, viewModel)
                1 -> SmsListTab(state, viewModel, smsPermissionLauncher)
                2 -> AutoDetectTab(
                    state,
                    viewModel,
                    smsPermissionLauncher,
                    onTriageSwipe = { smsId, save ->
                        // Both triage swipes are undoable. A save creates a
                        // transaction and a dismiss hides a message, so neither
                        // should be one accidental gesture from being permanent.
                        if (save) {
                            // The "Saved" snackbar is emitted by the outcome
                            // collector above, once the transaction exists.
                            viewModel.handleIntent(
                                ClassificationViewIntent.SaveClassifiedSms(smsId)
                            )
                        } else {
                            viewModel.handleIntent(
                                ClassificationViewIntent.DismissClassifiedSms(smsId)
                            )
                            scope.launch {
                                val result = triageSnackbarHostState.showSnackbar(
                                    message = dismissedMessage,
                                    actionLabel = undoActionLabel,
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.handleIntent(
                                        ClassificationViewIntent.UndoDismissClassifiedSms(smsId)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            (state.aiModelState as? AiModelState.Downloading)?.let { d ->
                AiDownloadStatusBanner(progress = d.progress)
            }
        }
    }
}
