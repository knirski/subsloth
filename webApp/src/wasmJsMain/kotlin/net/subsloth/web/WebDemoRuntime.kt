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
            is Media.MediaId.Episode -> Outcome.Failure(DecodeError.SerializationFailed)
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

    /** Returns the bundled mock film so the Pages demo can exercise real playback. */
    suspend fun fetchVideoSource(mediaId: Media.MediaId): Outcome<VideoSource> {
        val displayName = listCatalog().fold(
            onSuccess = { media -> media.firstOrNull { it.id == mediaId }?.title },
            onFailure = { null },
        )
        val quality = Quality(
            info = QualityDescriptor(
                resolution = Resolution(480, 270),
                label = "480p",
                bitrate = null,
                mimeType = "video/mp4",
            ),
            url = Res.getUri("files/mock_film.mp4"),
            downloadUrl = null,
        )
        val subtitles = persistentListOf(
            Subtitle(
                language = LanguageCode("en"),
                languageDisplayName = "English",
                url = Res.getUri("files/mock_film.srt"),
                downloadUrl = null,
                format = SubtitleFormat.SRT,
            ),
            Subtitle(
                language = LanguageCode("pl"),
                languageDisplayName = "Polski",
                url = Res.getUri("files/mock_film_pl.srt"),
                downloadUrl = null,
                format = SubtitleFormat.SRT,
            ),
        )
        return Outcome.Success(
            VideoSource(
                mediaId = mediaId,
                streamUrl = Res.getUri("files/mock_film.mp4"),
                selectedQuality = quality,
                availableQualities = persistentListOf(quality),
                availableSubtitles = subtitles,
                durationSeconds = MOCK_FILM_DURATION_SECONDS,
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
