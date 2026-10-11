package com.awbuilds.auraspend.ui.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.core.UiError
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraErrorState
import com.awbuilds.auraspend.ui.designsystem.AuraSkeleton
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Full-screen creation flow backed by the shared [TransactionEditor]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTransactionScreen(
    repository: TransactionRepository,
    onBack: () -> Unit
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    // A read that failed before the form exists, or a write that failed after.
    var error by remember { mutableStateOf<UiError?>(null) }
    val scope = rememberCoroutineScope()
    val errorMessage = error?.let { stringResource(it.messageRes) }

    LaunchedEffect(reload) {
        // Guarded: a category read failure used to leave this screen rendering
        // skeletons forever, indistinguishable from "still loading".
        val loaded = boundaryOrNull(TAG) { repository.getAllCategories().first() }
        categories = loaded.orEmpty()
        error = if (loaded == null) UiError.LOAD_FAILED else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_txn_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (error == UiError.LOAD_FAILED && categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                AuraErrorState(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.error_load_failed),
                    message = stringResource(R.string.error_load_failed_detail),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = {
                        error = null
                        reload++
                    }
                )
            }
        } else if (categories.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AuraSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.md)
            ) {
                Spacer(modifier = Modifier.height(AuraSpacing.md))
                AuraSkeleton(modifier = Modifier.fillMaxWidth().height(44.dp))
                AuraSkeleton(modifier = Modifier.fillMaxWidth().height(56.dp))
                AuraSkeleton(modifier = Modifier.fillMaxWidth().height(56.dp))
                AuraSkeleton(modifier = Modifier.fillMaxWidth().height(120.dp))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // A failed write is reported above the form, which stays on screen with
                // everything the user typed instead of navigating away as if saved.
                errorMessage?.let { message ->
                    AuraErrorBanner(
                        message = message,
                        modifier = Modifier.padding(
                            horizontal = AuraSpacing.gutter,
                            vertical = AuraSpacing.sm
                        ),
                        onDismiss = { error = null }
                    )
                }
                TransactionEditor(
                    transaction = null,
                    categories = categories,
                    isNew = true,
                    onCancel = onBack,
                    onSave = { transaction ->
                        scope.launch {
                            val saved = boundary(TAG, false) {
                                repository.saveTransaction(transaction)
                                true
                            }
                            if (saved) {
                                error = null
                                onBack()
                            } else {
                                error = UiError.SAVE_FAILED
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Log tag for this screen's IO boundaries. */
private const val TAG = "NewTransaction"
