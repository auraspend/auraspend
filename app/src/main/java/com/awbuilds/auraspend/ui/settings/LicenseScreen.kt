package com.awbuilds.auraspend.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing

private const val TAG = "LicenseScreen"
private const val REPO_URL = "https://github.com/auraspend/auraspend"

/**
 * A bundled component that AuraSpend redistributes, and the licence it carries.
 *
 * Deliberately a hard-coded list rather than something derived at runtime. It
 * cannot silently fall out of date with `build.gradle.kts` — which is the
 * failure mode that matters here, because an out-of-date attribution is a
 * licence-compliance bug, not a cosmetic one. `tools/check_license.sh` fails CI
 * if a dependency is added without a row appearing here.
 */
private data class ThirdPartyComponent(
    val name: String,
    val license: String,
    val role: ThirdPartyRole,
    val url: String
)

private enum class ThirdPartyRole { BUNDLED, DOWNLOADED }

private val THIRD_PARTY = listOf(
    ThirdPartyComponent(
        name = "Plus Jakarta Sans",
        license = "SIL Open Font License 1.1",
        role = ThirdPartyRole.BUNDLED,
        url = "https://fonts.google.com/specimen/Plus+Jakarta+Sans"
    ),
    ThirdPartyComponent(
        name = "ONNX Runtime (Android)",
        license = "MIT",
        role = ThirdPartyRole.BUNDLED,
        url = "https://onnxruntime.ai/"
    ),
    ThirdPartyComponent(
        name = "SQLCipher (Zetetic, Community Edition)",
        license = "BSD-3-Clause",
        role = ThirdPartyRole.BUNDLED,
        url = "https://www.zetetic.net/sqlcipher/"
    ),
    ThirdPartyComponent(
        name = "AndroidX — Room, Compose, Lifecycle, Work, Navigation, Paging, ProfileInstaller",
        license = "Apache-2.0",
        role = ThirdPartyRole.BUNDLED,
        url = "https://developer.android.com/jetpack/androidx"
    ),
    ThirdPartyComponent(
        name = "Google API Client, Google Drive API, play-services-auth",
        license = "Apache-2.0",
        role = ThirdPartyRole.BUNDLED,
        url = "https://developers.google.com/drive"
    ),
    ThirdPartyComponent(
        name = "OkHttp, Guava, google-http-client-gson",
        license = "Apache-2.0",
        role = ThirdPartyRole.BUNDLED,
        url = "https://square.github.io/okhttp/"
    ),
    ThirdPartyComponent(
        name = "all-MiniLM-L6-v2 (sentence-transformers)",
        license = "Apache-2.0",
        role = ThirdPartyRole.DOWNLOADED,
        url = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2"
    ),
    ThirdPartyComponent(
        name = "PennyWise AI — adapted parsing patterns (SMS amount, merchant and ingest-guard logic)",
        license = "AGPL-3.0",
        role = ThirdPartyRole.BUNDLED,
        url = "https://github.com/sarim2000/pennywiseai-tracker"
    )
)

/**
 * The open-source licences screen, reached from Settings → About.
 *
 * This exists because AGPL-3.0 §4 requires that a copy of the Licence accompany
 * the work. The app previously displayed only the string "Apache-2.0" with no way
 * to read anything, which satisfied no licence and was not a real attribution
 * surface for the bundled components either.
 *
 * Two panes via [AuraSegmentedControl] rather than one long scrolling page: the
 * full licence is ~34 KB, and nesting a scrollable text block inside a
 * `LazyColumn` produces the gesture conflict where neither scroll responds
 * reliably. Separate panes give the text the whole viewport to itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    // LocalResources, not LocalContext.current.resources: the latter is not
    // configuration-aware, so the read would not be invalidated on a
    // Configuration change. The licence text is not localised so it would not
    // visibly differ today, but the API is simply the wrong one and lint is
    // right.
    val resources = LocalResources.current

    // Read once, off the composition path, and fail soft: a missing resource
    // must show the empty state, not crash the screen. The exception is logged,
    // never surfaced, per AGENTS.md rule 1.
    val licenseText by produceState<String?>(initialValue = null, resources) {
        value = boundaryOrNull(TAG) {
            resources.openRawResource(R.raw.agpl_3_0)
                .bufferedReader()
                .use { it.readText() }
        }
    }

    var pane by rememberSaveable { mutableIntStateOf(0) }
    val panes = listOf(
        stringResource(R.string.license_tab_components),
        stringResource(R.string.license_tab_license)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.license_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AuraSpacing.gutter)
        ) {
            Spacer(modifier = Modifier.height(AuraSpacing.md))
            AuraSegmentedControl(
                options = panes,
                selectedIndex = pane,
                onSelect = { pane = it },
                modifier = Modifier.testTag("license-pane-selector")
            )
            Spacer(modifier = Modifier.height(AuraSpacing.md))

            when (pane) {
                0 -> ComponentsPane()
                else -> LicenseTextPane(text = licenseText)
            }
        }
    }
}

@Composable
private fun ComponentsPane() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("license-components"),
        contentPadding = PaddingValues(bottom = AuraSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
    ) {
        item {
            AuraCard(
                modifier = Modifier.fillMaxWidth(),
                style = AuraCardStyle.Tonal
            ) {
                Text(
                    stringResource(R.string.license_project_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                Text(
                    stringResource(R.string.license_project_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AuraSpacing.sm))
                OpenLinkButton(
                    label = stringResource(R.string.license_view_source),
                    url = REPO_URL
                )
            }
        }

        item {
            Text(
                stringResource(R.string.license_third_party_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(top = AuraSpacing.sm)
                    .semantics { heading() }
            )
        }

        items(THIRD_PARTY, key = { it.name }) { component ->
            AuraCard(
                modifier = Modifier.fillMaxWidth(),
                style = AuraCardStyle.Outlined,
                contentPadding = PaddingValues(AuraSpacing.md)
            ) {
                Text(
                    component.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AuraSpacing.xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        component.license,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (component.role == ThirdPartyRole.DOWNLOADED) {
                        Spacer(modifier = Modifier.width(AuraSpacing.sm))
                        Text(
                            stringResource(R.string.license_downloaded_at_runtime),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseTextPane(text: String?) {
    if (text == null) {
        // No raw exception in screen state: log the cause, show a recoverable
        // state. See AGENTS.md rule 1.
        AuraEmptyState(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.license_text_unavailable_title),
            message = stringResource(R.string.license_text_unavailable_body)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("license-text"),
        contentPadding = PaddingValues(bottom = AuraSpacing.xxxl)
    ) {
        item {
            AuraCard(
                modifier = Modifier.fillMaxWidth(),
                style = AuraCardStyle.Outlined,
                contentPadding = PaddingValues(AuraSpacing.md)
            ) {
                // Monospace because this is a legal document, not UI copy: the
                // clause numbering and indentation carry meaning. heightIn rather
                // than height so it survives 200% font scale.
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.heightIn(min = 0.dp)
                )
            }
        }
    }
}

@Composable
private fun OpenLinkButton(label: String, url: String) {
    val context = LocalContext.current
    TextButton(
        onClick = {
            // No browser to handle the link must not crash the screen, so the
            // launch goes through the boundary rather than throwing
            // ActivityNotFoundException into composition.
            boundaryOrNull(TAG) {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
        }
    ) {
        Text(label)
        Spacer(modifier = Modifier.width(AuraSpacing.xs))
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}
