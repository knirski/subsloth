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
import net.subsloth.settings.DiagnosticsContent
import net.subsloth.settings.DiagnosticsState

@PreviewTest
@Preview(name = "Phone Light", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Light", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Light", device = DEVICE_TV, showBackground = true)
@Composable
fun DiagnosticsScreenLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            DiagnosticsContent(
                state =
                    DiagnosticsState(
                        installedAppVersion = "1.2.3",
                        buildType = "release",
                        versionCode = "10203",
                        gitSha = "abc123def",
                        releaseChannel = "stable",
                        deviceApiLevel = "34",
                        apiBaseUrl = "https://api.example.com",
                        authStateCategory = "authenticated",
                        cacheAge = "5 minutes",
                        lastRefreshTime = "2024-01-15 10:30:00",
                        downloadQueueCounts = "2 active, 1 queued",
                        storageUsage = "1.2 GB used of 8 GB",
                        lastStatusCategory = "success",
                        lastSuccessfulRefreshAge = "3 minutes ago",
                        kodiMode = "Kodi-compatible request mode: enabled",
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
fun DiagnosticsScreenDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            DiagnosticsContent(
                state =
                    DiagnosticsState(
                        installedAppVersion = "1.2.3",
                        buildType = "release",
                        versionCode = "10203",
                        gitSha = "abc123def",
                        releaseChannel = "stable",
                        deviceApiLevel = "34",
                        apiBaseUrl = "https://api.example.com",
                        authStateCategory = "authenticated",
                        cacheAge = "5 minutes",
                        lastRefreshTime = "2024-01-15 10:30:00",
                        downloadQueueCounts = "2 active, 1 queued",
                        storageUsage = "1.2 GB used of 8 GB",
                        lastStatusCategory = "success",
                        lastSuccessfulRefreshAge = "3 minutes ago",
                        kodiMode = "Kodi-compatible request mode: enabled",
                    ),
            )
        }
    }
}
