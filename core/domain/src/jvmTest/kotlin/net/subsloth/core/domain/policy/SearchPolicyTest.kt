package net.subsloth.core.domain.policy

import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class SearchPolicyTest {
    private val movie =
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "The Dark Knight",
            plot =
            "When the menace known as the Joker wreaks havoc and chaos on " +
                "the people of Gotham, Batman must accept one of the greatest " +
                "psychological and physical tests of his ability to fight injustice.",
            availability = Availability.Available,
            rating = 9.0,
            year = 2008,
            genres = persistentListOf("Action", "Crime", "Drama"),
            durationMinutes = 152,
            slug = "the-dark-knight",
            imdbId = null,
            backdropUrl = null,
        )

    private val show =
        ShowSummary(
            id = Media.MediaId.Show(value = ShowId(1)),
            title = "Breaking Bad",
            plot =
            "A high school chemistry teacher diagnosed with inoperable lung " +
                "cancer turns to manufacturing and selling methamphetamine in " +
                "order to secure his family's future.",
            availability = Availability.Available,
            rating = 9.5,
            year = 2008,
            genres = persistentListOf("Crime", "Drama", "Thriller"),
            durationMinutes = 49,
            slug = "breaking-bad",
            imdbId = null,
            backdropUrl = null,
            status = ShowStatus.ENDED,
            countries = persistentListOf("US"),
        )

    @Test
    fun `search matches by title case-insensitively`() {
        assertThat(SearchPolicy.matches(movie, "dark knight")).isTrue()
        assertThat(SearchPolicy.matches(movie, "DARK KNIGHT")).isTrue()
        assertThat(SearchPolicy.matches(movie, "Dark Knight")).isTrue()
    }

    @Test
    fun `search requires all tokens to match`() {
        // "dark" matches but "xyzzy" does not — all tokens must match.
        assertThat(SearchPolicy.matches(movie, "dark xyzzy")).isFalse()
    }

    @Test
    fun `search matches when all tokens appear in title or plot`() {
        assertThat(SearchPolicy.matches(movie, "batman joker gotham")).isTrue()
    }

    @Test
    fun `search does not match unrelated title`() {
        assertThat(SearchPolicy.matches(movie, "star wars")).isFalse()
    }

    @Test
    fun `empty query matches nothing`() {
        assertThat(SearchPolicy.matches(movie, "")).isFalse()
    }

    @Test
    fun `blank query matches nothing`() {
        assertThat(SearchPolicy.matches(movie, "   ")).isFalse()
    }

    @Test
    fun `search matches show titles`() {
        assertThat(SearchPolicy.matches(show, "breaking bad")).isTrue()
        assertThat(SearchPolicy.matches(show, "breaking")).isTrue()
    }

    @Test
    fun `search matches across multiple media items`() {
        val items: List<Media> = listOf(movie, show)

        val darkResults = SearchPolicy.filter(items, "dark")
        assertThat(darkResults).hasSize(1)
        assertThat(darkResults[0].title).contains("Dark")

        val breakingResults = SearchPolicy.filter(items, "breaking")
        assertThat(breakingResults).hasSize(1)
        assertThat(breakingResults[0].title).contains("Breaking")
    }

    @Test
    fun `search returns empty when no items match`() {
        val items: List<Media> = listOf(movie, show)
        val results = SearchPolicy.filter(items, "nonexistent")
        assertThat(results).isEmpty()
    }

    @Test
    fun `search with empty query returns empty results`() {
        val items: List<Media> = listOf(movie, show)
        val results = SearchPolicy.filter(items, "")
        assertThat(results).isEmpty()
    }
}
