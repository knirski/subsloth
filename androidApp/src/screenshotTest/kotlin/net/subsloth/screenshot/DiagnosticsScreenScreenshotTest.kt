package net.subsloth.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.subsloth.settings.DiagnosticsContent
import net.subsloth.settings.DiagnosticsState

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun DiagnosticsScreenScreenshot() {
    MaterialTheme {
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
