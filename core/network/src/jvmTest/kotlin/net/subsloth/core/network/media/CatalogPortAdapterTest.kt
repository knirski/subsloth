package net.subsloth.core.network.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.subsloth.core.domain.port.CachedCatalogItem
import net.subsloth.core.domain.port.CatalogCachePort
import net.subsloth.core.model.Availability
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ExternalIdSource
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.network.media.api.Api
import net.subsloth.testing.assertions.assertThat
import kotlin.test.Test

private class FakeCatalogCache(
    private val movies: ImmutableList<Media> = persistentListOf(),
    private val shows: ImmutableList<Media> = persistentListOf(),
) : CatalogCachePort {
    override fun catalogItems(contentType: String): Flow<List<Media>> = when (contentType) {
        "movie" -> flowOf(movies)
        "show" -> flowOf(shows)
        else -> flowOf(emptyList())
    }

    override suspend fun replaceCatalog(items: List<CachedCatalogItem>) = Unit
}

class CatalogPortAdapterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `listCatalog returns combined movies and shows from cache`() = runTest {
        val movie = MovieSummary(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "Test Movie",
            plot = null,
            availability = Availability.Available,
            rating = null,
            year = null,
            genres = persistentListOf(),
            durationMinutes = null,
            slug = null,
            imdbId = null,
            backdropUrl = null,
        )
        val show = ShowSummary(
            id = Media.MediaId.Show(ShowId(2)),
            title = "Test Show",
            plot = null,
            availability = Availability.Available,
            rating = null,
            year = null,
            genres = persistentListOf(),
            durationMinutes = null,
            slug = null,
            imdbId = null,
            backdropUrl = null,
            status = net.subsloth.core.model.media.ShowStatus.ONGOING,
            countries = persistentListOf(),
        )
        val cache = FakeCatalogCache(
            movies = persistentListOf(movie),
            shows = persistentListOf(show),
        )
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(this@CatalogPortAdapterTest.json) }
            engine { addHandler { respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound) } }
        }
        val adapter = CatalogPortAdapter(cache, Api(client))
        val result = adapter.listCatalog()
        when (result) {
            is Outcome.Success -> {
                assertThat(result.value).hasSize(2)
                assertThat(result.value.map { it.title }).containsExactly("Test Movie", "Test Show")
            }
            is Outcome.Failure -> throw AssertionError("Expected success but got failure: ${result.error}")
        }
    }

    @Test
    fun `getDetails returns MovieDetails for movie id`() = runTest {
        val movieJson = """
            {
                "id": 1, "title": "Test Movie", "plot": "A test movie",
                "imdb_rating": 7.5, "year": 2024, "duration": 120,
                "slug": "test-movie",
                "array_genres": ["Action"], "countries": "US"
            }
        """.trimIndent()
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(this@CatalogPortAdapterTest.json) }
            engine {
                addHandler { request ->
                    when {
                        request.url.encodedPath == "/movies/1" -> respond(
                            content = ByteReadChannel(movieJson),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        else -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
                    }
                }
            }
        }
        val adapter = CatalogPortAdapter(FakeCatalogCache(), Api(client))
        val result = adapter.getDetails(Media.MediaId.Movie(MovieId(1)))
        when (result) {
            is Outcome.Success -> {
                val details = result.value
                assertThat(details.title).isEqualTo("Test Movie")
                assertThat(details).isInstanceOf(MovieDetails::class.java)
            }
            is Outcome.Failure -> throw AssertionError("Expected success but got failure: ${result.error}")
        }
    }

    @Test
    fun `getDetails returns ShowDetails for show id`() = runTest {
        val showJson = """
            {
                "id": 1, "title": "Test Show", "plot": "A test show",
                "imdb_rating": 8.0, "year": "2023", "duration": 45,
                "slug": "test-show", "status": "ongoing",
                "array_genres": ["Drama"], "countries": ["US"],
                "episodes": [
                    {"id": 1, "show_id": 1, "season": 1, "episode": 1, "title": "Pilot", "available": true}
                ]
            }
        """.trimIndent()
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(this@CatalogPortAdapterTest.json) }
            engine {
                addHandler { request ->
                    when {
                        request.url.encodedPath == "/shows/1" -> respond(
                            content = ByteReadChannel(showJson),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        else -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
                    }
                }
            }
        }
        val adapter = CatalogPortAdapter(FakeCatalogCache(), Api(client))
        val result = adapter.getDetails(Media.MediaId.Show(ShowId(1)))
        when (result) {
            is Outcome.Success -> {
                val details = result.value
                assertThat(details.title).isEqualTo("Test Show")
                assertThat(details).isInstanceOf(ShowDetails::class.java)
            }
            is Outcome.Failure -> throw AssertionError("Expected success but got failure: ${result.error}")
        }
    }

    @Test
    fun `getDetails returns NetworkError on HTTP failure`() = runTest {
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(this@CatalogPortAdapterTest.json) }
            engine { addHandler { respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound) } }
        }
        val adapter = CatalogPortAdapter(FakeCatalogCache(), Api(client))
        val result = adapter.getDetails(Media.MediaId.Movie(MovieId(999)))
        when (result) {
            is Outcome.Success -> throw AssertionError("Expected failure but got success")
            is Outcome.Failure -> assertThat(result.error).isInstanceOf(NetworkError::class.java)
        }
    }

    @Test
    fun `getDetails throws for Episode id`() = runTest {
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(json) }
            engine { addHandler { respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound) } }
        }
        val adapter = CatalogPortAdapter(FakeCatalogCache(), Api(client))
        try {
            adapter.getDetails(
                Media.MediaId.Episode(net.subsloth.core.model.identifier.EpisodeId(1)),
            )
            throw AssertionError("Expected error() to throw")
        } catch (_: IllegalStateException) {
            // expected — programmer error
        }
    }
}
