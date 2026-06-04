package net.subsloth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.settings.generated.resources.Res
import subsloth.feature.settings.generated.resources.settings_diagnostics
import subsloth.feature.settings.generated.resources.settings_downloads_section
import subsloth.feature.settings.generated.resources.settings_downloads_wifi_only
import subsloth.feature.settings.generated.resources.settings_logout
import subsloth.feature.settings.generated.resources.settings_logout_cancel
import subsloth.feature.settings.generated.resources.settings_logout_cleanup_clear_library
import subsloth.feature.settings.generated.resources.settings_logout_cleanup_delete_downloads
import subsloth.feature.settings.generated.resources.settings_logout_cleanup_message
import subsloth.feature.settings.generated.resources.settings_logout_cleanup_reset_preferences
import subsloth.feature.settings.generated.resources.settings_logout_cleanup_title
import subsloth.feature.settings.generated.resources.settings_logout_confirm
import subsloth.feature.settings.generated.resources.settings_playback_speed
import subsloth.feature.settings.generated.resources.settings_preferred_quality
import subsloth.feature.settings.generated.resources.settings_quality_playback_section
import subsloth.feature.settings.generated.resources.settings_subtitle_language
import subsloth.feature.settings.generated.resources.settings_subtitle_section
import subsloth.feature.settings.generated.resources.settings_subtitles_enabled
import subsloth.feature.settings.generated.resources.settings_title

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onNavigateToDiagnostics: () -> Unit = {},
) {
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

        is SettingsUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.error.detail ?: "Something went wrong",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is SettingsUiState.Content -> {
            SettingsContent(
                state = s,
                modifier = modifier,
                onSubtitleEnabledChanged = viewModel::onSubtitleEnabledChanged,
                onSubtitleLanguageChanged = viewModel::onSubtitleLanguageChanged,
                onQualityChanged = viewModel::onQualityChanged,
                onPlaybackSpeedChanged = viewModel::onPlaybackSpeedChanged,
                onDownloadsWifiOnlyChanged = viewModel::onDownloadsWifiOnlyChanged,
                onLogoutClick = viewModel::showLogoutCleanup,
                onNavigateToDiagnostics = onNavigateToDiagnostics,
                onPerformLogoutCleanup = viewModel::performLogoutCleanup,
                onDismissLogoutCleanup = viewModel::dismissLogoutCleanup,
            )
        }
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState.Content,
    modifier: Modifier = Modifier,
    onSubtitleEnabledChanged: (Boolean) -> Unit = {},
    onSubtitleLanguageChanged: (String?) -> Unit = {},
    onQualityChanged: (String?) -> Unit = {},
    onPlaybackSpeedChanged: (Float) -> Unit = {},
    onDownloadsWifiOnlyChanged: (Boolean) -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onPerformLogoutCleanup: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onDismissLogoutCleanup: () -> Unit = {},
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.settings_subtitle_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.settings_subtitles_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Checkbox(
                        checked = state.subtitleEnabled,
                        onCheckedChange = onSubtitleEnabledChanged,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.settings_subtitle_language),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = state.subtitleLanguage ?: "Default",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(Res.string.settings_quality_playback_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.settings_preferred_quality),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = state.quality ?: "Auto",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Column {
                    var sliderValue by remember(state.playbackSpeed) { mutableStateOf(state.playbackSpeed) }
                    Text(
                        stringResource(Res.string.settings_playback_speed, sliderValue),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onPlaybackSpeedChanged(sliderValue) },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Text(
                    text = stringResource(Res.string.settings_downloads_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.settings_downloads_wifi_only),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Checkbox(
                        checked = state.downloadsWifiOnly,
                        onCheckedChange = onDownloadsWifiOnlyChanged,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                OutlinedButton(
                    onClick = onNavigateToDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.settings_diagnostics))
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Button(
                    onClick = onLogoutClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(Res.string.settings_logout))
                }
            }
        }

        if (state.showLogoutCleanup) {
            LogoutCleanupDialog(
                onConfirm = onPerformLogoutCleanup,
                onDismiss = onDismissLogoutCleanup,
            )
        }
    }
}

@Composable
private fun LogoutCleanupDialog(
    onConfirm: (deleteDownloads: Boolean, resetPreferences: Boolean, clearLibrary: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteDownloads by remember { mutableStateOf(false) }
    var resetPreferences by remember { mutableStateOf(false) }
    var clearLibraryData by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_logout_cleanup_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.settings_logout_cleanup_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteDownloads, onCheckedChange = { deleteDownloads = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.settings_logout_cleanup_delete_downloads),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = resetPreferences, onCheckedChange = { resetPreferences = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.settings_logout_cleanup_reset_preferences),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearLibraryData, onCheckedChange = { clearLibraryData = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.settings_logout_cleanup_clear_library),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(deleteDownloads, resetPreferences, clearLibraryData) }) {
                Text(stringResource(Res.string.settings_logout_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_logout_cancel)) }
        },
    )
}
