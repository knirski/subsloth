package net.subsloth.details

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.subsloth.core.domain.policy.ResumePolicy
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.UiError
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.ui.error.toUiError
import kotlin.time.Clock
@Stable
sealed interface ShowDetailUiState {
    data object Loading : ShowDetailUiState

    @Immutable
    data class Content(
        val details: ShowDetails,
        val selectedSeason: Int,
        val isFavorite: Boolean = false,
        val isWatchLater: Boolean = false,
        val progressFraction: Double? = null,
        val watchedEpisodeIds: ImmutableList<Int> = persistentListOf(),
        /** Resumable playback fraction per episode id, for per-episode "Resume N%" hints. */
        val episodeProgress: ImmutableMap<Int, Double> = persistentMapOf(),
        val seasonQueues: ImmutableList<SeasonDownloadQueue> = persistentListOf(),
    ) : ShowDetailUiState

    @Immutable
    data class Error(val error: UiError) : ShowDetailUiState
}

class ShowDetailViewModel(
    private val mediaId: Media.MediaId.Show,
    private val getDetails: suspend (Media.MediaId) -> Outcome<MediaDetails> = {
        Outcome.Failure(DecodeError.SerializationFailed)
    },
    private val listLibrary: suspend () -> Outcome<List<LibraryItem>> = {
        Outcome.Success(emptyList())
    },
    private val listProgress: suspend () -> Result<List<PlaybackProgress>> = {
        Result.success(emptyList())
    },
    private val listWatchedIds: suspend () -> Set<String> = { emptySet() },
    private val addToLibrary: suspend (LibraryItem) -> Outcome<Unit> = {
        Outcome.Success(Unit)
    },
    private val removeFromLibrary: suspend (Media.MediaId) -> Outcome<Unit> = {
        Outcome.Success(Unit)
    },
    private val listSeasonQueues: suspend () -> Result<List<SeasonDownloadQueue>> = {
        Result.success(emptyList())
    },
    private val startSeasonDownload: suspend (Int, ImmutableList<Episode>) -> Unit = { _, _ -> },
    private val clock: Clock = Clock.System,
    private val savedState: Map<String, String> = mapOf("selectedSeason" to ""),
) : ViewModel() {
    private val log = Logger.withTag("ShowDetailViewModel")

    private val _uiState = MutableStateFlow<ShowDetailUiState>(ShowDetailUiState.Loading)
    val uiState: StateFlow<ShowDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = ShowDetailUiState.Loading
            when (val detailsResult = getDetails(mediaId)) {
                is Outcome.Success -> {
                    val details = detailsResult.value
                    if (details is ShowDetails) {
                        val watchedIds = runCatching { listWatchedIds() }.getOrElse { error ->
                            log.w { "Failed to load watched ids: $error" }
                            emptySet()
                        }
                        val flags = loadFlags()
                        val progressRows = loadProgressRows()
                        val progress = resumableShowProgress(details, progressRows)
                        val seasonQueues = loadSeasonQueues()
                        val restoredSeason = parseSeason(savedState["selectedSeason"].orEmpty(), details.seasons)
                        _uiState.value =
                            ShowDetailUiState.Content(
                                details = details,
                                selectedSeason = restoredSeason,
                                isFavorite = flags.isFavorite,
                                isWatchLater = flags.isWatchLater,
                                progressFraction = progress?.fraction,
                                watchedEpisodeIds = details.seasons
                                    .flatMap { season -> season.episodes }
                                    .filter { episode -> watchedIds.contains(episode.id.value.toString()) }
                                    .map { episode -> episode.id.value }
                                    .toImmutableList(),
                                episodeProgress = episodeProgressMap(details, progressRows),
                                seasonQueues = seasonQueues.toImmutableList(),
                            )
                        if (seasonQueues.any { it.isActive() }) {
                            awaitSeasonQueuesTerminal()
                        }
                    } else {
                        _uiState.value = ShowDetailUiState.Error(UiError.NotFound("Unexpected media type"))
                    }
                }

                is Outcome.Failure -> {
                    _uiState.value = ShowDetailUiState.Error(detailsResult.error.toUiError())
                }
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.update { current ->
            if (current is ShowDetailUiState.Content) current.copy(selectedSeason = seasonNumber) else current
        }
    }

    /**
     * Queues every episode of the selected season for download. Re-queues
     * when the previous queue completed or failed; no-op while a queue for
     * the season is still active or when the season has no episodes.
     */
    fun downloadSeason() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        val current = seasonDownloadState(content.seasonQueues, content.selectedSeason)
        if (current == SeasonDownloadState.Queued || current == SeasonDownloadState.Downloading) return
        val season = content.details.seasons.firstOrNull { it.seasonNumber == content.selectedSeason } ?: return
        if (season.episodes.isEmpty()) return
        viewModelScope.launch {
            startSeasonDownload(content.selectedSeason, season.episodes.toImmutableList())
            refreshSeasonQueues()
            awaitSeasonQueuesTerminal()
        }
    }

    private suspend fun loadSeasonQueues(): List<SeasonDownloadQueue> = listSeasonQueues()
        .getOrElse { error ->
            log.w { "Failed to load season queues: $error" }
            emptyList()
        }

    private suspend fun refreshSeasonQueues() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        _uiState.value = content.copy(seasonQueues = loadSeasonQueues().toImmutableList())
    }

    /**
     * Refreshes the queue list while any queue is still active so the show
     * button tracks progress without leaving the screen. Gives up after
     * [MAX_QUEUE_POLLS] to avoid polling a queue that never advances (the
     * web tier keeps queues queued without a transfer worker).
     */
    private suspend fun awaitSeasonQueuesTerminal() {
        repeat(MAX_QUEUE_POLLS) {
            delay(QUEUE_POLL_INTERVAL_MS)
            refreshSeasonQueues()
            val content = _uiState.value as? ShowDetailUiState.Content ?: return
            if (content.seasonQueues.none { it.isActive() }) return
        }
    }

    fun toggleFavorite() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        viewModelScope.launch {
            val result = if (content.isFavorite) {
                removeFromLibrary(mediaId)
            } else {
                addToLibrary(libraryItem(LibraryCollection.FAVORITES))
            }
            if (result is Outcome.Failure) {
                log.w { "Failed to toggle favorite: ${result.error}" }
            }
            refreshFlags()
        }
    }

    fun toggleWatchLater() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        viewModelScope.launch {
            val result = if (content.isWatchLater) {
                removeFromLibrary(mediaId)
            } else {
                addToLibrary(libraryItem(LibraryCollection.WATCH_LATER))
            }
            if (result is Outcome.Failure) {
                log.w { "Failed to toggle watch later: ${result.error}" }
            }
            refreshFlags()
        }
    }

    private suspend fun loadFlags(): DetailFlags {
        val library = when (val lib = listLibrary()) {
            is Outcome.Success -> lib.value

            is Outcome.Failure -> {
                log.w { "Failed to load library: ${lib.error}" }
                emptyList()
            }
        }
        return DetailFlags(
            isFavorite = library.any { it.mediaId == mediaId && it.collection == LibraryCollection.FAVORITES },
            isWatchLater = library.any { it.mediaId == mediaId && it.collection == LibraryCollection.WATCH_LATER },
        )
    }

    private suspend fun refreshFlags() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        val flags = loadFlags()
        _uiState.value = content.copy(
            isFavorite = flags.isFavorite,
            isWatchLater = flags.isWatchLater,
        )
    }

    private fun libraryItem(collection: LibraryCollection): LibraryItem = LibraryItem(
        mediaId = mediaId,
        collection = collection,
        addedAtEpochSeconds = clock.now(),
        sortOrder = 0,
    )

    /**
     * The stored progress playback would resume from for this show: the
     * most recently updated row among the show itself and its episodes,
     * filtered through [ResumePolicy] so the detail Play label matches the
     * player's resume decision.
     */
    private fun resumableShowProgress(details: ShowDetails, progressRows: List<PlaybackProgress>): PlaybackProgress? {
        val episodeIds = details.seasons.flatMap { season -> season.episodes }.map { episode -> episode.id }.toSet()
        return progressRows
            .filter { progress ->
                progress.mediaId == mediaId ||
                    (progress.mediaId as? Media.MediaId.Episode)?.value in episodeIds
            }
            .maxByOrNull { it.lastUpdatedEpochSeconds }
            ?.takeIf { ResumePolicy.resumablePosition(it) != null }
    }

    /**
     * Resumable fraction for every episode with stored progress, so each
     * episode row can show its own "Resume N%" hint.
     */
    private fun episodeProgressMap(
        details: ShowDetails,
        progressRows: List<PlaybackProgress>,
    ): ImmutableMap<Int, Double> {
        val episodeIds = details.seasons.flatMap { season ->
            season.episodes
        }.map { episode -> episode.id }.toSet()
        return buildMap<Int, Double> {
            progressRows.forEach { progress ->
                val episode = progress.mediaId as? Media.MediaId.Episode ?: return@forEach
                if (episode.value !in episodeIds) return@forEach
                if (ResumePolicy.resumablePosition(progress) == null) return@forEach
                put(episode.value.value, progress.fraction)
            }
        }.toImmutableMap()
    }

    private suspend fun loadProgressRows(): List<PlaybackProgress> = listProgress()
        .getOrElse { error ->
            log.w { "Failed to load progress: $error" }
            emptyList()
        }

    private fun parseSeason(saved: String, seasons: List<Season>): Int {
        val parsed = saved.toIntOrNull()
        if (parsed != null && seasons.any { it.seasonNumber == parsed }) return parsed
        return seasons.minOfOrNull { it.seasonNumber } ?: 1
    }

    private companion object {
        const val QUEUE_POLL_INTERVAL_MS = 2_000L
        const val MAX_QUEUE_POLLS = 900
    }
}
