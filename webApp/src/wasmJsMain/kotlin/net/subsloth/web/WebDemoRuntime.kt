package net.subsloth.web

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import net.subsloth.catalog.HomeViewModel
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.domain.port.InMemorySessionState
import net.subsloth.core.domain.port.LibraryPort
import net.subsloth.core.domain.port.PlaybackPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.fold
import net.subsloth.core.model.error.getOrElse
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientConfig
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.core.network.media.mapper.Mapper
import net.subsloth.core.ui.AppNavKey
import net.subsloth.core.ui.CatalogKey
import subsloth.webapp.generated.resources.Res
import kotlin.coroutines.cancellation.CancellationException

/** Duration of the bundled mock film, in seconds. */
private const val MOCK_FILM_DURATION_SECONDS = 45L
private const val MOCK_MOVIE2_DURATION_SECONDS = 8L
private const val MOCK_SERIES_DURATION_SECONDS = 8L

/** Bundled demo assets: each maps to a small mp4 + EN/PL SRT sidecars. */
private enum class MockVideoAsset(
    val videoPath: String,
    val subtitleEnPath: String,
    val subtitlePlPath: String,
    val durationSeconds: Long,
) {
    Film("files/mock_film.mp4", "files/mock_film.srt", "files/mock_film_pl.srt", MOCK_FILM_DURATION_SECONDS),
    Movie2("files/mock_movie2.mp4", "files/mock_movie2.srt", "files/mock_movie2_pl.srt", MOCK_MOVIE2_DURATION_SECONDS),
    Series("files/mock_series.mp4", "files/mock_series.srt", "files/mock_series_pl.srt", MOCK_SERIES_DURATION_SECONDS),
}

/** The fixture-backed runtime used by the publicly deployed GitHub Pages demo. */
class WebDemoRuntime internal constructor(private val api: Api) : WebRuntime {
    private val log = Logger.withTag("WebDemoRuntime")

    override suspend fun listCatalog(): Outcome<List<Media>> = try {
        val movies = Mapper.mapMovies(api.listMovies().movies).items
        val shows = Mapper.mapShows(api.listShows().shows).items
        Outcome.Success(movies + shows)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        log.e(exception) { "listCatalog failed" }
        Outcome.Failure(DecodeError.SerializationFailed)
    }

    override suspend fun getDetails(mediaId: Media.MediaId): Outcome<MediaDetails> = try {
        when (mediaId) {
            is Media.MediaId.Movie -> Mapper.mapMovieDetails(api.getMovie(mediaId.value.value))
            is Media.MediaId.Show -> Mapper.mapShowDetails(api.getShow(mediaId.value.value))
            is Media.MediaId.Episode -> Mapper.mapEpisodeDetails(api.getEpisode(mediaId.value.value))
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        log.e(exception) { "getDetails failed for $mediaId" }
        Outcome.Failure(DecodeError.SerializationFailed)
    }

    override fun catalogItems(type: String): Flow<List<Media>> = flow {
        emit(
            listCatalog().getOrElse { emptyList() }
                .filter { media -> mediaType(media) == type },
        )
    }

    /**
     * Returns a bundled mock asset for the requested media so the Pages
     * demo can exercise real playback. Assets are per-kind (movies →
     * mock_film/mock_movie2, series episodes → mock_series), each with
     * EN/PL subtitle tracks and the asset's own duration.
     */
    override suspend fun fetchVideoSource(mediaId: Media.MediaId): Outcome<VideoSource> {
        val displayName = listCatalog().fold(
            onSuccess = { media -> media.firstOrNull { it.id == mediaId }?.title },
            onFailure = { null },
        )
        val asset = when (mediaId) {
            is Media.MediaId.Movie ->
                if (mediaId.value.value == 1379) MockVideoAsset.Movie2 else MockVideoAsset.Film

            is Media.MediaId.Episode, is Media.MediaId.Show -> MockVideoAsset.Series
        }
        val quality = Quality(
            info = QualityDescriptor(
                resolution = Resolution(480, 270),
                label = "480p",
                bitrate = null,
                mimeType = "video/mp4",
            ),
            url = Res.getUri(asset.videoPath),
            downloadUrl = null,
        )
        val subtitles = persistentListOf(
            Subtitle(
                language = LanguageCode("en"),
                languageDisplayName = "English",
                url = Res.getUri(asset.subtitleEnPath),
                downloadUrl = null,
                format = SubtitleFormat.SRT,
            ),
            Subtitle(
                language = LanguageCode("pl"),
                languageDisplayName = "Polski",
                url = Res.getUri(asset.subtitlePlPath),
                downloadUrl = null,
                format = SubtitleFormat.SRT,
            ),
        )
        return Outcome.Success(
            VideoSource(
                mediaId = mediaId,
                streamUrl = Res.getUri(asset.videoPath),
                selectedQuality = quality,
                availableQualities = persistentListOf(quality),
                availableSubtitles = subtitles,
                durationSeconds = asset.durationSeconds,
                displayName = displayName,
            ),
        )
    }

    // ── WebRuntime: production members, safe demo no-ops ────────────────

    /** Demo playback port: prepare/refresh resolve the bundled mock asset. */
    private val demoPlaybackPort = object : PlaybackPort {
        override suspend fun prepareSource(mediaId: Media.MediaId): Outcome<VideoSource> = fetchVideoSource(mediaId)

        override suspend fun play(source: VideoSource, positionSeconds: Long): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun pause(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun seek(positionSeconds: Long): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun refreshStreamUrl(mediaId: Media.MediaId): Outcome<VideoSource> = fetchVideoSource(mediaId)
    }

    private val emptyLibraryPort = object : LibraryPort {
        override suspend fun listLibrary(): Outcome<List<LibraryItem>> = Outcome.Success(emptyList())

        override suspend fun addToLibrary(item: LibraryItem): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun removeFromLibrary(mediaId: Media.MediaId): Outcome<Unit> = Outcome.Success(Unit)
    }

    override val playbackPort: PlaybackPort get() = demoPlaybackPort

    override val libraryPort: LibraryPort get() = emptyLibraryPort

    override suspend fun listAllMedia(): Outcome<List<Media>> = listCatalog()

    override suspend fun listMovies(): Result<List<MovieSummary>> = runCatching {
        listCatalog().fold(
            onSuccess = { media -> media.filterIsInstance<MovieSummary>() },
            onFailure = { emptyList() },
        )
    }

    override suspend fun listShows(): Result<List<ShowSummary>> = runCatching {
        listCatalog().fold(
            onSuccess = { media -> media.filterIsInstance<ShowSummary>() },
            onFailure = { emptyList() },
        )
    }

    override suspend fun fetchEpisodesForShow(showId: ShowId): Outcome<List<Episode>> =
        when (val details = getDetails(Media.MediaId.Show(showId))) {
            is Outcome.Success -> {
                val showDetails = details.value as? net.subsloth.core.model.media.ShowDetails
                if (showDetails != null) {
                    Outcome.Success(showDetails.seasons.flatMap { it.episodes })
                } else {
                    Outcome.Failure(net.subsloth.core.model.error.MediaError.NotFound)
                }
            }

            is Outcome.Failure -> Outcome.Failure(details.error)
        }

    override suspend fun resolveShowIdForEpisode(episodeId: EpisodeId): ShowId? = try {
        api.getEpisode(episodeId.value).showId?.let { ShowId(it) }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "resolveShowIdForEpisode failed for $episodeId" }
        null
    }

    override suspend fun fetchSubtitleText(url: String): Outcome<String> = fetchSubtitleTextViaBrowser(url)

    override suspend fun savePlaybackProgress(
        mediaId: Media.MediaId,
        positionSeconds: Long,
        durationSeconds: Long,
        playbackMode: PlaybackMode,
    ) = Unit

    override suspend fun listAccountPlaybackProgress(): Result<List<PlaybackProgress>> = Result.success(emptyList())

    override suspend fun savePlaybackSpeed(speed: Float) = Unit

    override suspend fun loadPlaybackSpeed(): Float = net.subsloth.core.domain.policy.PlaybackSpeedPolicy.defaultSpeed()

    override suspend fun loadPreferredLanguage(): LanguageCode = LanguageCode("en")

    override fun invalidateSession() = Unit

    override val downloadController: net.subsloth.core.domain.port.DownloadsPort get() = api.let {
        // Demo tier has no download pipeline; everything reports empty/no-op.
        object : net.subsloth.core.domain.port.DownloadsPort {
            override suspend fun listDownloads(): Result<kotlinx.collections.immutable.ImmutableList<DownloadState>> =
                Result.success(persistentListOf())

            override suspend fun listOfflineAssets():
                Result<kotlinx.collections.immutable.ImmutableList<net.subsloth.core.model.download.OfflineAsset>> =
                Result.success(persistentListOf())

            override suspend fun enqueue(
                mediaId: Media.MediaId,
                requested: net.subsloth.core.model.identifier.Resolution,
                requiredBytes: Long?,
                transferPreference: net.subsloth.core.model.download.TransferPreference,
            ): Result<EnqueueOutcome> = Result.success(EnqueueOutcome.Queued)

            override suspend fun enqueueSubtitle(
                localId: net.subsloth.core.model.identifier.LocalMediaIdentifier,
                language: LanguageCode,
            ): Result<net.subsloth.core.domain.port.SubtitleEnqueueOutcome> =
                Result.success(net.subsloth.core.domain.port.SubtitleEnqueueOutcome.Queued)

            override suspend fun pause(
                localId: net.subsloth.core.model.identifier.LocalMediaIdentifier,
            ): Result<DownloadCommandOutcome> = Result.success(DownloadCommandOutcome.NoOp)

            override suspend fun resume(
                localId: net.subsloth.core.model.identifier.LocalMediaIdentifier,
            ): Result<DownloadCommandOutcome> = Result.success(DownloadCommandOutcome.NoOp)

            override suspend fun cancel(
                localId: net.subsloth.core.model.identifier.LocalMediaIdentifier,
            ): Result<DownloadCommandOutcome> = Result.success(DownloadCommandOutcome.NoOp)

            override suspend fun remove(
                localId: net.subsloth.core.model.identifier.LocalMediaIdentifier,
            ): Result<DownloadCommandOutcome> = Result.success(DownloadCommandOutcome.NoOp)
        }
    }

    override suspend fun listLibrary(): Outcome<List<LibraryItem>> = emptyLibraryPort.listLibrary()

    override suspend fun listDownloads(): Result<kotlinx.collections.immutable.ImmutableList<DownloadState>> =
        downloadController.listDownloads()

    override suspend fun removeDownload(localId: String): Result<DownloadCommandOutcome> =
        downloadController.remove(net.subsloth.core.model.identifier.LocalMediaIdentifier(localId))

    override suspend fun listSeasonQueues(): Result<kotlinx.collections.immutable.ImmutableList<SeasonDownloadQueue>> =
        Result.success(persistentListOf())

    override suspend fun retryDownload(localId: String): EnqueueOutcome = EnqueueOutcome.Queued

    override suspend fun pauseDownload(localId: String): DownloadCommandOutcome = DownloadCommandOutcome.NoOp

    override suspend fun resumeDownload(localId: String): DownloadCommandOutcome = DownloadCommandOutcome.NoOp

    override suspend fun cancelDownload(localId: String): DownloadCommandOutcome = DownloadCommandOutcome.NoOp

    override suspend fun listWatchedContentIds(): Set<String> = emptySet()

    override suspend fun isWatched(mediaId: Media.MediaId): Boolean = false

    private val demoSessionPort: SessionPort = InMemorySessionState()

    override val sessionPort: SessionPort get() = demoSessionPort

    override fun readSubtitleEnabled(profileKey: AccountProfileKey): Flow<Boolean> = MutableStateFlow(false)

    override fun readSubtitleLanguage(profileKey: AccountProfileKey): Flow<String?> = MutableStateFlow(null)

    override fun readQuality(profileKey: AccountProfileKey): Flow<String?> = MutableStateFlow(null)

    override fun readPlaybackSpeed(profileKey: AccountProfileKey): Flow<Float> =
        MutableStateFlow(net.subsloth.core.domain.policy.PlaybackSpeedPolicy.defaultSpeed())

    override fun readDownloadsWifiOnly(profileKey: AccountProfileKey): Flow<Boolean> = MutableStateFlow(false)

    override fun currentProfileKey(): AccountProfileKey = when (val session = sessionPort.current()) {
        is Session.Authenticated -> AccountProfileKey(session.userId)
        Session.Anonymous -> AccountProfileKey("default")
    }

    override fun apiBaseUrlFlow(): Flow<String> =
        MutableStateFlow(net.subsloth.core.domain.LoginDefaults.DEFAULT_API_BASE_URL)

    override suspend fun saveApiBaseUrl(url: String) = Unit

    override fun writeSubtitleEnabled(enabled: Boolean) = Unit

    override fun writeSubtitleLanguage(language: String?) = Unit

    override fun writeQuality(quality: String?) = Unit

    override fun writePlaybackSpeed(speed: Float) = Unit

    override fun writeDownloadsWifiOnly(wifiOnly: Boolean) = Unit

    override fun deleteAllDownloads() = Unit

    override fun clearPreferences() = Unit

    override fun clearLibrary() = Unit

    override fun clearCredentials() = Unit

    override fun createHomeViewModel(): HomeViewModel = HomeViewModel(
        listCatalog = ::listCatalog,
        getDetails = ::getDetails,
        catalogItems = ::catalogItems,
        syncCatalog = {
            when (val result = listCatalog()) {
                is Outcome.Success -> Outcome.Success(Unit)
                is Outcome.Failure -> result
            }
        },
        isCatalogStale = { false },
    )

    override fun close() = api.close()

    private fun mediaType(media: Media): String = when (media) {
        is MovieSummary -> "movie"
        is ShowSummary -> "show"
    }
}

internal fun createWebDemoRuntime(): WebDemoRuntime {
    ClientConfig.useMock = true
    return WebDemoRuntime(Api(ClientFactory.create()))
}
