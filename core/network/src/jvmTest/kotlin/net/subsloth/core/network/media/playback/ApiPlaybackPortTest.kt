package net.subsloth.core.network.media.playback

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.playback.VideoSource
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ResponseValidationPlugin
import net.subsloth.testing.assertions.assertThat
import kotlin.test.Test
import kotlin.test.assertIs

private typealias MockRoute = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

class ApiPlaybackPortTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun apiFor(handler: MockRoute): Api = Api(
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(this@ApiPlaybackPortTest.json) }
            // Mirror the production client stack: the validation plugin
            // is what surfaces 401 as ResponseValidationException
            // (classified to NetworkError.HttpError(401)).
            install(ResponseValidationPlugin)
            engine { addHandler(handler) }
        },
    )

    private fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun stubFor(path: String, status: HttpStatusCode = HttpStatusCode.OK, body: String): MockRoute =
        { request ->
            if (request.url.encodedPath == "/$path") {
                jsonResponse(body, status)
            } else {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
            }
        }

    @Test
    fun `prepareSource selects default quality from movie qualities`() = runTest {
        val api = apiFor(
            stubFor(
                "movies/1",
                body =
                """
                {
                  "id": 1,
                  "title": "Shooter",
                  "duration": 90,
                  "resolution": "HD",
                  "url": "https://media.invalid/movie.m3u8?wmsAuthSign=top",
                  "download_url": "https://media.invalid/movie.mp4",
                  "qualities": [
                    {"label": "1080p", "resolution": "1920x1080", "url": "https://media.invalid/1080.m3u8?wmsAuthSign=q1080"},
                    {"label": "480p", "resolution": "854x480", "url": "https://media.invalid/480.m3u8?wmsAuthSign=q480"}
                  ],
                  "subtitles": [{"lang": "en", "url": "https://media.invalid/en.vtt"}]
                }
                """.trimIndent(),
            ),
        )
        val port = ApiPlaybackPort(api)

        val result = port.prepareSource(Media.MediaId.Movie(MovieId(1)))

        val source = (result as Outcome.Success).value
        assertThat(source.selectedQuality.info.resolution).isEqualTo(Resolution.FULL_HD)
        assertThat(source.streamUrl).isEqualTo("https://media.invalid/1080.m3u8?wmsAuthSign=q1080")
        assertThat(source.availableQualities.size).isEqualTo(2)
        assertThat(source.availableSubtitles.size).isEqualTo(1)
        assertThat(source.durationSeconds).isEqualTo(5400L)
        assertThat(source.displayName).isEqualTo("Shooter")
    }

    @Test
    fun `prepareSource falls back to single adaptive quality from top-level url`() = runTest {
        val api = apiFor(
            stubFor(
                "movies/2",
                body =
                """
                {
                  "id": 2,
                  "name": "Shooter",
                  "duration": 95,
                  "resolution": "HD",
                  "url": "https://media.invalid/movie.m3u8?wmsAuthSign=top"
                }
                """.trimIndent(),
            ),
        )
        val port = ApiPlaybackPort(api)

        val result = port.prepareSource(Media.MediaId.Movie(MovieId(2)))

        val source = (result as Outcome.Success).value
        assertThat(source.availableQualities.size).isEqualTo(1)
        assertThat(source.selectedQuality.info.label).isEqualTo("HD")
        assertThat(source.selectedQuality.info.resolution).isEqualTo(Resolution.HD_720)
        assertThat(source.streamUrl).isEqualTo("https://media.invalid/movie.m3u8?wmsAuthSign=top")
        assertThat(source.displayName).isEqualTo("Shooter")
    }

    @Test
    fun `prepareSource resolves an episode`() = runTest {
        val api = apiFor(
            stubFor(
                "episodes/82476",
                body =
                """
                {
                  "id": 82476,
                  "show_id": 99,
                  "season": 1,
                  "number": 1,
                  "name": "Part 1: Black Fire Orchid",
                  "show_name": "The Lost Flowers of Alice Hart",
                  "available": true,
                  "resolution": "HD",
                  "duration": 55,
                  "url": "https://media.invalid/ep.m3u8?wmsAuthSign=ep"
                }
                """.trimIndent(),
            ),
        )
        val port = ApiPlaybackPort(api)

        val result = port.prepareSource(Media.MediaId.Episode(EpisodeId(82476)))

        val source = (result as Outcome.Success).value
        assertThat(source.mediaId).isEqualTo(Media.MediaId.Episode(EpisodeId(82476)))
        assertThat(source.streamUrl).isEqualTo("https://media.invalid/ep.m3u8?wmsAuthSign=ep")
        assertThat(source.durationSeconds).isEqualTo(3300L)
        assertThat(source.displayName).isEqualTo("Part 1: Black Fire Orchid")
    }

    @Test
    fun `prepareSource resolves a show to its first available episode`() = runTest {
        val showBody =
            """
            {
              "id": 5,
              "name": "Alice Hart",
              "episodes": [
                {"id": 2, "season": 1, "episode": 1, "available": false},
                {"id": 3, "season": 1, "episode": 2, "available": true}
              ]
            }
            """.trimIndent()
        val episodeBody =
            """
            {
              "id": 3,
              "show_id": 5,
              "season": 1,
              "episode": 2,
              "name": "Part 2",
              "available": true,
              "url": "https://media.invalid/ep3.m3u8?wmsAuthSign=ep3"
            }
            """.trimIndent()
        val api = apiFor { request ->
            when (request.url.encodedPath) {
                "/shows/5" -> jsonResponse(showBody)
                "/episodes/3" -> jsonResponse(episodeBody)
                else -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
            }
        }
        val port = ApiPlaybackPort(api)

        val result = port.prepareSource(Media.MediaId.Show(ShowId(5)))

        val source = (result as Outcome.Success).value
        assertThat(source.mediaId).isEqualTo(Media.MediaId.Episode(EpisodeId(3)))
        assertThat(source.streamUrl).isEqualTo("https://media.invalid/ep3.m3u8?wmsAuthSign=ep3")
    }

    @Test
    fun `prepareSource fails NotFound for a show without episodes`() = runTest {
        val api = apiFor(
            stubFor("shows/6", body = """{"id": 6, "name": "Empty Show", "episodes": []}"""),
        )
        val port = ApiPlaybackPort(api)

        val result = port.prepareSource(Media.MediaId.Show(ShowId(6)))

        val error = assertIs<Outcome.Failure>(result).error
        assertThat(error).isEqualTo(MediaError.NotFound)
    }

    @Test
    fun `prepareSource fails Unavailable when no stream url is present`() = runTest {
        val api = apiFor(
            stubFor("movies/7", body = """{"id": 7, "title": "Ghost Movie"}"""),
        )
        val port = ApiPlaybackPort(api)

        val result = port.prepareSource(Media.MediaId.Movie(MovieId(7)))

        val error = assertIs<Outcome.Failure>(result).error
        assertThat(error).isEqualTo(MediaError.Unavailable)
    }

    @Test
    fun `prepareSource classifies HTTP 401 as a network error`() = runTest {
        val api = apiFor(stubFor("movies/8", status = HttpStatusCode.Unauthorized, body = "{}"))
        val port = ApiPlaybackPort(api)

        val result = port.prepareSource(Media.MediaId.Movie(MovieId(8)))

        val error = assertIs<Outcome.Failure>(result).error
        assertThat(assertIs<NetworkError.HttpError>(error).code).isEqualTo(401)
    }

    @Test
    fun `refreshStreamUrl returns a fresh signed url`() = runTest {
        val bodies =
            listOf(
                """{"id": 9, "title": "Shooter", "resolution": "HD", "url": "https://media.invalid/movie.m3u8?wmsAuthSign=one"}""",
                """{"id": 9, "title": "Shooter", "resolution": "HD", "url": "https://media.invalid/movie.m3u8?wmsAuthSign=two"}""",
            )
        var calls = 0
        val api = apiFor { request ->
            require(request.url.encodedPath == "/movies/9")
            jsonResponse(bodies[calls++])
        }
        val port = ApiPlaybackPort(api)

        val first = port.refreshStreamUrl(Media.MediaId.Movie(MovieId(9)))
        val second = port.refreshStreamUrl(Media.MediaId.Movie(MovieId(9)))

        val firstSource = assertIs<Outcome.Success<VideoSource>>(first).value
        val secondSource = assertIs<Outcome.Success<VideoSource>>(second).value
        assertThat(firstSource.streamUrl).isNotEqualTo(secondSource.streamUrl)
        assertThat(secondSource.streamUrl).isEqualTo("https://media.invalid/movie.m3u8?wmsAuthSign=two")
    }

    @Test
    fun `play pause and seek are accepted no-ops`() = runTest {
        val api = apiFor { request -> error("Unexpected request: ${request.url.encodedPath}") }
        val port = ApiPlaybackPort(api)

        assertThat((port.play(sampleSource(), positionSeconds = 0L) as Outcome.Success).value).isEqualTo(Unit)
        assertThat((port.pause() as Outcome.Success).value).isEqualTo(Unit)
        assertThat((port.seek(42L) as Outcome.Success).value).isEqualTo(Unit)
    }

    private fun sampleSource(): VideoSource = VideoSource(
        mediaId = Media.MediaId.Movie(MovieId(1)),
        streamUrl = "https://media.invalid/movie.m3u8",
        selectedQuality =
        Quality(
            info =
            QualityDescriptor(
                resolution = Resolution.HD_720,
                label = "HD",
                bitrate = null,
                mimeType = null,
            ),
            url = "https://media.invalid/movie.m3u8",
            downloadUrl = null,
        ),
        availableQualities = persistentListOf(),
        availableSubtitles = persistentListOf(),
        durationSeconds = 0L,
    )
}
