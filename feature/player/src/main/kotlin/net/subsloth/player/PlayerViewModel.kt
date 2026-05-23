package net.subsloth.player

import android.annotation.SuppressLint
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.policy.PlaybackSpeedPolicy
import net.subsloth.core.media.MediaItemFactory
import net.subsloth.core.media.MediaPlaybackController
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Subtitle
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentMediaId: Media.MediaId? = null
    private var progressJob: Job? = null
    private var countdownJob: Job? = null

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
                )
                return@launch
            }

            fetchVideoSource(mediaId).fold(
                onSuccess = { source -> startPlayback(source) },
                onFailure = { error ->
                    val isAuth = isAuthError(error)
                    if (isAuth) {
                        onAuthFailure()
                    }
                    _uiState.value = PlayerUiState.Content(
                        title = "", positionSeconds = 0, durationSeconds = 0, isPlaying = false,
                        playbackSpeed = PlaybackSpeedPolicy.defaultSpeed(),
                        selectedSubtitle = null, availableSubtitles = emptyList(),
                        nextEpisode = null, showNextEpisodePrompt = false,
                        error = error.message ?: "Failed to load content",
                        authFailed = isAuth,
                    )
                },
            )
        }
    }

    private suspend fun startPlayback(source: VideoSource) {
        playerController?.let { controller ->
            val exoPlayer = controller.buildPlayer()
            val mediaItem = MediaItemFactory.createMediaItem(source)
                .buildUpon()
                .setSubtitleConfigurations(MediaItemFactory.buildSubtitleMediaItem(source))
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
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

    private fun parseMediaId(contentId: String, contentType: String): Media.MediaId? = when (contentType) {
        CONTENT_TYPE_MOVIE -> contentId.toLongOrNull()?.let { Media.MediaId.Movie(MovieId(it.toInt())) }
        CONTENT_TYPE_EPISODE -> contentId.toLongOrNull()?.let { Media.MediaId.Episode(EpisodeId(it.toInt())) }
        CONTENT_TYPE_SHOW -> contentId.toLongOrNull()?.let { Media.MediaId.Show(ShowId(it.toInt())) }
        else -> null
    }

    private fun isAuthError(error: Throwable): Boolean =
        error.message?.contains("401") == true || error.message?.contains("auth", ignoreCase = true) == true

    private companion object {
        private const val CONTENT_TYPE_MOVIE = "movie"
        private const val CONTENT_TYPE_EPISODE = "episode"
        private const val CONTENT_TYPE_SHOW = "show"
        private val PROGRESS_UPDATE_INTERVAL: Duration = 1.seconds
        private val NEXT_EPISODE_COUNTDOWN: Duration = 10.seconds
    }
}
