package net.subsloth.player

import android.annotation.SuppressLint
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.policy.PlaybackSpeedPolicy
import net.subsloth.core.domain.policy.QualityFallbackPolicy
import net.subsloth.core.domain.policy.StreamRefreshPolicy
import net.subsloth.core.media.MediaPlaybackController
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.playback.PlaybackError
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Stable
sealed interface PlayerUiState {
    data object Loading : PlayerUiState

    @Immutable
    data class Content(
        val title: String,
        val positionSeconds: Long,
        val durationSeconds: Long,
        val isPlaying: Boolean,
        val playbackSpeed: Float,
        val selectedSubtitle: Subtitle?,
        val availableSubtitles: List<Subtitle>,
        val nextEpisode: Episode?,
        val showNextEpisodePrompt: Boolean,
        val error: String?,
        val authFailed: Boolean,
        /** Whether this is an offline (local file) playback session. */
        val isOfflinePlayback: Boolean,
        /** Whether a quality fallback has been applied in this session. */
        val qualityFallbackNotice: String?,
    ) : PlayerUiState
}

@SuppressLint("UnsafeOptInUsageError")
@Suppress("TooManyFunctions")
class PlayerViewModel(
    private val contentId: String,
    private val contentType: String,
    private val playerController: MediaPlaybackController? = null,
    private val fetchVideoSource: suspend (Media.MediaId) -> Result<VideoSource> = {
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
    private val fetchEpisodes: suspend (Media.MediaId.Show) -> Result<List<Episode>> = {
        Result.success(emptyList())
    },
    private val saveProgress: suspend (Media.MediaId, Long, Long) -> Unit = { _, _, _ -> },
    private val onAuthFailure: () -> Unit = {},
    private val onNavigateToNextEpisode: (Media.MediaId) -> Unit = {},
    private val refreshStreamUrl: suspend (Media.MediaId) -> Result<VideoSource> = {
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentMediaId: Media.MediaId? = null
    private var currentSource: VideoSource? = null
    private var progressJob: Job? = null
    private var countdownJob: Job? = null

    /** Whether a stream URL refresh has been used in this session. */
    private var streamRefreshUsed = false

    /** Whether a quality fallback has been used in this session. */
    private var qualityFallbackUsed = false

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            currentMediaId = parseMediaId(contentId, contentType)
            val mediaId = currentMediaId
            if (mediaId == null) {
                _uiState.value = PlayerUiState.Content(
                    title = "", positionSeconds = 0, durationSeconds = 0, isPlaying = false,
                    playbackSpeed = PlaybackSpeedPolicy.defaultSpeed(),
                    selectedSubtitle = null, availableSubtitles = emptyList(),
                    nextEpisode = null, showNextEpisodePrompt = false,
                    error = "Invalid content identifier", authFailed = false,
                    isOfflinePlayback = false, qualityFallbackNotice = null,
                )
                return@launch
            }

            fetchVideoSource(mediaId).fold(
                onSuccess = { source -> startPlayback(source) },
                onFailure = { error ->
                    val playbackError = categorizeError(error)
                    val isAuth = playbackError is PlaybackError.AuthFailure
                    if (isAuth) {
                        saveProgressAndRouteToAuthRepair()
                    }
                    _uiState.value = PlayerUiState.Content(
                        title = "", positionSeconds = 0, durationSeconds = 0, isPlaying = false,
                        playbackSpeed = PlaybackSpeedPolicy.defaultSpeed(),
                        selectedSubtitle = null, availableSubtitles = emptyList(),
                        nextEpisode = null, showNextEpisodePrompt = false,
                        error = error.message ?: "Failed to load content",
                        authFailed = isAuth,
                        isOfflinePlayback = false,
                        qualityFallbackNotice = null,
                    )
                },
            )
        }
    }

    private suspend fun startPlayback(source: VideoSource) {
        currentSource = source
        streamRefreshUsed = false
        qualityFallbackUsed = false

        playerController?.let { controller ->
            if (source.playbackMode == PlaybackMode.OFFLINE) {
                val exoPlayer = controller.buildLocalPlayer()
                controller.startLocalPlayback(
                    localFileUri = source.streamUrl,
                    source = source,
                )
                controller.setErrorCallback { error ->
                    handlePlayerError(error)
                }
            } else {
                val exoPlayer = controller.buildPlayer()
                controller.startPlayback(source)
                controller.setErrorCallback { error ->
                    handlePlayerError(error)
                }
            }
        }

        _uiState.value = PlayerUiState.Content(
            title = source.mediaId.toString(),
            positionSeconds = 0,
            durationSeconds = source.durationSeconds,
            isPlaying = true,
            playbackSpeed = PlaybackSpeedPolicy.defaultSpeed(),
            selectedSubtitle = null,
            availableSubtitles = source.availableSubtitles,
            nextEpisode = null,
            showNextEpisodePrompt = false,
            error = null,
            authFailed = false,
            isOfflinePlayback = source.playbackMode == PlaybackMode.OFFLINE,
            qualityFallbackNotice = null,
        )

        if (playerController != null) {
            startProgressTracking()
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value as? PlayerUiState.Content ?: return
        playerController?.let {
            if (state.isPlaying) it.pause() else it.playWhenReady()
        }
        updatePlayingState()
    }

    fun seekTo(positionSeconds: Long) {
        playerController?.seekTo(positionSeconds.seconds)
        updatePlayingState()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController?.setPlaybackSpeed(speed)
        val state = _uiState.value as? PlayerUiState.Content ?: return
        _uiState.value = state.copy(playbackSpeed = speed)
    }

    fun selectSubtitle(subtitle: Subtitle?) {
        playerController?.setPreferredTextLanguage(subtitle?.language?.value)
        val state = _uiState.value as? PlayerUiState.Content ?: return
        _uiState.value = state.copy(selectedSubtitle = subtitle)
    }

    @Suppress("UnusedParameter")
    fun selectQuality(qualityLabel: String) {
        updatePlayingState()
    }

    fun dismissNextEpisode() {
        countdownJob?.cancel()
        val state = _uiState.value as? PlayerUiState.Content ?: return
        _uiState.value = state.copy(showNextEpisodePrompt = false)
    }

    fun playNextEpisode() {
        val state = _uiState.value as? PlayerUiState.Content ?: return
        val nextEp = state.nextEpisode ?: return
        dismissNextEpisode()
        onNavigateToNextEpisode(Media.MediaId.Episode(nextEp.id))
    }

    fun retryPlayback() {
        loadContent()
    }

    /**
     * Attempts a bounded stream URL refresh for the current media item.
     *
     * At most one refresh is allowed per playback session. Offline playback
     * never triggers a refresh. On success, playback restarts with the
     * refreshed source. On failure, a recoverable error is shown.
     */
    @Suppress("ReturnCount")
    fun retryWithRefresh() {
        val mediaId = currentMediaId ?: return
        val state = _uiState.value as? PlayerUiState.Content ?: return
        if (state.isOfflinePlayback) return
        if (!StreamRefreshPolicy.canRefresh(streamRefreshUsed, isOfflinePlayback = false)) return

        performStreamRefresh(mediaId, state)
    }

    private fun performStreamRefresh(mediaId: Media.MediaId, state: PlayerUiState.Content) {
        viewModelScope.launch {
            refreshStreamUrl(mediaId).fold(
                onSuccess = { refreshedSource ->
                    streamRefreshUsed = StreamRefreshPolicy.markRefreshUsed(streamRefreshUsed)
                    startPlayback(refreshedSource)
                },
                onFailure = { error ->
                    val isAuth = isAuthError(error)
                    if (isAuth) {
                        saveProgressAndRouteToAuthRepair()
                    }
                    _uiState.value = state.copy(
                        error = error.message ?: "Stream refresh failed",
                        authFailed = isAuth,
                    )
                },
            )
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        countdownJob?.cancel()
        super.onCleared()
        playerController?.release()
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_UPDATE_INTERVAL)
                val pos = playerController?.currentPosition()?.inWholeSeconds ?: 0L
                val dur = playerController?.duration()?.inWholeSeconds ?: 0L
                val state = _uiState.value as? PlayerUiState.Content ?: continue
                _uiState.value = state.copy(
                    positionSeconds = pos,
                    durationSeconds = dur,
                    isPlaying = playerController?.isPlaying() ?: false,
                )

                currentMediaId?.let { id ->
                    saveProgress(id, pos, dur)
                }

                if (dur > 0L && CompletionPolicy.isCompleted(pos, dur) && !state.showNextEpisodePrompt) {
                    onEpisodeCompleted(state)
                }
            }
        }
    }

    private fun onEpisodeCompleted(state: PlayerUiState.Content) {
        currentMediaId?.let { id ->
            viewModelScope.launch { saveProgress(id, state.positionSeconds, state.durationSeconds) }
        }
        val nextEp = state.nextEpisode
        if (nextEp != null) {
            countdownJob?.cancel()
            countdownJob = viewModelScope.launch {
                _uiState.value = state.copy(showNextEpisodePrompt = true)
                delay(NEXT_EPISODE_COUNTDOWN)
                if (_uiState.value is PlayerUiState.Content) {
                    playNextEpisode()
                }
            }
        }
    }

    private fun updatePlayingState() {
        val state = _uiState.value as? PlayerUiState.Content ?: return
        _uiState.value = state.copy(isPlaying = playerController?.isPlaying() ?: false)
    }

    /**
     * Handles player errors from ExoPlayer.
     *
     * - Auth errors during online playback: save progress and route to auth repair.
     * - Auth errors during offline playback: do not interrupt playback.
     * - Stream URL expiry: attempt bounded refresh if available.
     * - Quality errors: attempt bounded quality fallback if available.
     * - Other errors: show recoverable error with retry option.
     */
    private fun handlePlayerError(error: PlaybackException) {
        val state = _uiState.value as? PlayerUiState.Content ?: return

        // Offline playback is not interrupted by auth failures elsewhere
        if (state.isOfflinePlayback) return

        val playbackError = categorizePlayerError(error)

        when (playbackError) {
            is PlaybackError.AuthFailure -> {
                saveProgressAndRouteToAuthRepair()
                _uiState.value = state.copy(
                    error = playbackError.message,
                    authFailed = true,
                )
            }
            is PlaybackError.StreamUrlExpired -> {
                // Attempt bounded refresh
                if (StreamRefreshPolicy.canRefresh(streamRefreshUsed, isOfflinePlayback = false)) {
                    retryWithRefresh()
                } else {
                    _uiState.value = state.copy(
                        error = playbackError.message,
                        authFailed = false,
                    )
                }
            }
            is PlaybackError.Recoverable -> {
                // Attempt quality fallback if available
                val source = currentSource
                if (source != null && QualityFallbackPolicy.canFallback(qualityFallbackUsed)) {
                    val fallback = QualityFallbackPolicy.selectFallback(
                        availableQualities = source.availableQualities,
                        currentResolution = source.selectedQuality.info.resolution,
                        fallbackUsed = qualityFallbackUsed,
                    )
                    if (fallback != null) {
                        qualityFallbackUsed = QualityFallbackPolicy.markFallbackUsed(qualityFallbackUsed)
                        _uiState.value = state.copy(
                            qualityFallbackNotice = "Quality reduced to " +
                                (fallback.info.label ?: fallback.info.resolution.label),
                        )
                        // Restart playback with fallback quality
                        viewModelScope.launch {
                            val fallbackSource = source.copy(selectedQuality = fallback)
                            startPlayback(fallbackSource)
                        }
                    } else {
                        _uiState.value = state.copy(
                            error = playbackError.message,
                            authFailed = false,
                        )
                    }
                } else {
                    _uiState.value = state.copy(
                        error = playbackError.message,
                        authFailed = false,
                    )
                }
            }
        }
    }

    /**
     * Saves current progress and routes to auth repair.
     *
     * Only called for online playback auth failures. Offline playback is
     * never interrupted by auth failures.
     */
    private fun saveProgressAndRouteToAuthRepair() {
        val mediaId = currentMediaId
        val state = _uiState.value as? PlayerUiState.Content
        if (mediaId != null && state != null) {
            viewModelScope.launch { saveProgress(mediaId, state.positionSeconds, state.durationSeconds) }
        }
        onAuthFailure()
    }

    private fun parseMediaId(contentId: String, contentType: String): Media.MediaId? = when (contentType) {
        CONTENT_TYPE_MOVIE -> contentId.toLongOrNull()?.let { Media.MediaId.Movie(MovieId(it.toInt())) }
        CONTENT_TYPE_EPISODE -> contentId.toLongOrNull()?.let { Media.MediaId.Episode(EpisodeId(it.toInt())) }
        CONTENT_TYPE_SHOW -> contentId.toLongOrNull()?.let { Media.MediaId.Show(ShowId(it.toInt())) }
        else -> null
    }

    private fun categorizeError(error: Throwable): PlaybackError = when {
        isAuthError(error) -> PlaybackError.AuthFailure(error.message ?: "Authentication failed")
        isStreamUrlError(error) -> PlaybackError.StreamUrlExpired(error.message ?: "Stream URL expired")
        else -> PlaybackError.Recoverable(error.message ?: "Playback error")
    }

    private fun categorizePlayerError(error: PlaybackException): PlaybackError {
        val message = error.message ?: "Playback error"
        return when {
            isAuthPlayerError(error) -> PlaybackError.AuthFailure(message)
            isStreamUrlPlayerError(error) -> PlaybackError.StreamUrlExpired(message)
            else -> PlaybackError.Recoverable(message)
        }
    }

    private fun isAuthError(error: Throwable): Boolean =
        error.message?.contains("401") == true || error.message?.contains("auth", ignoreCase = true) == true

    private fun isStreamUrlError(error: Throwable): Boolean =
        error.message?.contains("403") == true || error.message?.contains("expired", ignoreCase = true) == true

    private fun isAuthPlayerError(error: PlaybackException): Boolean =
        error.message?.contains("401") == true || error.message?.contains("auth", ignoreCase = true) == true

    private fun isStreamUrlPlayerError(error: PlaybackException): Boolean =
        error.message?.contains("403") == true || error.message?.contains("expired", ignoreCase = true) == true

    private companion object {
        private const val CONTENT_TYPE_MOVIE = "movie"
        private const val CONTENT_TYPE_EPISODE = "episode"
        private const val CONTENT_TYPE_SHOW = "show"
        private val PROGRESS_UPDATE_INTERVAL: Duration = 1.seconds
        private val NEXT_EPISODE_COUNTDOWN: Duration = 10.seconds
    }
}
