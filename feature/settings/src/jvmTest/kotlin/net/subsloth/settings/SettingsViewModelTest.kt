package net.subsloth.settings

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads settings on init`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as SettingsUiState.Content
            assertThat(content.subtitleEnabled).isTrue()
            assertThat(content.subtitleLanguage).isEqualTo("en")
            assertThat(content.quality).isEqualTo("1080p")
            assertThat(content.playbackSpeed).isEqualTo(1.0f)
            assertThat(content.downloadsWifiOnly).isTrue()
        }
    }

    @Test
    fun `updates subtitle enabled`() = runTest(testDispatcher) {
        var savedEnabled: Boolean? = null
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
            setSubtitleEnabled = { savedEnabled = it },
        )
        viewModel.setSubtitleEnabled(false)
        assertThat(savedEnabled).isFalse()
    }

    @Test
    fun `updates quality`() = runTest(testDispatcher) {
        var savedQuality: String? = null
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
            setQuality = { savedQuality = it },
        )
        viewModel.setQuality("720p")
        assertThat(savedQuality).isEqualTo("720p")
    }

    @Test
    fun `updates playback speed`() = runTest(testDispatcher) {
        var savedSpeed: Float? = null
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
            setPlaybackSpeed = { savedSpeed = it },
        )
        viewModel.setPlaybackSpeed(1.5f)
        assertThat(savedSpeed).isEqualTo(1.5f)
    }

    @Test
    fun `updates downloads wifi only`() = runTest(testDispatcher) {
        var savedWifiOnly: Boolean? = null
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
            setDownloadsWifiOnly = { savedWifiOnly = it },
        )
        viewModel.setDownloadsWifiOnly(false)
        assertThat(savedWifiOnly).isFalse()
    }

    @Test
    fun `subtitle language can be cleared`() = runTest(testDispatcher) {
        var savedLanguage: String? = "en"
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
            setSubtitleLanguage = { savedLanguage = it },
        )
        viewModel.setSubtitleLanguage(null)
        assertThat(savedLanguage).isNull()
    }

    @Test
    fun `no new-episode notification settings in v1`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as SettingsUiState.Content
            assertThat(content.showNewEpisodeNotifications).isFalse()
        }
    }

    @Test
    fun `shows loading state initially`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
        )
        viewModel.uiState.test {
            val loading = awaitItem() as SettingsUiState.Loading
            assertThat(loading).isNotNull()
        }
    }

    @Test
    fun `logout sets cleanup choices visible`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
        )
        viewModel.showLogoutCleanup()
        viewModel.uiState.test {
            val content = awaitItem() as SettingsUiState.Content
            assertThat(content.showLogoutCleanup).isTrue()
        }
    }

    @Test
    fun `logout cleanup with only delete downloads`() = runTest(testDispatcher) {
        var deletedDownloads = false
        var clearedPreferences = false
        var clearedLibrary = false
        var clearedCredentials = false
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
            deleteAllDownloads = {
                deletedDownloads = true
                Result.success(Unit)
            },
            clearPreferences = { clearedPreferences = true },
            clearLibrary = { clearedLibrary = true },
            clearCredentials = { clearedCredentials = true },
        )
        viewModel.performLogoutCleanup(deleteDownloads = true, resetPreferences = false, clearLibraryData = false)
        assertThat(deletedDownloads).isTrue()
        assertThat(clearedPreferences).isFalse()
        assertThat(clearedLibrary).isFalse()
        assertThat(clearedCredentials).isTrue()
    }

    @Test
    fun `logout cleanup with all options`() = runTest(testDispatcher) {
        var deletedDownloads = false
        var clearedPreferences = false
        var clearedLibrary = false
        var clearedCredentials = false
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
            deleteAllDownloads = {
                deletedDownloads = true
                Result.success(Unit)
            },
            clearPreferences = { clearedPreferences = true },
            clearLibrary = { clearedLibrary = true },
            clearCredentials = { clearedCredentials = true },
        )
        viewModel.performLogoutCleanup(deleteDownloads = true, resetPreferences = true, clearLibraryData = true)
        assertThat(deletedDownloads).isTrue()
        assertThat(clearedPreferences).isTrue()
        assertThat(clearedLibrary).isTrue()
        assertThat(clearedCredentials).isTrue()
    }

    @Test
    fun `logout cleanup without any options still clears credentials`() = runTest(testDispatcher) {
        var deletedDownloads = false
        var clearedPreferences = false
        var clearedLibrary = false
        var clearedCredentials = false
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
            deleteAllDownloads = {
                deletedDownloads = true
                Result.success(Unit)
            },
            clearPreferences = { clearedPreferences = true },
            clearLibrary = { clearedLibrary = true },
            clearCredentials = { clearedCredentials = true },
        )
        viewModel.performLogoutCleanup(deleteDownloads = false, resetPreferences = false, clearLibraryData = false)
        assertThat(deletedDownloads).isFalse()
        assertThat(clearedPreferences).isFalse()
        assertThat(clearedLibrary).isFalse()
        assertThat(clearedCredentials).isTrue()
    }

    @Test
    fun `diagnostics state shows redacted fields`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as SettingsUiState.Content
            assertThat(content.diagnostics.installedAppVersion).isNotEmpty()
            assertThat(content.diagnostics.buildType).isEqualTo("debug")
            assertThat(content.diagnostics.apiBaseUrl).doesNotContain("http")
        }
    }

    @Test
    fun `diagnostics does not expose sensitive fields`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            profileKey = { AccountProfileKey("profile1") },
            subtitleEnabled = { flowOf(true) },
            subtitleLanguage = { flowOf("en") },
            quality = { flowOf("1080p") },
            playbackSpeed = { flowOf(1.0f) },
            downloadsWifiOnly = { flowOf(true) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as SettingsUiState.Content
            val diag = content.diagnostics
            assertThat(diag.gitSha).isNull()
            assertThat(diag.deviceApiLevel).isNull()
            assertThat(diag.cacheAge).isNull()
            assertThat(diag.downloadQueueCounts).isNull()
            assertThat(diag.storageUsage).isNull()
        }
    }
}
