package net.subsloth.core.network.media

import kotlinx.serialization.json.Json
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.network.media.api.model.Movie
import net.subsloth.core.network.media.api.model.MovieListResponse
import net.subsloth.core.network.media.api.model.Show
import net.subsloth.core.network.media.api.model.ShowListResponse
import net.subsloth.core.network.media.mapper.Mapper
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Cross-module integration test connecting fixture JSON → DTO decoding →
 * mapper → domain objects.
 *
 * Validates that the full pipeline from committed fixtures through typed
 * DTOs and into domain models produces well-formed domain objects.
 */
class DomainIntegrationTest {
    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private fun loadFixture(name: String): String {
        val resource =
            javaClass.getResource("/media/$name")
                ?: error("Fixture not found: /media/$name")
        return resource.readText()
    }

    @Test
    fun `Movies fixture decodes and maps to domain`() {
        val raw = loadFixture("Movies.json")
        val response = jsonParser.decodeFromString<MovieListResponse>(raw)

        assertThat(response.movies).isNotEmpty()
        val mappingResult = Mapper.mapMovies(response.movies)

        assertThat(mappingResult.items).isNotEmpty()
        val first = mappingResult.items.first()
        assertThat(first.title).isNotNull()
        assertThat(first.id).isNotNull()
    }

    @Test
    fun `Shows fixture decodes and maps to domain`() {
        val raw = loadFixture("Shows.json")
        val response = jsonParser.decodeFromString<ShowListResponse>(raw)

        assertThat(response.shows).isNotEmpty()
        val mappingResult = Mapper.mapShows(response.shows)

        assertThat(mappingResult.items).isNotEmpty()
        val first = mappingResult.items.first()
        assertThat(first.title).isNotNull()
        assertThat(first.id).isNotNull()
    }

    @Test
    fun `MovieDetail fixture decodes and maps to MovieDetails`() {
        val raw = loadFixture("MovieDetail.json")
        val dto = jsonParser.decodeFromString<Movie>(raw)

        assertThat(dto.id).isGreaterThan(0)
        assertThat(dto.name).isNotNull()

        when (val result = Mapper.mapMovieDetails(dto)) {
            is Outcome.Success -> {
                val details = result.value
                assertThat(details.title).isNotNull()
                assertThat(details.subtitles).isNotEmpty()
            }

            is Outcome.Failure -> error("mapMovieDetails failed: ${result.error}")
        }
    }

    @Test
    fun `ShowDetail fixture decodes and maps to ShowDetails`() {
        val raw = loadFixture("ShowDetail.json")
        val dto = jsonParser.decodeFromString<Show>(raw)

        assertThat(dto.id).isGreaterThan(0)
        assertThat(dto.name).isNotNull()

        when (val result = Mapper.mapShowDetails(dto)) {
            is Outcome.Success -> {
                val details = result.value
                assertThat(details.title).isNotNull()
            }

            is Outcome.Failure -> error("mapShowDetails failed: ${result.error}")
        }
    }
}
