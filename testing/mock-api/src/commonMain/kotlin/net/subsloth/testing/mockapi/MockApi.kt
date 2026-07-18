package net.subsloth.testing.mockapi

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import net.subsloth.core.domain.port.CachedCatalogItem
import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ExternalIdSource
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.progress.PlaybackProgress
import kotlin.time.Instant

/**
 * Deterministic in-memory mock of the media service.
 *
 * The mock is **not** used in production. Production wires the real
 * `core:network` Ktor adapter. The mock is the target of the
 * screenshot test suite, the dev/demo build flavours, and VM-level
 * tests that want to exercise the full port chain without a real
 * backend.
 *
 * The mock supports a session lifecycle:
 * - [login] stores a credential pair (any non-empty pair is accepted).
 * - All port calls succeed while a session is active.
 * - [expireSession] marks the session invalid; the next call returns
 *   [AuthError.SessionExpired] until [login] is called again.
 * - [logout] clears the session without expiring it.
 *
 * Usage from a port adapter:
 * ```
 * class MockCatalogPort : CatalogPort {
 *     override suspend fun listCatalog() = MockApi.listCatalog()
 *     override suspend fun getDetails(id: Media.MediaId) = MockApi.getDetails(id)
 * }
 * ```
 */
object MockApi {
    // ── State ──────────────────────────────────────────────────────────────

    private val seedMovies: List<MovieSummary> by lazy { buildSeedMovies() }
    private val seedShows: List<ShowSummary> by lazy { buildSeedShows() }
    private val seedEpisodes: Map<ShowId, List<Episode>> by lazy { buildSeedEpisodes() }

    private val library: MutableMap<Media.MediaId, LibraryItem> = mutableMapOf(
        Media.MediaId.Movie(MovieId(1)) to libraryItem(Media.MediaId.Movie(MovieId(1)), LibraryCollection.FAVORITES, 0),
        Media.MediaId.Movie(MovieId(2)) to libraryItem(Media.MediaId.Movie(MovieId(2)), LibraryCollection.HISTORY, 1),
        Media.MediaId.Movie(MovieId(3)) to libraryItem(Media.MediaId.Movie(MovieId(3)), LibraryCollection.CUSTOM, 2),
    )

    private val downloads: MutableMap<String, DownloadState> = mutableMapOf(
        "dl-movie-1" to buildCompletedDownload(Media.MediaId.Movie(MovieId(1)), Resolution.FULL_HD),
        "dl-movie-2" to buildActiveDownload(Media.MediaId.Movie(MovieId(2)), Resolution.HD_720, 0.35),
    )

    private val progress: MutableMap<Media.MediaId, PlaybackProgress> = mutableMapOf(
        Media.MediaId.Movie(MovieId(1)) to PlaybackProgress(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            positionSeconds = 1800L,
            durationSeconds = 7200L,
            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L),
            isWatched = false,
        ),
        Media.MediaId.Movie(MovieId(2)) to PlaybackProgress(
            mediaId = Media.MediaId.Movie(MovieId(2)),
            positionSeconds = 600L,
            durationSeconds = 5400L,
            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L),
            isWatched = false,
        ),
    )

    private var sessionExpired: Boolean = false

    private fun libraryItem(mediaId: Media.MediaId, collection: LibraryCollection, sortOrder: Int): LibraryItem =
        LibraryItem(
            mediaId = mediaId,
            collection = collection,
            addedAtEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L + sortOrder.toLong()),
            sortOrder = sortOrder,
        )

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun login(email: String, password: String): Outcome<Unit> {
        if (email.isBlank() || password.isBlank()) {
            return Outcome.Failure(AuthError.InvalidCredentials)
        }
        sessionExpired = false
        return Outcome.Success(Unit)
    }

    fun logout() {
        sessionExpired = false
    }

    /** Mark the current session invalid. The next port call returns [AuthError.SessionExpired]. */
    fun expireSession() {
        sessionExpired = true
    }

    /** Reset all mutable state to the seed values. Use between tests. */
    fun reset() {
        library.clear()
        library[Media.MediaId.Movie(MovieId(1))] =
            libraryItem(Media.MediaId.Movie(MovieId(1)), LibraryCollection.FAVORITES, 0)
        library[Media.MediaId.Movie(MovieId(2))] =
            libraryItem(Media.MediaId.Movie(MovieId(2)), LibraryCollection.HISTORY, 1)
        library[Media.MediaId.Movie(MovieId(3))] =
            libraryItem(Media.MediaId.Movie(MovieId(3)), LibraryCollection.CUSTOM, 2)
        downloads.clear()
        downloads["dl-movie-1"] = buildCompletedDownload(Media.MediaId.Movie(MovieId(1)), Resolution.FULL_HD)
        downloads["dl-movie-2"] = buildActiveDownload(Media.MediaId.Movie(MovieId(2)), Resolution.HD_720, 0.35)
        progress.clear()
        progress[Media.MediaId.Movie(MovieId(1))] = PlaybackProgress(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            positionSeconds = 1800L,
            durationSeconds = 7200L,
            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L),
            isWatched = false,
        )
        progress[Media.MediaId.Movie(MovieId(2))] = PlaybackProgress(
            mediaId = Media.MediaId.Movie(MovieId(2)),
            positionSeconds = 600L,
            durationSeconds = 5400L,
            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L),
            isWatched = false,
        )
        sessionExpired = false
    }

    // ── CatalogPort ────────────────────────────────────────────────────────

    fun listCatalog(): Outcome<List<Media>> = guard {
        seedMovies + seedShows
    }

    fun getDetails(id: Media.MediaId): Outcome<MovieDetails> = guard {
        when (id) {
            is Media.MediaId.Movie -> seedMovies.firstOrNull { it.id == id }
                ?.let { buildMovieDetails(it) }
                ?: throw IllegalStateException("Unknown movie id: $id")

            is Media.MediaId.Show -> throw IllegalStateException("Show details not supported in mock")

            is Media.MediaId.Episode -> throw IllegalStateException("Episodes are accessed via ShowDetails, not by id")
        }
    }

    // ── LibraryPort ────────────────────────────────────────────────────────

    fun listLibrary(): Outcome<List<LibraryItem>> = guard {
        library.values.toList()
    }

    fun addToLibrary(mediaId: Media.MediaId, collection: LibraryCollection): Outcome<Unit> = addToLibrary(
        libraryItem(mediaId, collection, library.size),
    )

    fun addToLibrary(item: LibraryItem): Outcome<Unit> = guard {
        library[item.mediaId] = item
    }

    fun removeFromLibrary(mediaId: Media.MediaId): Outcome<Unit> = guard {
        library.remove(mediaId)
    }

    // ── DownloadsPort (subset) ─────────────────────────────────────────────

    fun listDownloads(): Outcome<ImmutableList<DownloadState>> = guard {
        downloads.values.toImmutableList()
    }

    fun listProgress(): Outcome<List<PlaybackProgress>> = guard {
        progress.values.toList()
    }

    // ── CatalogSyncPort (subset) ───────────────────────────────────────────

    fun sync(): Outcome<Unit> = guard<Unit> { }

    fun isStale(): Boolean = false

    // ── CatalogCachePort (subset) ──────────────────────────────────────────

    fun listCatalogCache(): Outcome<List<CachedCatalogItem>> = guard {
        seedMovies.map { movie ->
            CachedCatalogItem(
                contentId = movie.id.value.value.toString(),
                contentType = "movie",
                title = movie.title,
                plot = movie.plot,
                posterUrl = null,
                backdropUrl = movie.backdropUrl,
                year = movie.year,
                rating = movie.rating,
                durationMinutes = movie.durationMinutes,
                slug = movie.slug,
                imdbId = movie.imdbId?.value,
                tmdbId = null,
                status = null,
                updatedAtEpochSeconds = movie.updatedAtEpochSeconds?.epochSeconds,
                newestVideoEpochSeconds = null,
                genres = movie.genres.toList(),
                countries = emptyList(),
            )
        }
    }

    // ── Session guard ──────────────────────────────────────────────────────

    private inline fun <T> guard(block: () -> T): Outcome<T> = when {
        sessionExpired -> Outcome.Failure(AuthError.SessionExpired)

        else -> try {
            Outcome.Success(block())
        } catch (_: IllegalStateException) {
            Outcome.Failure(NetworkError.UnexpectedResponse)
        }
    }

    // ── Seed data ──────────────────────────────────────────────────────────

    private fun buildSeedMovies(): List<MovieSummary> = (1..10).map { idx ->
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(idx)),
            title = "Movie $idx",
            plot = "Plot for movie $idx — a thrilling test fixture.",
            availability = Availability.Available,
            rating = 7.0 + (idx % 3) * 0.5,
            year = 2020 + idx,
            genres = genresForMovie(idx),
            durationMinutes = 90 + idx * 5,
            slug = "movie-$idx",
            imdbId = ExternalId(source = ExternalIdSource.IMDb, value = "tt$idx"),
            backdropUrl = "https://example.com/movie-$idx.jpg",
            updatedAtEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L + idx.toLong() * 86_400),
        )
    }

    private fun genresForMovie(idx: Int): ImmutableList<String> {
        val base = if (idx % 2 == 0) listOf("Action") else listOf("Drama")
        val extra = if (idx % 3 == 0) listOf("Sci-Fi") else listOf("Comedy")
        return (base + extra).toImmutableList()
    }

    private fun buildSeedShows(): List<ShowSummary> = (1..5).map { idx ->
        ShowSummary(
            id = Media.MediaId.Show(ShowId(idx)),
            title = "Show $idx",
            plot = "Plot for show $idx — a binge-worthy test fixture.",
            availability = Availability.Available,
            rating = 8.0 + (idx % 3) * 0.4,
            year = 2018 + idx,
            genres = (if (idx % 2 == 0) listOf("Drama", "Thriller") else listOf("Comedy")).toImmutableList(),
            durationMinutes = 45,
            slug = "show-$idx",
            imdbId = ExternalId(source = ExternalIdSource.IMDb, value = "tt${100 + idx}"),
            backdropUrl = "https://example.com/show-$idx.jpg",
            status = if (idx % 2 == 0) ShowStatus.ENDED else ShowStatus.ONGOING,
            countries = listOf("US", "UK").toImmutableList(),
            newestVideoEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L + idx.toLong() * 86_400),
        )
    }

    private fun buildSeedEpisodes(): Map<ShowId, List<Episode>> = (1..5).associate { showIdx ->
        ShowId(showIdx) to (1..4).map { epIdx ->
            Episode(
                id = EpisodeId(showIdx * 100 + epIdx),
                showId = ShowId(showIdx),
                seasonNumber = 1,
                episodeNumber = epIdx,
                title = "S1E$epIdx of Show $showIdx",
                plot = "Plot for episode $epIdx of show $showIdx.",
                durationSeconds = (45 * 60).toLong(),
                availability = Availability.Available,
                imdbId = null,
                qualities = listOf(
                    Quality(
                        info = QualityDescriptor(
                            resolution = Resolution.FULL_HD,
                            label = "1080p",
                            bitrate = 5_000_000,
                            mimeType = "video/mp4",
                        ),
                        url = "https://example.com/show-$showIdx/ep-$epIdx/1080p.m3u8",
                        downloadUrl = null,
                    ),
                    Quality(
                        info = QualityDescriptor(
                            resolution = Resolution.HD_720,
                            label = "720p",
                            bitrate = 2_500_000,
                            mimeType = "video/mp4",
                        ),
                        url = "https://example.com/show-$showIdx/ep-$epIdx/720p.m3u8",
                        downloadUrl = null,
                    ),
                ).toImmutableList(),
                subtitles = listOf(
                    Subtitle(
                        language = LanguageCode("en"),
                        languageDisplayName = "English",
                        url = "https://example.com/show-$showIdx/ep-$epIdx/en.vtt",
                        downloadUrl = null,
                        format = SubtitleFormat.VTT,
                    ),
                ).toImmutableList(),
                airDateEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L + epIdx.toLong() * 86_400),
                premiereDateEpochSeconds = null,
            )
        }
    }

    private fun buildMovieDetails(summary: MovieSummary): MovieDetails = MovieDetails(
        id = summary.id,
        title = summary.title,
        plot = summary.plot,
        description = "Long description for ${summary.title}.",
        availability = summary.availability,
        rating = summary.rating,
        year = summary.year,
        genres = summary.genres,
        durationMinutes = summary.durationMinutes,
        qualities = listOf(
            Quality(
                info = QualityDescriptor(
                    resolution = Resolution.FULL_HD,
                    label = "1080p",
                    bitrate = 5_000_000,
                    mimeType = "video/mp4",
                ),
                url = "https://example.com/${summary.slug}/1080p.m3u8",
                downloadUrl = null,
            ),
            Quality(
                info = QualityDescriptor(
                    resolution = Resolution.HD_720,
                    label = "720p",
                    bitrate = 2_500_000,
                    mimeType = "video/mp4",
                ),
                url = "https://example.com/${summary.slug}/720p.m3u8",
                downloadUrl = null,
            ),
        ).toImmutableList(),
        subtitles = listOf(
            Subtitle(
                language = LanguageCode("en"),
                languageDisplayName = "English",
                url = "https://example.com/${summary.slug}/en.vtt",
                downloadUrl = null,
                format = SubtitleFormat.VTT,
            ),
        ).toImmutableList(),
        slug = summary.slug,
        imdbId = summary.imdbId,
        tmdbId = null,
        countries = listOf("US", "UK").toImmutableList(),
        posterUrl = null,
        backdropUrl = summary.backdropUrl,
    )

    private fun buildShowDetails(summary: ShowSummary): ShowDetails = ShowDetails(
        id = summary.id,
        title = summary.title,
        plot = summary.plot,
        description = "Long description for ${summary.title}.",
        availability = summary.availability,
        rating = summary.rating,
        year = summary.year,
        genres = summary.genres,
        durationMinutes = summary.durationMinutes,
        qualities = listOf(
            Quality(
                info = QualityDescriptor(
                    resolution = Resolution.FULL_HD,
                    label = "1080p",
                    bitrate = 5_000_000,
                    mimeType = "video/mp4",
                ),
                url = "https://example.com/${summary.slug}/1080p.m3u8",
                downloadUrl = null,
            ),
        ).toImmutableList(),
        subtitles = listOf(
            Subtitle(
                language = LanguageCode("en"),
                languageDisplayName = "English",
                url = "https://example.com/${summary.slug}/en.vtt",
                downloadUrl = null,
                format = SubtitleFormat.VTT,
            ),
        ).toImmutableList(),
        slug = summary.slug,
        imdbId = summary.imdbId,
        tmdbId = null,
        countries = summary.countries,
        posterUrl = null,
        backdropUrl = summary.backdropUrl,
        status = summary.status,
        popularity = null,
        seasons = seedEpisodes[summary.id.value]?.groupBy { it.seasonNumber }
            ?.toList()
            ?.sortedBy { it.first }
            ?.map { (seasonNumber, eps) ->
                Season(
                    seasonNumber = seasonNumber,
                    title = "Season $seasonNumber",
                    plot = null,
                    episodes = eps.sortedBy { it.episodeNumber }.toImmutableList(),
                )
            }
            ?.toImmutableList() ?: persistentListOf(),
    )

    private fun buildCompletedDownload(mediaId: Media.MediaId.Movie, quality: Resolution): DownloadState.Completed =
        DownloadState.Completed(
            localId = LocalMediaIdentifier("dl-movie-${mediaId.value.value}"),
            mediaId = mediaId,
            quality = QualityDescriptor(
                resolution = quality,
                label = "${quality.label} test",
                bitrate = null,
                mimeType = null,
            ),
            downloadedAtEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L),
            sizeBytes = 1_500_000_000L,
            videoPath = OfflineRelativePath("dl-movie-${mediaId.value.value}/video.mp4"),
            subtitleLanguages = listOf(LanguageCode("en")).toImmutableSet(),
        )

    private fun buildActiveDownload(
        mediaId: Media.MediaId.Movie,
        quality: Resolution,
        fraction: Double,
    ): DownloadState.Active = DownloadState.Active(
        localId = LocalMediaIdentifier("dl-movie-${mediaId.value.value}"),
        mediaId = mediaId,
        quality = QualityDescriptor(
            resolution = quality,
            label = "${quality.label} test",
            bitrate = null,
            mimeType = null,
        ),
        progressPercent = (fraction * 100).toInt(),
        subtitleLanguages = listOf(LanguageCode("en")).toImmutableSet(),
    )
}
