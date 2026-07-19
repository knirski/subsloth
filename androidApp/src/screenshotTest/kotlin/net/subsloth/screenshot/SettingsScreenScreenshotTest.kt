package net.subsloth.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.settings.DiagnosticsState
import net.subsloth.settings.SettingsContent
import net.subsloth.settings.SettingsUiState

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Light", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Light", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Light", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
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

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Dark", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Dark", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Dark", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
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
