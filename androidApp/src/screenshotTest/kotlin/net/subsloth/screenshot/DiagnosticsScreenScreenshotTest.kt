package net.subsloth.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.settings.DiagnosticsContent
import net.subsloth.settings.DiagnosticsState

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Light", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Light", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Light", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
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

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Dark", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Dark", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Dark", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
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
