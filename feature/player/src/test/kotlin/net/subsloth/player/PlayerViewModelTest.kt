package net.subsloth.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.domain.policy.PlaybackSpeedPolicy
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.playback.PlaybackError
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────

    @Test
    fun `initial state is Content after immediate execution`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(PlayerUiState.Content::class.java)
    }

    @Test
    fun `loads content and transitions to Content state`() = runTest(testDispatcher) {
        val source = createVideoSource()
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(PlayerUiState.Content::class.java)
        val content = state as PlayerUiState.Content
        assertThat(content.playbackError).isNull()
    }

    @Test
    fun `shows error when fetchVideoSource fails`() = runTest(testDispatcher) {
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.failure(Exception("Network error")) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.playbackError).isNotNull()
        assertThat(state.playbackError!!.message).contains("Network error")
    }

    @Test
    fun `shows error for invalid content identifier`() = runTest(testDispatcher) {
        val viewModel = createViewModel(contentId = "invalid", contentType = "unknown")

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.playbackError).isNotNull()
    }

    @Test
    fun `dismissNextEpisode hides prompt`() = runTest(testDispatcher) {
        val source = createVideoSource()
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        viewModel.dismissNextEpisode()

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.showNextEpisodePrompt).isFalse()
    }

    @Test
    fun `retryPlayback reloads content`() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = {
                    callCount++
                    Result.success(createVideoSource())
                },
            )

        assertThat(callCount).isEqualTo(1)

        viewModel.retryPlayback()
        assertThat(callCount).isEqualTo(2)
    }

    @Test
    fun `selectSubtitle does not crash`() = runTest(testDispatcher) {
        val subtitle = createSubtitle()
        val source = createVideoSource(availableSubtitles = listOf(subtitle))
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        viewModel.selectSubtitle(subtitle)
        viewModel.selectSubtitle(null)
    }

    @Test
    fun `setPlaybackSpeed does not crash`() = runTest(testDispatcher) {
        val source = createVideoSource()
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        viewModel.setPlaybackSpeed(1.5f)
        viewModel.setPlaybackSpeed(1.0f)
    }

    @Test
    fun `selectQuality does not crash`() = runTest(testDispatcher) {
        val source = createVideoSource()
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        viewModel.selectQuality("720p")
    }

    @Test
    fun `contentId and contentType are parsed into correct mediaId`() = runTest(testDispatcher) {
        val results = mutableListOf<Media.MediaId>()
        val viewModel =
            createViewModel(
                contentId = "42",
                contentType = "movie",
                fetchVideoSource = { mediaId ->
                    results.add(mediaId)
                    Result.success(createVideoSource())
                },
            )

        assertThat(results).hasSize(1)
        assertThat(results.first()).isEqualTo(Media.MediaId.Movie(MovieId(42)))
    }

    @Test
    fun `auth failure triggers onAuthFailure callback`() = runTest(testDispatcher) {
        var authFailureCalled = false
        createViewModel(
            contentId = "1",
            contentType = "movie",
            fetchVideoSource = { Result.failure(Exception("401 auth failed")) },
            onAuthFailure = { authFailureCalled = true },
        )

        assertThat(authFailureCalled).isTrue()
    }

    @Test
    fun `non-auth error does not trigger auth failure`() = runTest(testDispatcher) {
        var authFailureCalled = false
        createViewModel(
            contentId = "1",
            contentType = "movie",
            fetchVideoSource = { Result.failure(Exception("Network timeout")) },
            onAuthFailure = { authFailureCalled = true },
        )

        assertThat(authFailureCalled).isFalse()
    }

    @Test
    fun `sets duration from video source`() = runTest(testDispatcher) {
        val source = createVideoSource(durationSeconds = 1800L)
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.durationSeconds).isEqualTo(1800L)
    }

    // ── Offline playback ─────────────────────────────────────────────────

    @Test
    fun `offline playback sets playbackMode to OFFLINE`() = runTest(testDispatcher) {
        val source = createVideoSource(playbackMode = PlaybackMode.OFFLINE)
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.playbackMode).isEqualTo(PlaybackMode.OFFLINE)
    }

    @Test
    fun `online playback sets playbackMode to ONLINE`() = runTest(testDispatcher) {
        val source = createVideoSource(playbackMode = PlaybackMode.ONLINE)
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.playbackMode).isEqualTo(PlaybackMode.ONLINE)
    }

    // ── Auth failure handling ─────────────────────────────────────────────

    @Test
    fun `auth failure during initial load routes to auth repair without progress`() = runTest(testDispatcher) {
        var authFailureCalled = false
        var savedProgress: Triple<Media.MediaId, Long, Long>? = null
        createViewModel(
            contentId = "1",
            contentType = "movie",
            fetchVideoSource = { Result.failure(Exception("401 Unauthorized")) },
            saveProgress = { id, pos, dur ->
                savedProgress = Triple(id, pos, dur)
            },
            onAuthFailure = { authFailureCalled = true },
        )

        // Auth failure is routed even when no progress exists yet
        assertThat(authFailureCalled).isTrue()
        // Progress cannot be saved before playback starts
        assertThat(savedProgress).isNull()
    }

    @Test
    fun `auth failure sets playbackError to AuthFailure`() = runTest(testDispatcher) {
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.failure(Exception("401 Unauthorized")) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.playbackError).isInstanceOf(PlaybackError.AuthFailure::class.java)
    }

    // ── Quality fallback notice ───────────────────────────────────────────

    @Test
    fun `qualityFallbackNotice is null by default`() = runTest(testDispatcher) {
        val source = createVideoSource()
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.qualityFallbackNotice).isNull()
    }

    // ── Stream refresh ────────────────────────────────────────────────────

    @Test
    fun `retryWithRefresh on offline playback is a no-op`() = runTest(testDispatcher) {
        var refreshCallCount = 0
        val source = createVideoSource(playbackMode = PlaybackMode.OFFLINE)
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
                refreshStreamUrl = {
                    refreshCallCount++
                    Result.failure(UnsupportedOperationException("Not implemented"))
                },
            )

        viewModel.retryWithRefresh()

        assertThat(refreshCallCount).isEqualTo(0)
    }

    @Test
    fun `second refresh attempt after streamRefreshUsed is blocked`() = runTest(testDispatcher) {
        var refreshCallCount = 0
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(createVideoSource()) },
                refreshStreamUrl = {
                    refreshCallCount++
                    Result.success(createVideoSource())
                },
            )

        viewModel.retryWithRefresh()
        viewModel.retryWithRefresh()

        assertThat(refreshCallCount).isEqualTo(1)
    }

    // ── Playback speed ────────────────────────────────────────────────────

    @Test
    fun `setPlaybackSpeed calls savePlaybackSpeed for online playback`() = runTest(testDispatcher) {
        val savedSpeeds = mutableListOf<Float>()
        val source = createVideoSource()
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
                savePlaybackSpeed = { savedSpeeds.add(it) },
            )

        viewModel.setPlaybackSpeed(1.5f)

        assertThat(savedSpeeds).containsExactly(1.5f)
    }

    @Test
    fun `setPlaybackSpeed does not call savePlaybackSpeed for offline playback`() = runTest(testDispatcher) {
        var savePlaybackSpeedCalled = false
        val source = createVideoSource(playbackMode = PlaybackMode.OFFLINE)
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
                savePlaybackSpeed = { savePlaybackSpeedCalled = true },
            )

        viewModel.setPlaybackSpeed(1.5f)

        assertThat(savePlaybackSpeedCalled).isFalse()
    }

    // ── Subtitle fallback ──────────────────────────────────────────────────

    @Test
    fun `subtitle fallback selects English when available`() = runTest(testDispatcher) {
        val enSubtitle = createSubtitle()
        val esSubtitle = createSpanishSubtitle()
        val source = createVideoSource(availableSubtitles = listOf(enSubtitle, esSubtitle))
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.selectedSubtitle).isNotNull()
        assertThat(state.selectedSubtitle!!.language).isEqualTo(LanguageCode("en"))
    }

    @Test
    fun `subtitle fallback selects first available when English is unavailable`() = runTest(testDispatcher) {
        val esSubtitle = createSpanishSubtitle()
        val source = createVideoSource(availableSubtitles = listOf(esSubtitle))
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.selectedSubtitle).isNotNull()
        assertThat(state.selectedSubtitle!!.language).isEqualTo(LanguageCode("es"))
    }

    @Test
    fun `subtitle fallback notice shown when falling back from preferred language`() = runTest(testDispatcher) {
        val esSubtitle = createSpanishSubtitle()
        val source = createVideoSource(availableSubtitles = listOf(esSubtitle))
        val viewModel =
            createViewModel(
                contentId = "1",
                contentType = "movie",
                fetchVideoSource = { Result.success(source) },
            )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.subtitleFallbackNotice).isNotNull()
    }

    // ── Next-episode flow (Fix 1) ──────────────────────────────────────────

    @Test
    fun `next episode is set when playing show content`() = runTest(testDispatcher) {
        val episode1 = createEpisode(id = 1, seasonNumber = 1, episodeNumber = 1)
        val episode2 = createEpisode(id = 2, seasonNumber = 1, episodeNumber = 2)
        val source = createVideoSource(
            mediaId = Media.MediaId.Episode(EpisodeId(1)),
        )
        var fetchedShowId: ShowId? = null
        val viewModel = createViewModel(
            contentId = "1",
            contentType = "show",
            fetchVideoSource = { Result.success(source) },
            fetchEpisodes = { showId ->
                fetchedShowId = showId.value
                Result.success(listOf(episode1, episode2))
            },
        )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.nextEpisode).isNotNull()
        assertThat(state.nextEpisode!!.id).isEqualTo(EpisodeId(2))
    }

    @Test
    fun `next episode is null when current is last episode`() = runTest(testDispatcher) {
        val episode1 = createEpisode(id = 1, seasonNumber = 1, episodeNumber = 1)
        val episode2 = createEpisode(id = 2, seasonNumber = 1, episodeNumber = 2)
        val source = createVideoSource(
            mediaId = Media.MediaId.Episode(EpisodeId(2)),
        )
        val viewModel = createViewModel(
            contentId = "1",
            contentType = "show",
            fetchVideoSource = { Result.success(source) },
            fetchEpisodes = {
                Result.success(listOf(episode1, episode2))
            },
        )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.nextEpisode).isNull()
    }

    @Test
    fun `next episode is null when fetchEpisodes returns empty list`() = runTest(testDispatcher) {
        val source = createVideoSource(
            mediaId = Media.MediaId.Episode(EpisodeId(1)),
        )
        val viewModel = createViewModel(
            contentId = "1",
            contentType = "show",
            fetchVideoSource = { Result.success(source) },
            fetchEpisodes = { Result.success(emptyList()) },
        )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.nextEpisode).isNull()
    }

    @Test
    fun `next episode is null for movie content`() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            contentId = "1",
            contentType = "movie",
            fetchVideoSource = { Result.success(createVideoSource()) },
        )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.nextEpisode).isNull()
    }

    // ── Quality state (Fix 2) ──────────────────────────────────────────────

    @Test
    fun `availableQualities are populated in state after playback starts`() = runTest(testDispatcher) {
        val quality1 = createQuality()
        val quality2 = Quality(
            info = QualityDescriptor(
                resolution = Resolution.HD_720,
                label = "720p",
                bitrate = null,
                mimeType = null,
            ),
            url = null,
            downloadUrl = null,
        )
        val source = createVideoSource(
            availableQualities = listOf(quality1, quality2),
            selectedQuality = quality1,
        )
        val viewModel = createViewModel(
            contentId = "1",
            contentType = "movie",
            fetchVideoSource = { Result.success(source) },
        )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.availableQualities).hasSize(2)
        assertThat(state.selectedQualityLabel).isEqualTo("1080p")
    }

    @Test
    fun `selectQuality updates the selected quality label in state`() = runTest(testDispatcher) {
        val quality1 = Quality(
            info = QualityDescriptor(
                resolution = Resolution.FULL_HD,
                label = "1080p",
                bitrate = null,
                mimeType = null,
            ),
            url = null,
            downloadUrl = null,
        )
        val quality2 = Quality(
            info = QualityDescriptor(
                resolution = Resolution.HD_720,
                label = "720p",
                bitrate = null,
                mimeType = null,
            ),
            url = null,
            downloadUrl = null,
        )
        val source = createVideoSource(
            availableQualities = listOf(quality1, quality2),
            selectedQuality = quality1,
        )
        val viewModel = createViewModel(
            contentId = "1",
            contentType = "movie",
            fetchVideoSource = { Result.success(source) },
        )

        viewModel.selectQuality("720p")

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.selectedQualityLabel).isEqualTo("720p")
    }

    // ── Playback speed persistence (Fix 3) ─────────────────────────────────

    @Test
    fun `initial playback speed comes from loadPlaybackSpeed`() = runTest(testDispatcher) {
        var loadCalled = false
        val viewModel = createViewModel(
            contentId = "1",
            contentType = "movie",
            fetchVideoSource = { Result.success(createVideoSource()) },
            loadPlaybackSpeed = {
                loadCalled = true
                1.5f
            },
        )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(loadCalled).isTrue()
        assertThat(state.playbackSpeed).isWithin(0.001f).of(1.5f)
    }

    @Test
    fun `loadPlaybackSpeed defaults to 1x when not provided`() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            contentId = "1",
            contentType = "movie",
            fetchVideoSource = { Result.success(createVideoSource()) },
        )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.playbackSpeed).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `selectQuality preserves current position and speed`() = runTest(testDispatcher) {
        val source = createVideoSource(
            availableQualities = listOf(
                createQuality(label = "1080p"),
                createQuality(label = "720p"),
            ),
        )
        val viewModel = createViewModel(
            fetchVideoSource = { Result.success(source) },
            loadPlaybackSpeed = { 1.5f },
        )

        // Set current position (simulated via state update if progress tracking was running)
        // In this test, we don't have a real controller, so we just check what startPlayback sets.
        // But selectQuality takes the position from current state.

        val initialState = viewModel.uiState.value as PlayerUiState.Content
        // Manually update state to simulate some playback progress
        // Actually we can't manually update _uiState from here as it is private.
        // But startPlayback sets it to positionSeconds passed.
        // Initial load calls startPlayback(source, 0)

        viewModel.setPlaybackSpeed(2.0f)
        viewModel.selectQuality("720p")

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.selectedQualityLabel).isEqualTo("720p")
        assertThat(state.playbackSpeed).isWithin(0.001f).of(2.0f)
        assertThat(state.positionSeconds).isEqualTo(0L) // Initial was 0
    }

    @Test
    fun `subtitle selection honors preferred language`() = runTest(testDispatcher) {
        val enSubtitle = createSubtitle()
        val esSubtitle = createSpanishSubtitle()
        val source = createVideoSource(availableSubtitles = listOf(enSubtitle, esSubtitle))

        val viewModel = createViewModel(
            fetchVideoSource = { Result.success(source) },
            loadPreferredLanguage = { LanguageCode("es") },
        )

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.selectedSubtitle?.language?.value).isEqualTo("es")
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun createViewModel(
        contentId: String = "1",
        contentType: String = "movie",
        fetchVideoSource: suspend (Media.MediaId) -> Result<VideoSource> = {
            Result.success(createVideoSource())
        },
        fetchEpisodes: suspend (Media.MediaId.Show) -> Result<List<Episode>> = {
            Result.success(emptyList())
        },
        onAuthFailure: () -> Unit = {},
        saveProgress: suspend (Media.MediaId, Long, Long) -> Unit = { _, _, _ -> },
        refreshStreamUrl: suspend (Media.MediaId) -> Result<VideoSource> = {
            Result.failure(UnsupportedOperationException("Not implemented"))
        },
        savePlaybackSpeed: suspend (Float) -> Unit = {},
        loadPlaybackSpeed: suspend () -> Float = { PlaybackSpeedPolicy.defaultSpeed() },
        loadPreferredLanguage: suspend () -> LanguageCode = { LanguageCode("en") },
    ): PlayerViewModel = PlayerViewModel(
        contentId = contentId,
        contentType = contentType,
        playerController = null,
        fetchVideoSource = fetchVideoSource,
        fetchEpisodes = fetchEpisodes,
        onAuthFailure = onAuthFailure,
        saveProgress = saveProgress,
        refreshStreamUrl = refreshStreamUrl,
        savePlaybackSpeed = savePlaybackSpeed,
        loadPlaybackSpeed = loadPlaybackSpeed,
        loadPreferredLanguage = loadPreferredLanguage,
    )

    private fun createVideoSource(
        mediaId: Media.MediaId = Media.MediaId.Movie(MovieId(1)),
        streamUrl: String = "https://example.com/stream.m3u8",
        availableSubtitles: List<Subtitle> = emptyList(),
        availableQualities: List<Quality> = listOf(createQuality()),
        selectedQuality: Quality = createQuality(),
        durationSeconds: Long = 3600L,
        playbackMode: PlaybackMode = PlaybackMode.ONLINE,
    ): VideoSource = VideoSource(
        mediaId = mediaId,
        streamUrl = streamUrl,
        selectedQuality = selectedQuality,
        availableQualities = availableQualities,
        availableSubtitles = availableSubtitles,
        durationSeconds = durationSeconds,
        playbackMode = playbackMode,
    )

    private fun createQuality(resolution: Resolution = Resolution.FULL_HD, label: String = "1080p"): Quality = Quality(
        info = QualityDescriptor(resolution = resolution, label = label, bitrate = null, mimeType = null),
        url = null,
        downloadUrl = null,
    )

    private fun createSubtitle(): Subtitle = Subtitle(
        language = LanguageCode("en"),
        languageDisplayName = "English",
        url = "https://example.com/sub.srt",
        downloadUrl = null,
        format = SubtitleFormat.SRT,
    )

    private fun createSpanishSubtitle(): Subtitle = Subtitle(
        language = LanguageCode("es"),
        languageDisplayName = "Spanish",
        url = "https://example.com/sub-es.srt",
        downloadUrl = null,
        format = SubtitleFormat.SRT,
    )

    private fun createEpisode(id: Int = 1, showId: Int = 1, seasonNumber: Int = 1, episodeNumber: Int = 1): Episode =
        Episode(
            id = EpisodeId(id),
            showId = ShowId(showId),
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = "Episode $id",
            plot = null,
            durationSeconds = null,
            availability = Availability.Available,
            imdbId = null,
            qualities = emptyList(),
            subtitles = emptyList(),
            airDateEpochSeconds = null,
            premiereDateEpochSeconds = null,
        )
}
