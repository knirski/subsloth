package net.subsloth.core.network.media

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.core.network.media.client.ResponseValidationException
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiLiveDriftTest {
    private val login: String = System.getenv("SUBSLOTH_LOGIN") ?: ""
    private val password: String = System.getenv("SUBSLOTH_PASSWORD") ?: ""
    private val baseUrl: String = System.getenv("SUBSLOTH_URL") ?: ""
    private val resolvedBaseUrl: String by lazy { baseUrl.ifEmpty { ClientFactory.DEFAULT_BASE_URL } }

    private val clients = mutableListOf<HttpClient>()

    private val client by lazy {
        ClientFactory
            .create(
                login = login,
                password = password,
                baseUrl = resolvedBaseUrl,
                enableHttpLogging = false,
            ).also { clients.add(it) }
    }
    private val api by lazy { Api(client) }

    @BeforeEach
    fun checkCredentials() {
        assumeTrue(
            login.isNotEmpty() && password.isNotEmpty(),
            "Live drift tests skipped: SUBSLOTH_LOGIN and SUBSLOTH_PASSWORD must be set",
        )
    }

    @BeforeEach
    @AfterEach
    fun tearDown() {
        clients.forEach { it.close() }
        clients.clear()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun assertHttpError(expectedCode: Int, block: suspend () -> Unit) {
        val error =
            try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            } ?: throw AssertionError("Expected HTTP $expectedCode but request succeeded")
        when (error) {
            is ResponseException -> assertThat(error.response.status.value).isEqualTo(expectedCode)

            else -> throw AssertionError(
                "Expected HTTP $expectedCode but got: ${error.message}",
                error,
            )
        }
    }

    private fun String?.diagnosticMessage(): String =
        if (this == null) {
            "Unknown error — check SUBSLOTH_URL ($resolvedBaseUrl) is correct and includes /api/v2/ path"
        } else if (contains("redirect") || contains("HTML") || contains("Expected JSON")) {
            "API returned unexpected response (redirect or HTML) — SUBSLOTH_URL ($resolvedBaseUrl) may point " +
                "to the web frontend instead of the Kodi API endpoint (needs /api/v2/ path). Error: $this"
        } else {
            this
        }

    @Test
    fun `connectivity check to API base URL`() = runTest {
        val probeClient = HttpClient { }
        try {
            clients.add(probeClient)
            val response = probeClient.get(resolvedBaseUrl.trimEnd('/') + "/movies")
            System.err.println(
                "[ApiLiveDriftTest] $resolvedBaseUrl -> HTTP ${response.status.value} " +
                    "Content-Type: ${response.contentType() ?: "none"} " +
                    "Body preview: ${response.bodyAsText().take(200)}",
            )
        } catch (e: Exception) {
            System.err.println("[ApiLiveDriftTest] Connectivity check failed for $resolvedBaseUrl: ${e.message}")
        }
    }

    @Test
    fun `list movies returns non-empty typed movie list`() = runTest {
        try {
            val response = api.listMovies()
            assertThat(response.movies).isNotEmpty()
            val first = response.movies.first()
            assertThat(first.id).isGreaterThan(0)
            assertThat(first.name).isNotNull()
            assertThat(first.updatedAt).isNotNull()
        } catch (e: ResponseValidationException) {
            throw AssertionError(e.message.diagnosticMessage(), e)
        }
    }

    @Test
    fun `list shows returns non-empty typed show list`() = runTest {
        try {
            val response = api.listShows()
            assertThat(response.shows).isNotEmpty()
            val first = response.shows.first()
            assertThat(first.id).isGreaterThan(0)
            assertThat(first.name).isNotNull()
            assertThat(first.newestVideo).isNotNull()
        } catch (e: ResponseValidationException) {
            throw AssertionError(e.message.diagnosticMessage(), e)
        }
    }

    @Test
    fun `movie detail with valid id returns typed movie`() = runTest {
        try {
            val firstId =
                api
                    .listMovies()
                    .movies
                    .firstOrNull()
                    ?.id
                    ?: error("No movies returned from list — check API drift")
            val movie = api.getMovie(firstId)
            assertThat(movie.id).isGreaterThan(0)
            assertThat(movie.name).isNotNull()
            assertThat(movie.updatedAt).isNotNull()
            assertThat(movie.imdbRating).isNotNull()
        } catch (e: ResponseValidationException) {
            throw AssertionError(e.message.diagnosticMessage(), e)
        }
    }

    @Test
    fun `movie detail with nonexistent id returns 404`() = runTest {
        try {
            assertHttpError(404) { api.getMovie(0) }
        } catch (e: ResponseValidationException) {
            throw AssertionError(e.message.diagnosticMessage(), e)
        }
    }

    @Test
    fun `show detail with valid id returns typed show with episodes`() = runTest {
        try {
            val firstId =
                api
                    .listShows()
                    .shows
                    .firstOrNull()
                    ?.id
                    ?: error("No shows returned from list — check API drift")
            val show = api.getShow(firstId)
            assertThat(show.id).isGreaterThan(0)
            assertThat(show.name).isNotNull()
            assertThat(show.seasons).isNotNull()
            val episodes =
                show.episodes
                    ?: error("Show detail has no flat episodes list — check OpenAPI drift")
            assertThat(episodes).isNotEmpty()
        } catch (e: ResponseValidationException) {
            throw AssertionError(e.message.diagnosticMessage(), e)
        }
    }

    @Test
    fun `episode detail with discovered id returns typed episode`() = runTest {
        try {
            val firstShowId =
                api
                    .listShows()
                    .shows
                    .firstOrNull()
                    ?.id
                    ?: error("No shows returned from list — check API drift")
            val firstEpisode =
                api
                    .getShow(firstShowId)
                    .episodes
                    ?.firstOrNull()
                    ?: error("Show detail has no flat episodes list — check OpenAPI drift")

            val episode = api.getEpisode(firstEpisode.id)
            assertThat(episode.id).isGreaterThan(0)
            assertThat(episode.name ?: episode.title).isNotNull()
            assertThat(episode.url).isNotNull()
            assertThat(episode.createdAt).isNotNull()
            assertThat(episode.updatedAt).isNotNull()
            assertThat(episode.subtitles).isNotEmpty()
            val firstSubtitle = episode.subtitles!!.first()
            assertThat(firstSubtitle.lang ?: firstSubtitle.language ?: firstSubtitle.code).isNotNull()
            assertThat(firstSubtitle.url ?: firstSubtitle.downloadUrl).isNotNull()
        } catch (e: ResponseValidationException) {
            throw AssertionError(e.message.diagnosticMessage(), e)
        }
    }

    @Test
    fun `invalid credentials return 401 unauthorized`() = runTest {
        val badClient =
            ClientFactory
                .create(
                    login = "invalid",
                    password = "credentials",
                    baseUrl = resolvedBaseUrl,
                    enableHttpLogging = false,
                ).also { clients.add(it) }
        val badApi = Api(badClient)
        try {
            assertHttpError(401) { badApi.listMovies() }
        } catch (e: ResponseValidationException) {
            throw AssertionError(e.message.diagnosticMessage(), e)
        }
    }
}
