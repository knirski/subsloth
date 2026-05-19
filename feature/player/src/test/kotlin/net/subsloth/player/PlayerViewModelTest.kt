package net.subsloth.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
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
        assertThat(content.error).isNull()
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
        assertThat(state.error).isNotNull()
        assertThat(state.error).contains("Network error")
    }

    @Test
    fun `shows error for invalid content identifier`() = runTest(testDispatcher) {
        val viewModel = createViewModel(contentId = "invalid", contentType = "unknown")

        val state = viewModel.uiState.value as PlayerUiState.Content
        assertThat(state.error).isNotNull()
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun createViewModel(
        contentId: String = "1",
        contentType: String = "movie",
        fetchVideoSource: suspend (Media.MediaId) -> Result<VideoSource> = {
            Result.success(createVideoSource())
        },
        onAuthFailure: () -> Unit = {},
    ): PlayerViewModel = PlayerViewModel(
        contentId = contentId,
        contentType = contentType,
        playerController = null,
        fetchVideoSource = fetchVideoSource,
        onAuthFailure = onAuthFailure,
    )

    private fun createVideoSource(
        mediaId: Media.MediaId = Media.MediaId.Movie(MovieId(1)),
        streamUrl: String = "https://example.com/stream.m3u8",
        availableSubtitles: List<Subtitle> = emptyList(),
        durationSeconds: Long = 3600L,
    ): VideoSource = VideoSource(
        mediaId = mediaId,
        streamUrl = streamUrl,
        selectedQuality = createQuality(),
        availableQualities = listOf(createQuality()),
        availableSubtitles = availableSubtitles,
        durationSeconds = durationSeconds,
    )

    private fun createQuality(): Quality = Quality(
        info = QualityDescriptor(resolution = Resolution.FULL_HD, label = "1080p", bitrate = null, mimeType = null),
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
}
