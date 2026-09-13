package net.subsloth.settings

import androidx.compose.runtime.Composable

@Composable
fun SettingsScreenPreview() {
    SettingsContent(
        state = SettingsUiState.Content(
            subtitleEnabled = true,
            subtitleLanguage = "en",
            quality = "1080p",
            downloadsWifiOnly = true,
            diagnostics = DiagnosticsState.REDACTED,
        ),
    )
}

@Composable
fun DiagnosticsScreenPreview() {
    DiagnosticsContent(
        state = DiagnosticsState.REDACTED,
    )
}
