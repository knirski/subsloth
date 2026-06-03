package net.subsloth.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.subsloth.core.model.identifier.AccountProfileKey

@Stable
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    @Immutable
    data class Content(
        val subtitleEnabled: Boolean,
        val subtitleLanguage: String?,
        val quality: String?,
        val playbackSpeed: Float,
        val downloadsWifiOnly: Boolean,
        val showNewEpisodeNotifications: Boolean = false,
        val showLogoutCleanup: Boolean = false,
        val diagnostics: DiagnosticsState,
    ) : SettingsUiState
}

@Immutable
data class DiagnosticsState(
    val installedAppVersion: String = "1.0.0",
    val buildType: String = "debug",
    val versionCode: String = "1",
    val gitSha: String? = null,
    val releaseChannel: String = "debug-sideload",
    val deviceApiLevel: String? = null,
    val apiBaseUrl: String = "redacted",
    val authStateCategory: String = "unknown",
    val cacheAge: String? = null,
    val lastRefreshTime: String? = null,
    val downloadQueueCounts: String? = null,
    val storageUsage: String? = null,
    val lastStatusCategory: String? = null,
    val lastSuccessfulRefreshAge: String? = null,
    val kodiMode: String = "Kodi-compatible request mode: enabled",
) {
    companion object {
        val REDACTED: DiagnosticsState = DiagnosticsState()
    }
}

class SettingsViewModel(
    private val profileKey: () -> AccountProfileKey = { AccountProfileKey("default") },
    initialSubtitleEnabled: Boolean = true,
    initialSubtitleLanguage: String? = null,
    initialQuality: String? = null,
    initialPlaybackSpeed: Float = 1.0f,
    initialDownloadsWifiOnly: Boolean = true,
    private val setSubtitleEnabled: (Boolean) -> Unit = {},
    private val setSubtitleLanguage: (String?) -> Unit = {},
    private val setQuality: (String?) -> Unit = {},
    private val setPlaybackSpeed: (Float) -> Unit = {},
    private val setDownloadsWifiOnly: (Boolean) -> Unit = {},
    private val deleteAllDownloads: () -> Unit = {},
    private val clearPreferences: (AccountProfileKey) -> Unit = {},
    private val clearLibrary: () -> Unit = {},
    private val clearCredentials: () -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Content(
        subtitleEnabled = initialSubtitleEnabled,
        subtitleLanguage = initialSubtitleLanguage,
        quality = initialQuality,
        playbackSpeed = initialPlaybackSpeed,
        downloadsWifiOnly = initialDownloadsWifiOnly,
        diagnostics = DiagnosticsState.REDACTED,
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onSubtitleEnabledChanged(enabled: Boolean) { this.setSubtitleEnabled(enabled) }

    fun onSubtitleLanguageChanged(language: String?) { this.setSubtitleLanguage(language) }

    fun onQualityChanged(quality: String?) { this.setQuality(quality) }

    fun onPlaybackSpeedChanged(speed: Float) { this.setPlaybackSpeed(speed) }

    fun onDownloadsWifiOnlyChanged(wifiOnly: Boolean) { this.setDownloadsWifiOnly(wifiOnly) }

    fun showLogoutCleanup() {
        _uiState.update { current ->
            if (current is SettingsUiState.Content) current.copy(showLogoutCleanup = true) else current
        }
    }

    fun dismissLogoutCleanup() {
        _uiState.update { current ->
            if (current is SettingsUiState.Content) current.copy(showLogoutCleanup = false) else current
        }
    }

    fun performLogoutCleanup(deleteDownloads: Boolean, resetPreferences: Boolean, clearLibraryData: Boolean) {
        if (deleteDownloads) deleteAllDownloads()
        val key = profileKey()
        if (resetPreferences) clearPreferences(key)
        if (clearLibraryData) clearLibrary()
        clearCredentials()
        _uiState.update { current ->
            if (current is SettingsUiState.Content) current.copy(showLogoutCleanup = false) else current
        }
    }
}
