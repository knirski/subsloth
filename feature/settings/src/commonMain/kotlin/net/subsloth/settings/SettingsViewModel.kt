package net.subsloth.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val setSubtitleEnabled: suspend (Boolean) -> Unit = {},
    private val setSubtitleLanguage: suspend (String?) -> Unit = {},
    private val setQuality: suspend (String?) -> Unit = {},
    private val setPlaybackSpeed: suspend (Float) -> Unit = {},
    private val setDownloadsWifiOnly: suspend (Boolean) -> Unit = {},
    private val deleteAllDownloads: suspend () -> Result<Unit> = { Result.success(Unit) },
    private val clearPreferences: suspend (AccountProfileKey) -> Unit = {},
    private val clearLibrary: suspend () -> Unit = {},
    private val clearCredentials: suspend () -> Unit = {},
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

    fun setSubtitleEnabled(enabled: Boolean) {
        viewModelScope.launch { setSubtitleEnabled(enabled) }
    }

    fun setSubtitleLanguage(language: String?) {
        viewModelScope.launch { setSubtitleLanguage(language) }
    }

    fun setQuality(quality: String?) {
        viewModelScope.launch { setQuality(quality) }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { setPlaybackSpeed(speed) }
    }

    fun setDownloadsWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { setDownloadsWifiOnly(wifiOnly) }
    }

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
        viewModelScope.launch {
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
}
