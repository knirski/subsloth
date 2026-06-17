package net.subsloth.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.policy.PlaybackErrorClassifier
import net.subsloth.core.domain.policy.PlaybackSpeedPolicy
import net.subsloth.core.domain.policy.QualityFallbackPolicy
import net.subsloth.core.domain.policy.StreamRefreshPolicy
import net.subsloth.core.domain.policy.SubtitlePolicy
import net.subsloth.core.media.PlayCommand
import net.subsloth.core.media.PlayerSnapshot
import net.subsloth.core.media.SubtitleMapper
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.fold
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
        val qualityFallbackUsed: Boolean = false,
        val mediaId: Media.MediaId? = null,
        val session: PlayerSession? = null,
        val snapshotCountSinceSave: Int = 0,
    ) : PlayerUiState

    @Immutable
    sealed interface Notice {
        /** Notice is bound to a string resource, with one optional format argument. */
        data class Localized(val resKey: String, val formatArg: String? = null) : Notice

        /** Notice is an already-resolved raw string (no resource lookup). */
        data class Raw(val message: String) : Notice
    }
}

data class PlayerSession(val source: VideoSource, val streamRefreshUsed: Boolean = false)

@Suppress("TooManyFunctions")
class PlayerViewModel(
    val mediaId: Media.MediaId,
    private val fetchVideoSource: suspend (Media.MediaId) -> Outcome<VideoSource> = {
        Outcome.Failure(net.subsloth.core.model.error.DecodeError.SerializationFailed)
    },
    private val fetchEpisodes: suspend (Media.MediaId.Show) -> Result<List<Episode>> = {
        Result.success(emptyList())
    },
    private val saveProgress: suspend (Media.MediaId, Long, Long) -> Unit = { _, _, _ -> },
    private val onAuthFailure: () -> Unit = {},
    private val onNavigateToNextEpisode: (Media.MediaId) -> Unit = {},
    private val refreshStreamUrl: suspend (Media.MediaId) -> Outcome<VideoSource> = {
        Outcome.Failure(net.subsloth.core.model.error.DecodeError.SerializationFailed)
    },
    private val savePlaybackSpeed: suspend (Float) -> Unit = {},
    private val loadPlaybackSpeed: suspend () -> Float = { PlaybackSpeedPolicy.defaultSpeed() },
    private val loadPreferredLanguage: suspend () -> LanguageCode = {
        LanguageCode("en")
    },
    private val resolveShowIdForEpisode: suspend (EpisodeId) -> ShowId? = { null },
) : ViewModel() {
    private val log = Logger.withTag("PlayerViewModel")

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _playCommands = Channel<PlayCommand>(Channel.UNLIMITED)
    val playCommands: Flow<PlayCommand> = _playCommands.receiveAsFlow()

    init {
        loadContent()
    }

    private fun loadContent() {
        log.d { "Loading content for mediaId=$mediaId" }
        viewModelScope.launch {
            fetchVideoSource(mediaId).fold(
                onSuccess = { source -> startPlayback(source) },
                onFailure = { error ->
                    log.e { "Failed to fetch video source: $error" }
                    val playbackError = PlaybackErrorClassifier.classify(error)
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

        val preferred = loadPreferredLanguage()
        val initialSubtitle = SubtitlePolicy.selectDefault(source.availableSubtitles, preferredLanguage = preferred)
        val subtitleTrack = initialSubtitle?.let { SubtitleMapper.toSubtitleTrack(it) }

        _playCommands.send(
            PlayCommand(
                url = source.streamUrl,
                positionSeconds = positionSeconds,
                subtitleTrack = subtitleTrack,
            ),
        )

        val subtitleNotice: PlayerUiState.Notice? = when {
            initialSubtitle == null && source.availableSubtitles.isNotEmpty() ->
                PlayerUiState.Notice.Localized(resKey = "no_subtitles")

            initialSubtitle != null &&
                initialSubtitle.language != preferred &&
                source.availableSubtitles.isNotEmpty() ->
                PlayerUiState.Notice.Localized(
                    resKey = "subtitle_in",
                    formatArg = initialSubtitle.languageDisplayName ?: initialSubtitle.language.value,
                )

            else -> null
        }

        val currentSpeed = (uiState.value as? PlayerUiState.Content)?.playbackSpeed
        val initialSpeed = currentSpeed ?: loadPlaybackSpeed()
        val previous = uiState.value as? PlayerUiState.Content
        val previousRefreshUsed = previous?.session?.streamRefreshUsed == true

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
            qualityFallbackNotice = previous?.qualityFallbackNotice,
            subtitleFallbackNotice = subtitleNotice,
            mediaId = mediaId,
            session = PlayerSession(source = source, streamRefreshUsed = previousRefreshUsed),
            snapshotCountSinceSave = 0,
        )

        populateNextEpisode(source)
    }

    fun onPlayerSnapshot(snapshot: PlayerSnapshot) {
        val dur = snapshot.durationSeconds
        val stateBefore = _uiState.value as? PlayerUiState.Content ?: return
        val mediaId = stateBefore.mediaId

        // The counter increment is computed inside `update` so the new
        // value is based on the latest snapshot, not a possibly-stale
        // pre-update read.
        _uiState.update { current ->
            (current as? PlayerUiState.Content)?.copy(
                positionSeconds = snapshot.positionSeconds,
                durationSeconds = dur,
                isPlaying = snapshot.isPlaying,
                snapshotCountSinceSave = current.snapshotCountSinceSave + 1,
            ) ?: current
        }
        val nextCount = (_uiState.value as? PlayerUiState.Content)?.snapshotCountSinceSave ?: 0

        if (nextCount % 60 == 0 && mediaId != null) {
            viewModelScope.launch { saveProgress(mediaId, snapshot.positionSeconds, dur) }
        }

        if (dur > 0L &&
            CompletionPolicy.isCompleted(snapshot.positionSeconds, dur) &&
            !stateBefore.showNextEpisodePrompt
        ) {
            showNextEpisodePrompt(stateBefore)
        }
    }

    fun onPlayerError(message: String) {
        val playbackError = categorizePlaybackError(message)
        val isAuth = playbackError is PlaybackError.AuthFailure
        if (isAuth) {
            saveProgressAndRouteToAuthRepair()
        }
        _uiState.update { current ->
            (current as? PlayerUiState.Content)?.copy(playbackError = playbackError) ?: current
        }
    }

    fun onPlaybackEnded() {
        val state = _uiState.value as? PlayerUiState.Content ?: return
        showNextEpisodePrompt(state)
    }

    private fun showNextEpisodePrompt(state: PlayerUiState.Content) {
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

    private fun populateNextEpisode(source: VideoSource) {
        viewModelScope.launch {
            val showId = when (val id = mediaId) {
                is Media.MediaId.Show -> id.value
                is Media.MediaId.Episode -> resolveShowIdForEpisode(id.value)
                else -> return@launch
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

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { current ->
            (current as? PlayerUiState.Content)?.copy(playbackSpeed = speed) ?: current
        }
        val state = _uiState.value as? PlayerUiState.Content ?: return
        if (state.playbackMode == PlaybackMode.ONLINE) {
            viewModelScope.launch { savePlaybackSpeed(speed) }
        }
    }

    fun selectSubtitle(subtitle: Subtitle?) {
        _uiState.update { current ->
            (current as? PlayerUiState.Content)?.copy(selectedSubtitle = subtitle) ?: current
        }
    }

    fun selectQuality(qualityLabel: String) {
        val state = _uiState.value as? PlayerUiState.Content ?: return
        val currentSession = state.session ?: return
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

    fun retryWithRefresh() {
        val state = _uiState.value as? PlayerUiState.Content ?: return
        val currentSession = state.session ?: return
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
                    _uiState.update { current ->
                        (current as? PlayerUiState.Content)
                            ?.copy(session = currentSession.copy(streamRefreshUsed = true)) ?: current
                    }
                },
                onFailure = { error ->
                    log.e { "Stream refresh failed: $error" }
                    val playbackError = PlaybackErrorClassifier.classify(error)
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
        log.d { "ViewModel cleared" }
        super.onCleared()
    }

    private fun saveProgressAndRouteToAuthRepair() {
        val state = _uiState.value as? PlayerUiState.Content
        if (state != null) {
            viewModelScope.launch {
                withContext(NonCancellable) {
                    saveProgress(state.mediaId ?: return@withContext, state.positionSeconds, state.durationSeconds)
                }
            }
        }
        onAuthFailure()
    }

    private fun categorizePlaybackError(message: String): PlaybackError {
        // The player bridge reports errors as opaque strings. Parse the
        // HTTP status code if present so the typed classifier can
        // dispatch 401 to AuthFailure and 403 to StreamUrlExpired.
        val code = HTTP_STATUS_REGEX.find(message)?.value?.toIntOrNull()
        val domainError = if (code != null && code in 400..599) {
            net.subsloth.core.model.error.NetworkError.HttpError(code, message)
        } else {
            net.subsloth.core.model.error.DecodeError.SerializationFailed
        }
        return PlaybackErrorClassifier.classify(domainError)
    }

    private companion object {
        // Matches "401", "403", etc. inside an arbitrary player error
        // message. The player bridge is not coupled to the network
        // shell so we recover the status code from the message.
        val HTTP_STATUS_REGEX = Regex("""\b(40[0-9]|41[0-9]|42[0-9]|43[0-9]|44[0-9]|45[0-9])\b""")
    }
}
