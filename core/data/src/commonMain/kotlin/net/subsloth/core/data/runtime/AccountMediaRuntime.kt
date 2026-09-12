package net.subsloth.core.data.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.subsloth.core.data.media.CatalogRepository
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.error.NetworkErrorClassifier
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.mapper.Mapper
import net.subsloth.database.dao.AccountPlaybackProgressDao
import net.subsloth.database.dao.OfflinePlaybackProgressDao
import net.subsloth.database.dao.WatchedStateDao
import net.subsloth.database.entity.AccountPlaybackProgressEntity
import net.subsloth.database.entity.OfflinePlaybackProgressEntity
import net.subsloth.database.entity.WatchedStateEntity
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Platform-neutral, account-scoped media runtime shared by the Android and
 * desktop composition roots.
 *
 * Both containers previously carried byte-identical copies of these helpers
 * (catalog projections, playback-progress persistence, watched state, and
 * subtitle-track resolution). The platform-specific parts — DataStore,
 * credential storage, and byte-transfer wiring — stay in each container;
 * this class owns the logic that only depends on the session, the
 * session-scoped [CatalogRepository]/[Api], and the Room DAOs.
 *
 * Providers are lambdas because both containers rebuild the API/repository
 * pair whenever the session changes; resolving them at call time keeps this
 * runtime pointed at the current session.
 */
class AccountMediaRuntime(
    private val sessionPort: SessionPort,
    private val catalogRepository: () -> CatalogRepository,
    private val api: () -> Api,
    private val accountPlaybackProgressDao: () -> AccountPlaybackProgressDao,
    private val offlinePlaybackProgressDao: () -> OfflinePlaybackProgressDao,
    private val watchedStateDao: () -> WatchedStateDao,
    private val clock: Clock,
    private val anonymousProfileKey: String,
) {
    /** Profile key for the active session; [anonymousProfileKey] when signed out. */
    fun currentProfileKey(): AccountProfileKey = when (val session = sessionPort.current()) {
        is Session.Authenticated -> AccountProfileKey(session.userId)
        Session.Anonymous -> AccountProfileKey(anonymousProfileKey)
    }

    // ── Catalog projections ──────────────────────────────────────────────

    suspend fun listMovies(): Result<List<MovieSummary>> = runCatching {
        catalogRepository().catalogItems("movie").first().filterIsInstance<MovieSummary>()
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun listShows(): Result<List<ShowSummary>> = runCatching {
        catalogRepository().catalogItems("show").first().filterIsInstance<ShowSummary>()
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Merged movie+show catalog for the search screen. A failed side
     * degrades to a failure so the screen renders no results rather than a
     * partial list.
     */
    suspend fun listAllMedia(): Outcome<List<Media>> {
        val movies = listMovies()
        val shows = listShows()
        return when {
            movies.isSuccess && shows.isSuccess -> Outcome.Success(
                movies.getOrThrow() + shows.getOrThrow(),
            )

            else -> {
                val error = movies.exceptionOrNull() ?: shows.exceptionOrNull()
                    ?: return Outcome.Success(emptyList())
                Outcome.Failure(NetworkErrorClassifier.classifyToNetwork(error))
            }
        }
    }

    suspend fun fetchEpisodesForShow(showId: Media.MediaId.Show): Outcome<List<Episode>> =
        when (val result = catalogRepository().getDetails(showId)) {
            is Outcome.Success -> {
                val details = result.value as? ShowDetails
                if (details != null) {
                    Outcome.Success(details.seasons.flatMap { it.episodes })
                } else {
                    Outcome.Failure(MediaError.NotFound)
                }
            }

            is Outcome.Failure -> Outcome.Failure(result.error)
        }

    // ── Playback progress ────────────────────────────────────────────────

    suspend fun listAccountPlaybackProgress(): Result<List<PlaybackProgress>> = runCatching {
        when (val session = sessionPort.current()) {
            Session.Anonymous -> emptyList()

            is Session.Authenticated -> accountPlaybackProgressDao()
                .getAllForProfile(session.userId)
                .first()
                .map { it.toPlaybackProgress() }
        }
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun loadPlaybackProgress(mediaId: Media.MediaId): PlaybackProgress? =
        listAccountPlaybackProgress().getOrDefault(emptyList()).firstOrNull { it.mediaId == mediaId }

    /**
     * Persists progress for the active session: online playback goes to the
     * account-scoped table (and crosses the watched threshold into
     * [markWatched]); offline playback goes to the shared offline table.
     */
    suspend fun savePlaybackProgress(
        mediaId: Media.MediaId,
        positionSeconds: Long,
        durationSeconds: Long,
        playbackMode: PlaybackMode = PlaybackMode.ONLINE,
    ) {
        when (playbackMode) {
            PlaybackMode.OFFLINE -> offlinePlaybackProgressDao().upsert(
                OfflinePlaybackProgressEntity(
                    contentId = mediaId.toContentId(),
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                    updatedAtEpochSeconds = clock.now().epochSeconds,
                ),
            )

            PlaybackMode.ONLINE -> {
                accountPlaybackProgressDao().upsert(
                    AccountPlaybackProgressEntity(
                        profileKey = currentProfileKey().value,
                        contentId = mediaId.toContentId(),
                        contentType = mediaId.toContentType(),
                        positionSeconds = positionSeconds,
                        durationSeconds = durationSeconds,
                        updatedAtEpochSeconds = clock.now().epochSeconds,
                    ),
                )
                if (durationSeconds > 0 &&
                    positionSeconds.toDouble() / durationSeconds > CompletionPolicy.WATCHED_THRESHOLD
                ) {
                    markWatched(mediaId)
                }
            }
        }
    }

    /** Marks [mediaId] watched for the active profile (watched_state table). */
    suspend fun markWatched(mediaId: Media.MediaId) {
        watchedStateDao().upsert(
            WatchedStateEntity(
                profileKey = currentProfileKey().value,
                contentId = mediaId.toContentId(),
                contentType = mediaId.toContentType(),
                isWatched = true,
                watchedAtEpochSeconds = clock.now().epochSeconds,
            ),
        )
    }

    /** Content ids marked watched for the active profile. */
    suspend fun listWatchedContentIds(): Set<String> = watchedStateDao()
        .getAllForProfile(currentProfileKey().value)
        .first()
        .filter { it.isWatched }
        .map { it.contentId }
        .toSet()

    /** Watched state for a single media item, for the active profile. */
    suspend fun isWatched(mediaId: Media.MediaId): Boolean = watchedStateDao()
        .getByProfileAndContentId(currentProfileKey().value, mediaId.toContentId())
        ?.isWatched == true

    // ── Subtitle tracks ──────────────────────────────────────────────────

    /**
     * Resolves the subtitle tracks offered for [mediaId] at transfer time.
     * Signed subtitle URLs are ephemeral, so they are fetched fresh and
     * never persisted.
     */
    suspend fun resolveSubtitleTracks(mediaId: Media.MediaId): List<Subtitle> {
        val tracks = when (mediaId) {
            is Media.MediaId.Movie -> api().getMovie(mediaId.value.value).subtitles
            is Media.MediaId.Episode -> api().getEpisode(mediaId.value.value).subtitles
            is Media.MediaId.Show -> null
        }
        return Mapper.mapSubtitleTracks(tracks)
    }

    private fun AccountPlaybackProgressEntity.toPlaybackProgress(): PlaybackProgress {
        val fraction = if (durationSeconds > 0) {
            positionSeconds.toDouble() / durationSeconds.toDouble()
        } else {
            0.0
        }
        return PlaybackProgress(
            mediaId = parsePlaybackMediaId(contentId, contentType),
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(updatedAtEpochSeconds),
            isWatched = fraction > CompletionPolicy.WATCHED_THRESHOLD,
        )
    }

    private fun parsePlaybackMediaId(contentId: String, contentType: String): Media.MediaId = when (contentType) {
        "movie" -> Media.MediaId.Movie(MovieId(contentId.toIntOrNull() ?: error("Invalid contentId: $contentId")))

        "show" -> Media.MediaId.Show(ShowId(contentId.toIntOrNull() ?: error("Invalid contentId: $contentId")))

        "episode" -> Media.MediaId.Episode(
            EpisodeId(contentId.toIntOrNull() ?: error("Invalid contentId: $contentId")),
        )

        else -> error("Unknown contentType: $contentType")
    }
}

/** Numeric content id used by Room columns and library keys. */
internal fun Media.MediaId.toContentId(): String = when (this) {
    is Media.MediaId.Movie -> value.value.toString()
    is Media.MediaId.Show -> value.value.toString()
    is Media.MediaId.Episode -> value.value.toString()
}

/** Lowercase media type used by Room columns. */
internal fun Media.MediaId.toContentType(): String = when (this) {
    is Media.MediaId.Movie -> "movie"
    is Media.MediaId.Show -> "show"
    is Media.MediaId.Episode -> "episode"
}
