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

@Composable
fun DiagnosticsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is SettingsUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is SettingsUiState.Content -> {
            DiagnosticsContent(
                diagnostics = s.diagnostics,
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun DiagnosticsContent(diagnostics: DiagnosticsState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        item {
            Text(
                text = "Version information",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        item { DiagnosticRow("Installed app version", diagnostics.installedAppVersion) }
        item { DiagnosticRow("Build type", diagnostics.buildType) }
        item { DiagnosticRow("Version code", diagnostics.versionCode) }
        diagnostics.gitSha?.let { item { DiagnosticRow("Git SHA", it) } }
        item { DiagnosticRow("Release channel", diagnostics.releaseChannel) }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "Device",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        diagnostics.deviceApiLevel?.let { item { DiagnosticRow("Device / API level", it) } }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "Network",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        item { DiagnosticRow("API base URL", diagnostics.apiBaseUrl) }
        item { DiagnosticRow("Auth state", diagnostics.authStateCategory) }
        item { DiagnosticRow("Kodi mode", diagnostics.kodiMode) }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "Cache & Storage",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        diagnostics.cacheAge?.let { item { DiagnosticRow("Cache age", it) } }
        diagnostics.lastRefreshTime?.let { item { DiagnosticRow("Last refresh", it) } }
        diagnostics.downloadQueueCounts?.let { item { DiagnosticRow("Download queue", it) } }
        diagnostics.storageUsage?.let { item { DiagnosticRow("Storage usage", it) } }
        diagnostics.lastStatusCategory?.let { item { DiagnosticRow("Last status", it) } }
        diagnostics.lastSuccessfulRefreshAge?.let { item { DiagnosticRow("Last successful refresh", it) } }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "No export, share, or copy actions are available in v1.",
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
