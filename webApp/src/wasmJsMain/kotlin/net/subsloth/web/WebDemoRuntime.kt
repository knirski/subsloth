package net.subsloth.web

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.subsloth.catalog.HomeViewModel
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.fold
import net.subsloth.core.model.error.getOrElse
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.playback.VideoSource
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
class WebDemoRuntime internal constructor(private val api: Api) {
    private val log = Logger.withTag("WebDemoRuntime")

    suspend fun listCatalog(): Outcome<List<Media>> = try {
        val movies = Mapper.mapMovies(api.listMovies().movies).items
        val shows = Mapper.mapShows(api.listShows().shows).items
        Outcome.Success(movies + shows)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        log.e(exception) { "listCatalog failed" }
        Outcome.Failure(DecodeError.SerializationFailed)
    }

    suspend fun getDetails(mediaId: Media.MediaId): Outcome<MediaDetails> = try {
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

    fun catalogItems(type: String): Flow<List<Media>> = flow {
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
    suspend fun fetchVideoSource(mediaId: Media.MediaId): Outcome<VideoSource> {
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

    fun createHomeViewModel(): HomeViewModel = HomeViewModel(
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

    fun close() = api.close()

    private fun mediaType(media: Media): String = when (media) {
        is MovieSummary -> "movie"
        is ShowSummary -> "show"
    }
}

internal fun createWebDemoRuntime(): WebDemoRuntime {
    ClientConfig.useMock = true
    return WebDemoRuntime(Api(ClientFactory.create()))
}

internal class WebDemoApp(
    val runtime: WebDemoRuntime,
    val mode: WebRuntimeMode,
    val startDestination: AppNavKey,
    val bannerText: String,
) {
    fun close() = runtime.close()
}

internal fun createWebDemoApp(): WebDemoApp = WebDemoApp(
    runtime = createWebDemoRuntime(),
    mode = WebRuntimeMode.Demo,
    startDestination = CatalogKey,
    bannerText = DEMO_BANNER_TEXT,
)
