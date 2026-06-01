package net.subsloth.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.policy.PlaybackSpeedPolicy
import net.subsloth.core.domain.policy.QualityFallbackPolicy
import net.subsloth.core.domain.policy.StreamRefreshPolicy
import net.subsloth.core.domain.policy.SubtitlePolicy
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
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
        val availableSubtitles: ImmutableList<Subtitle>,
        val availableQualities: ImmutableList<Quality>,
        val selectedQualityLabel: String?,
        val nextEpisode: Episode?,
        val showNextEpisodePrompt: Boolean,
        val playbackError: PlaybackError?,
        val playbackMode: PlaybackMode,
        val qualityFallbackNotice: Notice?,
        val subtitleFallbackNotice: Notice?,
        val streamRefreshUsed: Boolean = false,
        val qualityFallbackUsed: Boolean = false,
        val mediaId: Media.MediaId? = null,
    ) : PlayerUiState

    @Immutable
    data class Notice(val message: String = "", val resKey: String? = null, val formatArg: String? = null)
}

private data class PlayerSession(val source: VideoSource, val streamRefreshUsed: Boolean = false)

@Suppress("TooManyFunctions")
class PlayerViewModel(
    val mediaId: Media.MediaId,
    private val playerController: PlayerController? = null,
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
    private val savePlaybackSpeed: suspend (Float) -> Unit = {},
    private val loadPlaybackSpeed: suspend () -> Float = { PlaybackSpeedPolicy.defaultSpeed() },
    private val loadPreferredLanguage: suspend () -> LanguageCode = {
        LanguageCode("en")
    },
    private val resolveShowIdForEpisode: suspend (EpisodeId) -> ShowId? = { null },
    private val stopService: () -> Unit = {},
) : ViewModel() {
    private val log = Logger.withTag("PlayerViewModel")

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var session: PlayerSession? = null
    private var progressJob: Job? = null

    init {
        loadContent()
    }

    private fun loadContent() {
        log.d { "Loading content for mediaId=$mediaId" }
        viewModelScope.launch {
            fetchVideoSource(mediaId).fold(
                onSuccess = { source -> startPlayback(source) },
                onFailure = { error ->
                    log.e(error) { "Failed to fetch video source" }
                    val playbackError = categorizePlaybackError(error)
                    val isAuth = playbackError is PlaybackError.AuthFailure
                    if (isAuth) {
                        saveProgressAndRouteToAuthRepair()
                    }
                    _uiState.value = PlayerUiState.Content(
                        title = "", positionSeconds = 0, durationSeconds = 0, isPlaying = false,
                        playbackSpeed = PlaybackSpeedPolicy.defaultSpeed(),
                        selectedSubtitle = null, availableSubtitles = persistentListOf(),
                        availableQualities = persistentListOf(), selectedQualityLabel = null,
                        nextEpisode = null, showNextEpisodePrompt = false,
                        playbackError = playbackError,
                        playbackMode = PlaybackMode.ONLINE,
                        qualityFallbackNotice = null,
                        subtitleFallbackNotice = null,
                        mediaId = mediaId,
                    )
                },
            )
        }
    }

    private suspend fun startPlayback(source: VideoSource, positionSeconds: Long = 0L) {
        log.d {
            "Starting playback: mode=${source.playbackMode}, pos=${positionSeconds}s, url=${source.streamUrl.take(80)}"
        }
        session = PlayerSession(source = source)

        playerController?.let { controller ->
            if (source.playbackMode == PlaybackMode.OFFLINE) {
                controller.buildLocalPlayer()
                controller.startLocalPlayback(
                    localFileUri = source.streamUrl,
                    source = source,
                    positionSeconds = positionSeconds,
                )
                controller.setErrorCallback { error ->
                    handlePlayerError(error)
                }
            } else {
                controller.buildPlayer()
                controller.startPlayback(source, positionSeconds = positionSeconds)
                controller.setErrorCallback { error ->
                    handlePlayerError(error)
                }
            }
        }

        val preferred = loadPreferredLanguage()
        val initialSubtitle = SubtitlePolicy.selectDefault(source.availableSubtitles, preferredLanguage = preferred)
        val subtitleNotice: PlayerUiState.Notice? = when {
            initialSubtitle == null && source.availableSubtitles.isNotEmpty() ->
                PlayerUiState.Notice(resKey = "no_subtitles")

            initialSubtitle != null &&
                initialSubtitle.language != preferred &&
                source.availableSubtitles.isNotEmpty() ->
                PlayerUiState.Notice(
                    resKey = "subtitle_in",
                    formatArg = initialSubtitle.languageDisplayName ?: initialSubtitle.language.value,
                )

            else -> null
        }
        if (initialSubtitle != null) {
            playerController?.setPreferredTextLanguage(initialSubtitle.language.value)
        }

        val currentSpeed = (uiState.value as? PlayerUiState.Content)?.playbackSpeed
        val initialSpeed = currentSpeed ?: loadPlaybackSpeed()
        playerController?.setPlaybackSpeed(initialSpeed)

        _uiState.value = PlayerUiState.Content(
            title = source.mediaId.toString(),
            positionSeconds = positionSeconds,
            durationSeconds = source.durationSeconds,
            isPlaying = true,
            playbackSpeed = initialSpeed,
            selectedSubtitle = initialSubtitle,
            availableSubtitles = source.availableSubtitles.toImmutableList(),
            availableQualities = source.availableQualities.toImmutableList(),
            selectedQualityLabel = source.selectedQuality.info.label
                ?: source.selectedQuality.info.resolution.label,
            nextEpisode = null,
            showNextEpisodePrompt = false,
            playbackError = null,
            playbackMode = source.playbackMode,
            qualityFallbackNotice = (uiState.value as? PlayerUiState.Content)?.qualityFallbackNotice,
            subtitleFallbackNotice = subtitleNotice,
            mediaId = mediaId,
        )

        if (playerController != null) {
            startProgressTracking()
        }

        populateNextEpisode(source)
    }

    private fun populateNextEpisode(source: VideoSource) {
        viewModelScope.launch {
            val showId = when (val id = mediaId) {
                is Media.MediaId.Show -> id.value
                is Media.MediaId.Episode -> resolveShowIdForEpisode(id.value)
                else -> null
            } ?: return@launch
            fetchEpisodes(Media.MediaId.Show(showId)).onSuccess { episodes ->
                val currentEpisodeId = (source.mediaId as? Media.MediaId.Episode)?.value
                if (currentEpisodeId != null) {
                    val sorted = episodes.sortedWith(
                        compareBy({ it.seasonNumber }, { it.episodeNumber }),
                    )
                    val currentIndex = sorted.indexOfFirst { it.id == currentEpisodeId }
                    if (currentIndex >= 0 && currentIndex < sorted.size - 1) {
                        val next = sorted[currentIndex + 1]
                        _uiState.update { current ->
                            (current as? PlayerUiState.Content)?.copy(nextEpisode = next) ?: current
                        }
                    }
                }
            }
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
        _uiState.update { current ->
            (current as? PlayerUiState.Content)?.copy(playbackSpeed = speed) ?: current
        }
        val state = _uiState.value as? PlayerUiState.Content ?: return
        if (state.playbackMode == PlaybackMode.ONLINE) {
            viewModelScope.launch { savePlaybackSpeed(speed) }
        }
    }

    fun selectSubtitle(subtitle: Subtitle?) {
        playerController?.setPreferredTextLanguage(subtitle?.language?.value)
        _uiState.update { current ->
            (current as? PlayerUiState.Content)?.copy(selectedSubtitle = subtitle) ?: current
        }
    }

    fun selectQuality(qualityLabel: String) {
        val state = _uiState.value as? PlayerUiState.Content ?: return
        val currentSession = session ?: return
        val quality = currentSession.source.availableQualities.find {
            it.info.label == qualityLabel || it.info.resolution.label == qualityLabel
        } ?: run {
            log.w { "Quality not found: $qualityLabel" }
            return
        }
        log.d { "Switching quality to: ${quality.info.label}" }
        val updatedSource = currentSession.source.copy(selectedQuality = quality)
        viewModelScope.launch {
            startPlayback(updatedSource, positionSeconds = state.positionSeconds)
        }
    }

    fun dismissNextEpisode() {
        _uiState.update { current ->
            (current as? PlayerUiState.Content)?.copy(showNextEpisodePrompt = false) ?: current
        }
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

    @Suppress("ReturnCount")
    fun retryWithRefresh() {
        val state = _uiState.value as? PlayerUiState.Content ?: return
        val currentSession = session ?: return
        if (state.playbackMode == PlaybackMode.OFFLINE) return
        if (!StreamRefreshPolicy.canRefresh(currentSession.streamRefreshUsed, isOfflinePlayback = false)) return

        performStreamRefresh(currentSession)
    }

    private fun performStreamRefresh(currentSession: PlayerSession) {
        log.d { "Performing stream refresh" }
        viewModelScope.launch {
            refreshStreamUrl(currentSession.source.mediaId).fold(
                onSuccess = { refreshedSource ->
                    startPlayback(
                        refreshedSource,
                        positionSeconds =
                        (_uiState.value as? PlayerUiState.Content)?.positionSeconds ?: 0L,
                    )
                    session = currentSession.copy(streamRefreshUsed = true)
                    _uiState.update { current ->
                        (current as? PlayerUiState.Content)?.copy(streamRefreshUsed = true) ?: current
                    }
                },
                onFailure = { error ->
                    val playbackError = categorizePlaybackError(error)
                    val isAuth = playbackError is PlaybackError.AuthFailure
                    if (isAuth) {
                        saveProgressAndRouteToAuthRepair()
                    }
                    _uiState.update { current ->
                        (current as? PlayerUiState.Content)?.copy(playbackError = playbackError) ?: current
                    }
                },
            )
        }
    }

    override fun onCleared() {
        log.d { "ViewModel cleared, releasing player" }
        progressJob?.cancel()
        super.onCleared()
        playerController?.release()
        stopService()
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_UPDATE_INTERVAL)
                val pos = playerController?.currentPosition()?.inWholeSeconds ?: 0L
                val dur = playerController?.duration()?.inWholeSeconds ?: 0L
                val playing = playerController?.isPlaying() ?: false
                _uiState.update { current ->
                    (current as? PlayerUiState.Content)?.copy(
                        positionSeconds = pos,
                        durationSeconds = dur,
                        isPlaying = playing,
                    ) ?: current
                }
                val state = _uiState.value as? PlayerUiState.Content ?: continue

                state.mediaId?.let { id ->
                    saveProgress(id, pos, dur)
                }

                if (dur > 0L && CompletionPolicy.isCompleted(pos, dur) && !state.showNextEpisodePrompt) {
                    onEpisodeCompleted(state)
                }
            }
        }
    }

    private fun onEpisodeCompleted(state: PlayerUiState.Content) {
        state.mediaId?.let { id ->
            viewModelScope.launch { saveProgress(id, state.positionSeconds, state.durationSeconds) }
        }
        val nextEp = state.nextEpisode
        if (nextEp != null) {
            _uiState.update { current ->
                (current as? PlayerUiState.Content)?.copy(showNextEpisodePrompt = true) ?: current
            }
        }
    }

    private fun updatePlayingState() {
        val playing = playerController?.isPlaying() ?: false
        _uiState.update { current ->
            (current as? PlayerUiState.Content)?.copy(
                isPlaying = playing,
            ) ?: current
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handlePlayerError(error: Throwable) {
        val state = _uiState.value as? PlayerUiState.Content ?: return

        val playbackError = categorizePlaybackError(error)
        log.e(error) { "Player error: $playbackError (mode=${state.playbackMode})" }

        if (state.playbackMode == PlaybackMode.OFFLINE && playbackError is PlaybackError.AuthFailure) return

        when (playbackError) {
            is PlaybackError.AuthFailure -> {
                saveProgressAndRouteToAuthRepair()
                _uiState.update { current ->
                    (current as? PlayerUiState.Content)?.copy(playbackError = playbackError) ?: current
                }
            }

            is PlaybackError.StreamUrlExpired -> {
                val currentSession = session
                if (currentSession != null &&
                    StreamRefreshPolicy.canRefresh(
                        currentSession.streamRefreshUsed,
                        state.playbackMode == PlaybackMode.OFFLINE,
                    )
                ) {
                    retryWithRefresh()
                } else {
                    _uiState.update { current ->
                        (current as? PlayerUiState.Content)?.copy(playbackError = playbackError) ?: current
                    }
                }
            }

            is PlaybackError.Recoverable -> {
                val currentSession = session
                if (currentSession != null && QualityFallbackPolicy.canFallback(state.qualityFallbackUsed)) {
                    val fallback = QualityFallbackPolicy.selectFallback(
                        availableQualities = currentSession.source.availableQualities,
                        currentResolution = currentSession.source.selectedQuality.info.resolution,
                        fallbackUsed = state.qualityFallbackUsed,
                    )
                    if (fallback != null) {
                        viewModelScope.launch {
                            val fallbackSource = currentSession.source.copy(selectedQuality = fallback)
                            startPlayback(fallbackSource, positionSeconds = state.positionSeconds)

                            _uiState.update { current ->
                                (current as? PlayerUiState.Content)?.copy(
                                    qualityFallbackUsed = true,
                                    qualityFallbackNotice = PlayerUiState.Notice(
                                        resKey = "quality_reduced",
                                        formatArg = fallback.info.label ?: fallback.info.resolution.label,
                                    ),
                                ) ?: current
                            }
                        }
                    } else {
                        _uiState.update { current ->
                            (current as? PlayerUiState.Content)?.copy(playbackError = playbackError) ?: current
                        }
                    }
                } else {
                    _uiState.update { current ->
                        (current as? PlayerUiState.Content)?.copy(playbackError = playbackError) ?: current
                    }
                }
            }
        }
    }

    private fun saveProgressAndRouteToAuthRepair() {
        val state = _uiState.value as? PlayerUiState.Content
        if (state != null) {
            viewModelScope.launch(NonCancellable) {
                saveProgress(state.mediaId ?: return@launch, state.positionSeconds, state.durationSeconds)
            }
        }
        onAuthFailure()
    }

    private fun categorizePlaybackError(error: Throwable): PlaybackError {
        val message = error.message.orEmpty()

        return when {
            isLikelyAuthError(message) -> PlaybackError.AuthFailure
            isLikelyStreamExpired(message) -> PlaybackError.StreamUrlExpired
            else -> PlaybackError.Recoverable()
        }
    }

    private fun isLikelyAuthError(message: String): Boolean =
        message.contains("401") || message.contains("auth", ignoreCase = true)

    private fun isLikelyStreamExpired(message: String): Boolean =
        message.contains("403") || message.contains("expired", ignoreCase = true)

    private companion object {
        private val PROGRESS_UPDATE_INTERVAL: Duration = 1.seconds
    }
}
