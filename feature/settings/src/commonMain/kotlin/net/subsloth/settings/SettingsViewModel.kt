package net.subsloth.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    private val readSubtitleEnabled: suspend (AccountProfileKey) -> Flow<Boolean> = { flowOf(true) },
    private val readSubtitleLanguage: suspend (AccountProfileKey) -> Flow<String?> = { flowOf(null) },
    private val readQuality: suspend (AccountProfileKey) -> Flow<String?> = { flowOf(null) },
    private val readPlaybackSpeed: suspend (AccountProfileKey) -> Flow<Float> = { flowOf(1.0f) },
    private val readDownloadsWifiOnly: suspend (AccountProfileKey) -> Flow<Boolean> = { flowOf(true) },
    private val writeSubtitleEnabled: (Boolean) -> Unit = {},
    private val writeSubtitleLanguage: (String?) -> Unit = {},
    private val writeQuality: (String?) -> Unit = {},
    private val writePlaybackSpeed: (Float) -> Unit = {},
    private val writeDownloadsWifiOnly: (Boolean) -> Unit = {},
    private val deleteAllDownloads: () -> Unit = {},
    private val clearPreferences: (AccountProfileKey) -> Unit = {},
    private val clearLibrary: () -> Unit = {},
    private val clearCredentials: () -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(
        SettingsUiState.Content(
            subtitleEnabled = true,
            subtitleLanguage = null,
            quality = null,
            playbackSpeed = 1.0f,
            downloadsWifiOnly = true,
            diagnostics = DiagnosticsState.REDACTED,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val key = profileKey()
            val enabled = readSubtitleEnabled(key).first()
            val lang = readSubtitleLanguage(key).first()
            val qual = readQuality(key).first()
            val speed = readPlaybackSpeed(key).first()
            val wifi = readDownloadsWifiOnly(key).first()
            _uiState.value = SettingsUiState.Content(
                subtitleEnabled = enabled,
                subtitleLanguage = lang,
                quality = qual,
                playbackSpeed = speed,
                downloadsWifiOnly = wifi,
                diagnostics = DiagnosticsState.REDACTED,
            )
        }
    }

    fun onSubtitleEnabledChanged(enabled: Boolean) {
        writeSubtitleEnabled(enabled)
    }

    fun onSubtitleLanguageChanged(language: String?) {
        writeSubtitleLanguage(language)
    }

    fun onQualityChanged(quality: String?) {
        writeQuality(quality)
    }

    fun onPlaybackSpeedChanged(speed: Float) {
        writePlaybackSpeed(speed)
    }

    fun onDownloadsWifiOnlyChanged(wifiOnly: Boolean) {
        writeDownloadsWifiOnly(wifiOnly)
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
