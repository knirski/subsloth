package net.subsloth.core.network.media

import kotlinx.coroutines.runBlocking
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException

class ApiLiveDriftTest {
    private val login: String = System.getenv("SUBSLOTH_LOGIN") ?: ""
    private val password: String = System.getenv("SUBSLOTH_PASSWORD") ?: ""

    private val api by lazy {
        ClientFactory.create(
            login = login,
            password = password,
            enableHttpLogging = false,
        )
    }

    @BeforeEach
    fun checkCredentials() {
        assumeTrue(
            login.isNotEmpty() && password.isNotEmpty(),
            "Live drift tests skipped: SUBSLOTH_LOGIN and SUBSLOTH_PASSWORD must be set",
        )
    }

    private suspend fun assertHttpError(expectedCode: Int, block: suspend () -> Unit) {
        val error =
            runCatching { block() }.exceptionOrNull()
                ?: throw AssertionError("Expected HTTP $expectedCode but request succeeded")
        when (error) {
            is HttpException -> assertThat(error.code()).isEqualTo(expectedCode)
            else -> throw AssertionError(
                "Expected HTTP $expectedCode but got: ${error.message}",
                error,
            )
        }
    }

    @Test
    fun `list movies returns non-empty typed movie list`() = runBlocking {
        val response = api.listMovies()
        assertThat(response.movies).isNotEmpty()
        val first = response.movies.first()
        assertThat(first.id).isGreaterThan(0)
        assertThat(first.name).isNotNull()
        assertThat(first.updatedAt).isNotNull()
    }

    @Test
    fun `list shows returns non-empty typed show list`() = runBlocking {
        val response = api.listShows()
        assertThat(response.shows).isNotEmpty()
        val first = response.shows.first()
        assertThat(first.id).isGreaterThan(0)
        assertThat(first.name).isNotNull()
        assertThat(first.newestVideo).isNotNull()
    }

    @Test
    fun `movie detail with valid id returns typed movie`() = runBlocking {
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
    }

    @Test
    fun `movie detail with nonexistent id returns 404`() = runBlocking {
        assertHttpError(404) { api.getMovie(0) }
    }

    @Test
    fun `show detail with valid id returns typed show with episodes`() = runBlocking {
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
    }

    @Test
    fun `episode detail with discovered id returns typed episode`() = runBlocking {
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
    }

    @Test
    fun `invalid credentials return 401 unauthorized`() = runBlocking {
        val badApi =
            ClientFactory.create(
                login = "invalid",
                password = "credentials",
                enableHttpLogging = false,
            )
        assertHttpError(401) { badApi.listMovies() }
    }
}
