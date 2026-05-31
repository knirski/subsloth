package subsloth.core.network.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import subsloth.core.network.media.api.Api
import subsloth.core.network.media.api.model.Episode
import subsloth.core.network.media.api.model.Movie
import subsloth.core.network.media.api.model.MovieListResponse
import subsloth.core.network.media.api.model.Show
import subsloth.core.network.media.api.model.ShowListResponse
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates that committed fixture files decode against the typed DTOs and
 * contain no sensitive data.  Assertions are generic (non-empty, positive IDs,
 * reasonable timestamps, no forbidden hosts) — they do not pin to specific
 * IDs or record counts that change with every capture.
 */
class FixtureTest {
    private val fixtureNames =
        listOf(
            "Movies.json",
            "Shows.json",
            "MovieDetail.json",
            "ShowDetail.json",
            "EpisodeDetail.json",
        )

    private val forbiddenHosts =
        listOf(
            "example.com",
            "media.tv",
            "media-mirror.tv",
            "placehold.co",
            "subsloth.test",
        )

    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun loadFixtureText(name: String): String {
        val resource =
            javaClass.getResource("/media/$name")
                ?: error("Fixture not found: /media/$name")
        return resource.readText()
    }

    private fun fixtureStrings(name: String): List<String> {
        fun collectStrings(element: JsonElement): List<String> = when (element) {
            is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
            is JsonArray -> element.flatMap(::collectStrings)
            is JsonObject -> element.values.flatMap(::collectStrings)
        }

        return collectStrings(jsonParser.parseToJsonElement(loadFixtureText(name)))
    }

    // ── Decoding tests — shape, not specific IDs ────────────────────────

    @Test
    fun `movies json decodes into typed movie list`() {
        val response = jsonParser.decodeFromString<MovieListResponse>(loadFixtureText("Movies.json"))
        assertTrue(response.movies.isNotEmpty())
        val first = response.movies.first()
        assertTrue(first.id > 0)
        assertNotNull(first.name ?: first.title)
        if (first.imdbRating != null) {
            assertIs<Double>(first.imdbRating)
        }
    }

    @Test
    fun `shows json decodes into typed show list`() {
        val response = jsonParser.decodeFromString<ShowListResponse>(loadFixtureText("Shows.json"))
        assertTrue(response.shows.isNotEmpty())
        val first = response.shows.first()
        assertTrue(first.id > 0)
        assertNotNull(first.name ?: first.title)
        assertNotNull(first.newestVideo)
    }

    @Test
    fun `movie detail json decodes into typed movie`() {
        val movie = jsonParser.decodeFromString<Movie>(loadFixtureText("MovieDetail.json"))
        assertTrue(movie.id > 0)
        assertNotNull(movie.name)
        assertNotNull(movie.imdbRating)
        assertTrue(movie.updatedAt != null && movie.updatedAt > 0)
        assertNotNull(movie.subtitles)
        assertTrue(movie.subtitles.isNotEmpty())
    }

    @Test
    fun `show detail json decodes into flat show`() {
        val show = jsonParser.decodeFromString<Show>(loadFixtureText("ShowDetail.json"))
        assertTrue(show.id > 0)
        assertNotNull(show.name)
        assertNotNull(show.seasons)
        assertNotNull(show.episodes)
        assertTrue(show.episodes.isNotEmpty())
        assertNotNull(show.newestVideo)
    }

    @Test
    fun `episode detail json decodes into typed episode`() {
        val episode = jsonParser.decodeFromString<Episode>(loadFixtureText("EpisodeDetail.json"))
        assertTrue(episode.id > 0)
        assertNotNull(episode.name ?: episode.title)
        assertNotNull(episode.url)
        assertTrue(episode.updatedAt != null && episode.updatedAt > 0)
        assertNotNull(episode.subtitles)
        assertTrue(episode.subtitles.isNotEmpty())
        val sub = episode.subtitles.first()
        assertNotNull(sub.lang ?: sub.language ?: sub.code)
        assertNotNull(sub.url ?: sub.downloadUrl)
    }

    // ── Security / sanitisation tests ───────────────────────────────────

    @Test
    fun `all fixtures contain no comments field by name`() {
        for (name in fixtureNames) {
            val text = loadFixtureText(name).lowercase()
            assertTrue("comments" !in text)
            assertTrue("comment_count" !in text)
            assertTrue("comments_count" !in text)
        }
    }

    @Test
    fun `all fixtures use obfuscated nonexistent hosts for url values`() {
        for (name in fixtureNames) {
            val values = fixtureStrings(name)
            assertTrue(values.isNotEmpty())

            for (value in values) {
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    val host =
                        URI(value).host
                            ?: error("Missing host in URL value: $value")
                    assertTrue(host.endsWith(".invalid"))
                    forbiddenHosts.forEach { forbiddenHost ->
                        assertTrue(forbiddenHost !in host)
                    }
                }
            }
        }
    }

    // ── API contract test ───────────────────────────────────────────────

    @Test
    fun `listMovies sends expected query parameters`() = runTest {
        val capturedUrl = mutableListOf<String>()
        val mockEngine =
            MockEngine { request ->
                capturedUrl.add(request.url.toString())
                respond(
                    content = ByteReadChannel("""{"movies":[],"meta":null}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client =
            HttpClient(mockEngine) {
                install(ContentNegotiation) { json(jsonParser) }
            }
        val api = Api(client)

        api.listMovies(page = 1, perPage = 50, genre = "action")

        val url = capturedUrl.single()
        assertTrue("page=1" in url)
        assertTrue("per_page=50" in url)
        assertTrue("genre=action" in url)
    }

    @Test
    fun `listShows sends expected query parameters`() = runTest {
        val capturedUrl = mutableListOf<String>()
        val mockEngine =
            MockEngine { request ->
                capturedUrl.add(request.url.toString())
                respond(
                    content = ByteReadChannel("""{"shows":[],"meta":null}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client =
            HttpClient(mockEngine) {
                install(ContentNegotiation) { json(jsonParser) }
            }
        val api = Api(client)

        api.listShows(page = 2, sort = "year", country = "US")

        val url = capturedUrl.single()
        assertTrue("page=2" in url)
        assertTrue("sort=year" in url)
        assertTrue("country=US" in url)
    }
}
