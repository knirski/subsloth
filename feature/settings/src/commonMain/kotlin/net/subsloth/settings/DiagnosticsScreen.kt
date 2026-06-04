package net.subsloth.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.settings.generated.resources.Res
import subsloth.feature.settings.generated.resources.diagnostics_api_base_url
import subsloth.feature.settings.generated.resources.diagnostics_api_level
import subsloth.feature.settings.generated.resources.diagnostics_auth_state
import subsloth.feature.settings.generated.resources.diagnostics_build_type
import subsloth.feature.settings.generated.resources.diagnostics_cache_age
import subsloth.feature.settings.generated.resources.diagnostics_cache_section
import subsloth.feature.settings.generated.resources.diagnostics_device_section
import subsloth.feature.settings.generated.resources.diagnostics_download_queue
import subsloth.feature.settings.generated.resources.diagnostics_git_sha
import subsloth.feature.settings.generated.resources.diagnostics_installed_version
import subsloth.feature.settings.generated.resources.diagnostics_kodi_mode
import subsloth.feature.settings.generated.resources.diagnostics_last_refresh
import subsloth.feature.settings.generated.resources.diagnostics_last_status
import subsloth.feature.settings.generated.resources.diagnostics_last_successful_refresh
import subsloth.feature.settings.generated.resources.diagnostics_network_section
import subsloth.feature.settings.generated.resources.diagnostics_no_export
import subsloth.feature.settings.generated.resources.diagnostics_release_channel
import subsloth.feature.settings.generated.resources.diagnostics_storage_usage
import subsloth.feature.settings.generated.resources.diagnostics_title
import subsloth.feature.settings.generated.resources.diagnostics_version_code
import subsloth.feature.settings.generated.resources.diagnostics_version_section

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiagnosticsContent(state = state, modifier = modifier)
}

@Composable
internal fun DiagnosticsContent(state: DiagnosticsState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item {
            Text(
                text = stringResource(Res.string.diagnostics_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        item {
            Text(
                text = stringResource(Res.string.diagnostics_version_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        item { DiagnosticRow(stringResource(Res.string.diagnostics_installed_version), state.installedAppVersion) }
        item { DiagnosticRow(stringResource(Res.string.diagnostics_build_type), state.buildType) }
        item { DiagnosticRow(stringResource(Res.string.diagnostics_version_code), state.versionCode) }
        state.gitSha?.let { item { DiagnosticRow(stringResource(Res.string.diagnostics_git_sha), it) } }
        item { DiagnosticRow(stringResource(Res.string.diagnostics_release_channel), state.releaseChannel) }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = stringResource(Res.string.diagnostics_device_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        state.deviceApiLevel?.let { item { DiagnosticRow(stringResource(Res.string.diagnostics_api_level), it) } }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = stringResource(Res.string.diagnostics_network_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        item { DiagnosticRow(stringResource(Res.string.diagnostics_api_base_url), state.apiBaseUrl) }
        item { DiagnosticRow(stringResource(Res.string.diagnostics_auth_state), state.authStateCategory) }
        item { DiagnosticRow(stringResource(Res.string.diagnostics_kodi_mode), state.kodiMode) }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = stringResource(Res.string.diagnostics_cache_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        state.cacheAge?.let { item { DiagnosticRow(stringResource(Res.string.diagnostics_cache_age), it) } }
        state.lastRefreshTime?.let { item { DiagnosticRow(stringResource(Res.string.diagnostics_last_refresh), it) } }
        state.downloadQueueCounts?.let {
            item { DiagnosticRow(stringResource(Res.string.diagnostics_download_queue), it) }
        }
        state.storageUsage?.let { item { DiagnosticRow(stringResource(Res.string.diagnostics_storage_usage), it) } }
        state.lastStatusCategory?.let { item { DiagnosticRow(stringResource(Res.string.diagnostics_last_status), it) } }
        state.lastSuccessfulRefreshAge?.let {
            item { DiagnosticRow(stringResource(Res.string.diagnostics_last_successful_refresh), it) }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = stringResource(Res.string.diagnostics_no_export),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
