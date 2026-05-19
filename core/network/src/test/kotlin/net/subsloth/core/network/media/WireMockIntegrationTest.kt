package net.subsloth.core.network.media

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.subsloth.core.network.media.api.model.Episode
import net.subsloth.core.network.media.api.model.Movie
import net.subsloth.core.network.media.api.model.MovieListResponse
import net.subsloth.core.network.media.api.model.Show
import net.subsloth.core.network.media.api.model.ShowListResponse
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.testing.assertions.assertThat
import net.subsloth.testing.contract.FixtureLoader
import net.subsloth.testing.contract.WireMockServerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import java.io.IOException

class WireMockIntegrationTest {
    private lateinit var server: WireMockServer

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @BeforeEach
    fun setUp() {
        server = WireMockServerFactory.create(port = 0)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    private fun api() = ClientFactory.create(
        login = "test",
        password = "test",
        baseUrl = server.baseUrl().trimEnd('/') + "/",
    )

    @Test
    fun `listMovies returns the committed fixture payload through Retrofit`(): Unit = runBlocking {
        val expected = loadFixture<MovieListResponse>("Movies.json")
        val response = api().listMovies()

        assertThat(response.movies).hasSize(expected.movies.size)
        assertThat(response.movies.map { it.id }).isEqualTo(expected.movies.map { it.id })
        assertThat(response.movies.map { it.name ?: it.title })
            .isEqualTo(expected.movies.map { it.name ?: it.title })
    }

    @Test
    fun `listShows returns the committed fixture payload through Retrofit`(): Unit = runBlocking {
        val expected = loadFixture<ShowListResponse>("Shows.json")
        val response = api().listShows()

        assertThat(response.shows).hasSize(expected.shows.size)
        assertThat(response.shows.map { it.id }).isEqualTo(expected.shows.map { it.id })
        assertThat(response.shows.map { it.name ?: it.title })
            .isEqualTo(expected.shows.map { it.name ?: it.title })
    }

    @Test
    fun `getMovie returns the committed movie detail fixture`(): Unit = runBlocking {
        val expected = loadFixture<Movie>("MovieDetail.json")
        val movie = api().getMovie(expected.id)

        assertThat(movie.id).isEqualTo(expected.id)
        assertThat(movie.name).isEqualTo(expected.name)
        assertThat(movie.imdbRating).isEqualTo(expected.imdbRating)
        assertThat(movie.updatedAt).isEqualTo(expected.updatedAt)
        assertThat(movie.subtitles?.size).isEqualTo(expected.subtitles?.size)
    }

    @Test
    fun `getShow returns the committed show detail fixture`(): Unit = runBlocking {
        val expected = loadFixture<Show>("ShowDetail.json")
        val show = api().getShow(expected.id)

        assertThat(show.id).isEqualTo(expected.id)
        assertThat(show.name).isEqualTo(expected.name)
        assertThat(show.seasons).isEqualTo(expected.seasons)
        assertThat(show.episodes?.map { it.id }).isEqualTo(expected.episodes?.map { it.id })
    }

    @Test
    fun `getEpisode returns the committed episode detail fixture`(): Unit = runBlocking {
        val expected = loadFixture<Episode>("EpisodeDetail.json")
        val episode = api().getEpisode(expected.id)

        assertThat(episode.id).isEqualTo(expected.id)
        assertThat(episode.name ?: episode.title).isEqualTo(expected.name ?: expected.title)
        assertThat(episode.updatedAt).isEqualTo(expected.updatedAt)
        assertThat(episode.subtitles?.map { it.code ?: it.lang ?: it.language })
            .isEqualTo(expected.subtitles?.map { it.code ?: it.lang ?: it.language })
    }

    @Test
    fun `listMovies with pagination params still returns fixture data`(): Unit = runBlocking {
        val expected = loadFixture<MovieListResponse>("Movies.json")
        val withParams = api().listMovies(page = 2, perPage = 50)
        val withoutParams = api().listMovies()

        assertThat(withParams.movies).hasSize(expected.movies.size)
        assertThat(withParams.movies.map { it.id }).isEqualTo(withoutParams.movies.map { it.id })
    }

    @Test
    fun `request to path with no matching stub returns 404`(): Unit = runBlocking {
        val emptyServer = WireMockServer(WireMockConfiguration().port(0))
        emptyServer.start()
        try {
            val badApi =
                ClientFactory.create(
                    login = "x",
                    password = "x",
                    baseUrl = emptyServer.baseUrl().trimEnd('/') + "/",
                )

            val error = runCatching { badApi.listMovies() }.exceptionOrNull()
            assertThat(error).isInstanceOf(HttpException::class.java)
            assertThat((error as HttpException).code()).isEqualTo(404)
        } finally {
            emptyServer.stop()
        }
    }

    @Test
    fun `request to stopped server throws IOException`(): Unit = runBlocking {
        val ephemeralServer = WireMockServer(WireMockConfiguration().port(0))
        ephemeralServer.start()
        val port = ephemeralServer.port()
        ephemeralServer.stop()

        val disconnectedApi =
            ClientFactory.create(
                login = "x",
                password = "x",
                baseUrl = "http://localhost:$port/",
            )

        val error = runCatching { disconnectedApi.listMovies() }.exceptionOrNull()
        assertThat(error).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `malformed JSON in response body throws an exception`(): Unit = runBlocking {
        val badServer = WireMockServer(WireMockConfiguration().port(0))
        badServer.start()
        try {
            badServer.stubFor(
                get(urlPathMatching("/movies"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{this is not valid json"),
                    ),
            )

            val badApi =
                ClientFactory.create(
                    login = "x",
                    password = "x",
                    baseUrl = badServer.baseUrl().trimEnd('/') + "/",
                )

            val error = runCatching { badApi.listMovies() }.exceptionOrNull()
            assertThat(error).isNotNull()
        } finally {
            badServer.stop()
        }
    }

    private inline fun <reified T> loadFixture(name: String): T =
        json.decodeFromString(FixtureLoader.loadFixtureText("/media/$name"))
}
