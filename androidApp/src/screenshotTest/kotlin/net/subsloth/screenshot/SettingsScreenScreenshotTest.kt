package net.subsloth.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.screenshot.DEVICE_PHONE
import net.subsloth.screenshot.DEVICE_TABLET
import net.subsloth.screenshot.DEVICE_TV
import net.subsloth.settings.DiagnosticsState
import net.subsloth.settings.SettingsContent
import net.subsloth.settings.SettingsUiState

@PreviewTest
@Preview(name = "Phone Light", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Light", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Light", device = DEVICE_TV, showBackground = true)
@Composable
fun SettingsScreenLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SettingsContent(
                state =
                    SettingsUiState.Content(
                        subtitleEnabled = true,
                        subtitleLanguage = "en",
                        quality = "1080p",
                        playbackSpeed = 1.0f,
                        downloadsWifiOnly = true,
                        diagnostics = DiagnosticsState.REDACTED,
                    ),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Phone Dark", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Dark", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Dark", device = DEVICE_TV, showBackground = true)
@Composable
fun SettingsScreenDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SettingsContent(
                state =
                    SettingsUiState.Content(
                        subtitleEnabled = true,
                        subtitleLanguage = "en",
                        quality = "1080p",
                        playbackSpeed = 1.0f,
                        downloadsWifiOnly = true,
                        diagnostics = DiagnosticsState.REDACTED,
                    ),
            )
        }
    }
}
