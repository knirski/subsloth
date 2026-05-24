package net.subsloth.core.network.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.api.model.Episode
import net.subsloth.core.network.media.api.model.Movie
import net.subsloth.core.network.media.api.model.MovieListResponse
import net.subsloth.core.network.media.api.model.Show
import net.subsloth.core.network.media.api.model.ShowListResponse
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import retrofit2.http.Query
import java.net.URI

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

    private val json =
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

        return collectStrings(json.parseToJsonElement(loadFixtureText(name)))
    }

    // ── Decoding tests — shape, not specific IDs ────────────────────────

    @Test
    fun `movies json decodes into typed movie list`() {
        val response = json.decodeFromString<MovieListResponse>(loadFixtureText("Movies.json"))
        assertThat(response.movies).isNotEmpty()
        val first = response.movies.first()
        assertThat(first.id).isGreaterThan(0)
        assertThat(first.name ?: first.title).isNotNull()
        // imdb_rating is Double? — presence varies; check type only when present
        if (first.imdbRating != null) {
            assertThat(first.imdbRating).isInstanceOf(Double::class.javaObjectType)
        }
    }

    @Test
    fun `shows json decodes into typed show list`() {
        val response = json.decodeFromString<ShowListResponse>(loadFixtureText("Shows.json"))
        assertThat(response.shows).isNotEmpty()
        val first = response.shows.first()
        assertThat(first.id).isGreaterThan(0)
        assertThat(first.name ?: first.title).isNotNull()
        assertThat(first.newestVideo).isNotNull()
    }

    @Test
    fun `movie detail json decodes into typed movie`() {
        val movie = json.decodeFromString<Movie>(loadFixtureText("MovieDetail.json"))
        assertThat(movie.id).isGreaterThan(0)
        assertThat(movie.name).isNotNull()
        assertThat(movie.imdbRating).isNotNull()
        assertThat(movie.updatedAt).isGreaterThan(0)
        // Movie detail must include at least one subtitle track
        assertThat(movie.subtitles).isNotEmpty()
    }

    @Test
    fun `show detail json decodes into flat show`() {
        val show = json.decodeFromString<Show>(loadFixtureText("ShowDetail.json"))
        assertThat(show.id).isGreaterThan(0)
        assertThat(show.name).isNotNull()
        // seasons is Int? — real API returns the number of seasons
        assertThat(show.seasons).isNotNull()
        // episodes is the flat episode list (Kodi plugin uses this)
        assertThat(show.episodes).isNotEmpty()
        assertThat(show.newestVideo).isNotNull()
    }

    @Test
    fun `episode detail json decodes into typed episode`() {
        val episode = json.decodeFromString<Episode>(loadFixtureText("EpisodeDetail.json"))
        assertThat(episode.id).isGreaterThan(0)
        assertThat(episode.name ?: episode.title).isNotNull()
        assertThat(episode.url).isNotNull()
        // updated_at is the reliable timestamp; created_at may be absent
        assertThat(episode.updatedAt).isGreaterThan(0)
        assertThat(episode.subtitles).isNotEmpty()
        val sub = episode.subtitles!!.first()
        assertThat(sub.lang ?: sub.language ?: sub.code).isNotNull()
        assertThat(sub.url ?: sub.downloadUrl).isNotNull()
    }

    // ── Security / sanitisation tests ───────────────────────────────────

    @Test
    fun `all fixtures contain no comments field by name`() {
        for (name in fixtureNames) {
            val text = loadFixtureText(name).lowercase()
            assertThat(text).doesNotContain("comments")
            assertThat(text).doesNotContain("comment_count")
            assertThat(text).doesNotContain("comments_count")
        }
    }

    @Test
    fun `all fixtures use obfuscated nonexistent hosts for url values`() {
        for (name in fixtureNames) {
            val values = fixtureStrings(name)
            assertThat(values).isNotEmpty()
            // Empty strings are valid API data (e.g. missing kinopoisk_id)

            for (value in values) {
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    val host =
                        URI(value).host
                            ?: error("Missing host in URL value: $value")
                    assertThat(host).endsWith(".invalid")
                    forbiddenHosts.forEach { forbiddenHost ->
                        assertThat(host).doesNotContain(forbiddenHost)
                    }
                }
            }
        }
    }

    // ── API contract test ───────────────────────────────────────────────

    @Test
    fun `list endpoints expose catalog query parameters`() {
        val listMovies =
            Api::class.java
                .declaredMethods
                .single { it.name == "listMovies" }
        val listShows =
            Api::class.java
                .declaredMethods
                .single { it.name == "listShows" }
        val listMoviesQueryNames =
            listMovies.parameterAnnotations
                .dropLast(1)
                .map { it.filterIsInstance<Query>().single().value }
        val listShowsQueryNames =
            listShows.parameterAnnotations
                .dropLast(1)
                .map { it.filterIsInstance<Query>().single().value }

        assertThat(listMoviesQueryNames)
            .containsExactly(
                "page",
                "per_page",
                "q",
                "sort",
                "genre",
                "country",
                "subtitles",
                "year_from",
                "year_to",
                "rating_from",
                "rating_to",
            )
        assertThat(listShowsQueryNames)
            .containsExactly(
                "page",
                "per_page",
                "q",
                "sort",
                "genre",
                "country",
                "subtitles",
                "year_from",
                "year_to",
                "rating_from",
                "rating_to",
            )
    }
}
