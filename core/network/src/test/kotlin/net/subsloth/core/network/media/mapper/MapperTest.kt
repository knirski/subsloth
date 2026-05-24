package net.subsloth.core.network.media.mapper

import net.subsloth.core.model.Availability
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import net.subsloth.core.network.media.api.model.Episode as DtoEpisode
import net.subsloth.core.network.media.api.model.Movie as DtoMovie
import net.subsloth.core.network.media.api.model.MovieSummary as DtoMovieSummary
import net.subsloth.core.network.media.api.model.Show as DtoShow
import net.subsloth.core.network.media.api.model.ShowSummary as DtoShowSummary
import net.subsloth.core.network.media.api.model.SubtitleTrack as DtoSubtitleTrack
import net.subsloth.core.network.media.api.model.VideoQuality as DtoVideoQuality

class MapperTest {
    // ── Movie Summary ────────────────────────────────────────────────────

    @Test
    fun `mapMovieSummary maps all fields from DTO`() {
        val dto =
            DtoMovieSummary(
                id = 1,
                title = "Test Movie",
                plot = "A test movie plot",
                description = "Full description",
                imdbRating = 7.5,
                year = 2024,
                arrayGenres = listOf("Action", "Drama"),
                duration = 120,
                slug = "test-movie",
                imdbId = "tt1234567",
                backdropUrl = "https://cdn.invalid/backdrop.jpg",
                updatedAt = 1_700_000_000L,
            )

        val result = Mapper.mapMovieSummary(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo(
            Media.MediaId.Movie(
                MovieId(1),
            ),
        )
        assertThat(result.title).isEqualTo("Test Movie")
        assertThat(result.plot).isEqualTo("A test movie plot")
        assertThat(result.availability).isInstanceOf(Availability.Available::class.java)
        assertThat(result.rating).isEqualTo(7.5)
        assertThat(result.year).isEqualTo(2024)
        assertThat(result.genres).containsExactly("Action", "Drama")
        assertThat(result.durationMinutes).isEqualTo(120)
    }

    @Test
    fun `mapMovieSummary uses name when title is absent`() {
        val dto = DtoMovieSummary(id = 1, name = "Movie Name")
        val result = Mapper.mapMovieSummary(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("Movie Name")
    }

    @Test
    fun `mapMovieSummary returns null when both name and title are absent`() {
        val dto = DtoMovieSummary(id = 1)
        val result = Mapper.mapMovieSummary(dto)

        assertThat(result).isNull()
    }

    @Test
    fun `mapMovieSummary with expired availability`() {
        val dto = DtoMovieSummary(id = 1, title = "Old Movie", updatedAt = null)
        val result = Mapper.mapMovieSummary(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.availability).isInstanceOf(Availability.Expired::class.java)
    }

    // ── Movie Details ────────────────────────────────────────────────────

    @Test
    fun `mapMovieDetails maps all fields from DTO`() {
        val dto =
            DtoMovie(
                id = 1,
                title = "Test Movie Detail",
                plot = "A longer plot",
                description = "Detailed description",
                imdbRating = 8.0,
                year = 2023,
                arrayGenres = listOf("Comedy"),
                duration = 90,
                slug = "test-movie-detail",
                imdbId = "tt7654321",
                tmdbId = 12_345,
                countries = "US,UK",
                posterUrl = "https://cdn.invalid/poster.jpg",
                backdropUrl = "https://cdn.invalid/backdrop.jpg",
                updatedAt = 1_800_000_000L,
                subtitles = listOf(DtoSubtitleTrack(code = "en", language = "English", format = "vtt")),
                qualities =
                listOf(
                    DtoVideoQuality(label = "1080p", width = 1920, height = 1080),
                ),
            )

        val result = Mapper.mapMovieDetails(dto)

        assertThat(result.isSuccess).isTrue()
        val details = result.getOrThrow()
        assertThat(details.id).isEqualTo(
            Media.MediaId.Movie(
                MovieId(1),
            ),
        )
        assertThat(details.title).isEqualTo("Test Movie Detail")
        assertThat(details.countries).containsExactly("US", "UK")
        assertThat(details.qualities).hasSize(1)
        assertThat(details.subtitles).hasSize(1)
        assertThat(details.subtitles[0].language.value).isEqualTo("en")
        assertThat(details.subtitles[0].format).isEqualTo(SubtitleFormat.VTT)
    }

    @Test
    fun `mapMovieDetails fails when title is missing`() {
        val dto = DtoMovie(id = 1)
        val result = Mapper.mapMovieDetails(dto)

        assertThat(result.isFailure).isTrue()
        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(DomainResultException::class.java)
        val domainError = (error as DomainResultException).domainError
        assertThat(domainError).isInstanceOf(DecodeError.MissingFields::class.java)
        assertThat((domainError as DecodeError.MissingFields).fields).containsExactly("title")
    }

    // ── Show Summary ─────────────────────────────────────────────────────

    @Test
    fun `mapShowSummary maps ended show correctly`() {
        val dto =
            DtoShowSummary(
                id = 10,
                title = "Test Show",
                status = "ended",
                ended = true,
                imdbRating = 8.5,
                arrayGenres = listOf("Drama"),
                arrayCountries = listOf("US"),
                backdropUrl = "https://cdn.invalid/backdrop.jpg",
            )

        val result = Mapper.mapShowSummary(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.status).isEqualTo(ShowStatus.ENDED)
        assertThat(result.rating).isEqualTo(8.5)
    }

    @Test
    fun `mapShowSummary maps ongoing show`() {
        val dto = DtoShowSummary(id = 11, title = "Ongoing Show", status = "ongoing")

        val result = Mapper.mapShowSummary(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.status).isEqualTo(ShowStatus.ONGOING)
    }

    @Test
    fun `mapShowSummary defaults to unknown status`() {
        val dto = DtoShowSummary(id = 12, title = "Unknown Show")

        val result = Mapper.mapShowSummary(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.status).isEqualTo(ShowStatus.UNKNOWN)
    }

    // ── Show Details ─────────────────────────────────────────────────────

    @Test
    fun `mapShowDetails maps show with episodes into seasons`() {
        val dto =
            DtoShow(
                id = 20,
                title = "Test Show Detail",
                status = "ongoing",
                episodes =
                listOf(
                    DtoEpisode(id = 101, showId = 20, season = 1, episode = 1, title = "E1", available = true),
                    DtoEpisode(id = 102, showId = 20, season = 1, episode = 2, title = "E2", available = true),
                    DtoEpisode(id = 103, showId = 20, season = 2, episode = 1, title = "S2E1", available = true),
                ),
                arrayCountries = listOf("US"),
            )

        val result = Mapper.mapShowDetails(dto)

        assertThat(result.isSuccess).isTrue()
        val details = result.getOrThrow()
        assertThat(details.title).isEqualTo("Test Show Detail")
        assertThat(details.seasons).hasSize(2)
        assertThat(details.seasons[0].seasonNumber).isEqualTo(1)
        assertThat(details.seasons[0].episodes).hasSize(2)
        assertThat(details.seasons[1].seasonNumber).isEqualTo(2)
        assertThat(details.seasons[1].episodes).hasSize(1)
    }

    @Test
    fun `mapShowDetails fails when title is missing`() {
        val dto = DtoShow(id = 20)
        val result = Mapper.mapShowDetails(dto)

        assertThat(result.isFailure).isTrue()
        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(DomainResultException::class.java)
        val domainError = (error as DomainResultException).domainError
        assertThat(domainError).isInstanceOf(DecodeError.MissingFields::class.java)
        assertThat((domainError as DecodeError.MissingFields).fields).containsExactly("title")
    }

    // ── Episode ──────────────────────────────────────────────────────────

    @Test
    fun `mapEpisode fails when showId is missing`() {
        val dto = DtoEpisode(id = 101, title = "Orphan Episode", available = true)
        val result = Mapper.mapEpisode(dto)

        assertThat(result.isFailure).isTrue()
        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(DomainResultException::class.java)
        val domainError = (error as DomainResultException).domainError
        assertThat(domainError).isInstanceOf(DecodeError.MissingFields::class.java)
        assertThat((domainError as DecodeError.MissingFields).fields).containsExactly("show_id")
    }

    @Test
    fun `mapEpisode maps all fields from DTO`() {
        val dto =
            DtoEpisode(
                id = 101,
                showId = 20,
                season = 1,
                episode = 3,
                title = "Episode 3",
                plot = "Exciting plot",
                duration = 45,
                available = true,
                airDate = "2024-01-15",
                premiereDate = "2024-01-01",
                subtitles = listOf(DtoSubtitleTrack(code = "en", format = "srt")),
                qualities =
                listOf(
                    DtoVideoQuality(label = "720p", width = 1280, height = 720),
                ),
            )

        val result = Mapper.mapEpisode(dto)
        val episode = result.getOrThrow()

        assertThat(result.isSuccess).isTrue()
        assertThat(episode.title).isEqualTo("Episode 3")
        assertThat(episode.seasonNumber).isEqualTo(1)
        assertThat(episode.episodeNumber).isEqualTo(3)
        assertThat(episode.durationSeconds).isEqualTo(45 * 60)
        assertThat(episode.availability).isInstanceOf(Availability.Available::class.java)
        assertThat(episode.qualities).hasSize(1)
        assertThat(episode.subtitles).hasSize(1)
        assertThat(episode.airDateEpochSeconds).isNotNull()
        assertThat(episode.premiereDateEpochSeconds).isNotNull()
    }

    @Test
    fun `mapEpisode uses number when episode field is absent`() {
        val dto = DtoEpisode(id = 101, showId = 20, number = 5, title = "Ep 5", available = true)
        val result = Mapper.mapEpisode(dto)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().episodeNumber).isEqualTo(5)
    }

    @Test
    fun `mapEpisode with unavailable episode`() {
        val dto = DtoEpisode(id = 101, showId = 20, season = 1, episode = 1, title = "Upcoming", available = false)
        val result = Mapper.mapEpisode(dto)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().availability).isInstanceOf(Availability.Upcoming::class.java)
    }

    @Test
    fun `mapEpisode with null available`() {
        val dto = DtoEpisode(id = 101, showId = 20, season = 1, episode = 1, title = "Unknown", available = null)

        val result = Mapper.mapEpisode(dto)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().availability).isInstanceOf(Availability.Expired::class.java)
    }

    // ── Subtitle Tracks ──────────────────────────────────────────────────

    @Test
    fun `mapSubtitleTrack maps all fields`() {
        val dto = DtoSubtitleTrack(code = "fr", language = "French", label = "Français", format = "srt")

        val result = Mapper.mapSubtitleTrack(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.language.value).isEqualTo("fr")
        assertThat(result.languageDisplayName).isEqualTo("Français")
        assertThat(result.format).isEqualTo(SubtitleFormat.SRT)
    }

    @Test
    fun `mapSubtitleTrack falls back to lang field`() {
        val dto = DtoSubtitleTrack(lang = "de", language = "German", format = "vtt")

        val result = Mapper.mapSubtitleTrack(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.language.value).isEqualTo("de")
    }

    @Test
    fun `mapSubtitleTrack returns null when no language code`() {
        val dto = DtoSubtitleTrack(format = "srt")

        val result = Mapper.mapSubtitleTrack(dto)

        assertThat(result).isNull()
    }

    @Test
    fun `mapSubtitleTrack handles unknown format`() {
        val dto = DtoSubtitleTrack(code = "jp", language = "Japanese", format = "unknown_format")

        val result = Mapper.mapSubtitleTrack(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.format).isEqualTo(SubtitleFormat.UNKNOWN)
    }

    // ── Quality ──────────────────────────────────────────────────────────

    @Test
    fun `mapQuality maps from resolution string`() {
        val dto = DtoVideoQuality(label = "1080p", resolution = "1920x1080")

        val result = Mapper.mapQuality(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.info.resolution.width).isEqualTo(1920)
        assertThat(result.info.resolution.height).isEqualTo(1080)
        assertThat(result.url).isNull()
    }

    @Test
    fun `mapQuality uses width and height when resolution string is absent`() {
        val dto = DtoVideoQuality(label = "720p", width = 1280, height = 720)

        val result = Mapper.mapQuality(dto)

        assertThat(result).isNotNull()
        assertThat(result!!.info.resolution.width).isEqualTo(1280)
        assertThat(result.info.resolution.height).isEqualTo(720)
    }

    @Test
    fun `mapQuality returns null when resolution cannot be determined`() {
        val dto = DtoVideoQuality(label = "unknown")

        val result = Mapper.mapQuality(dto)

        assertThat(result).isNull()
    }

    // ── mapMovies / mapShows ─────────────────────────────────────────────

    @Test
    fun `mapMovies filters out items without title and reports skipped count`() {
        val dtos =
            listOf(
                DtoMovieSummary(id = 1, title = "Valid"),
                DtoMovieSummary(id = 2), // no title
                DtoMovieSummary(id = 3, name = "Name Only"),
            )

        val result = Mapper.mapMovies(dtos)

        assertThat(result.items).hasSize(2)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.total).isEqualTo(3)
    }

    @Test
    fun `mapShows maps multiple shows`() {
        val dtos =
            listOf(
                DtoShowSummary(id = 1, title = "Show A"),
                DtoShowSummary(id = 2, title = "Show B"),
            )

        val result = Mapper.mapShows(dtos)

        assertThat(result.items).hasSize(2)
        assertThat(result.skipped).isEqualTo(0)
    }

    // ── Availability ─────────────────────────────────────────────────────

    @Test
    fun `mapAvailability with non-null instant returns Available`() {
        assertThat(
            Mapper.mapAvailability(Instant.fromEpochSeconds(1_000_000L)),
        ).isInstanceOf(Availability.Available::class.java)
    }

    @Test
    fun `mapAvailability with null returns Expired`() {
        assertThat(Mapper.mapAvailability(null)).isInstanceOf(Availability.Expired::class.java)
    }

    // ── Show Status ──────────────────────────────────────────────────────

    @Test
    fun `mapShowStatus with ended flag returns ENDED`() {
        assertThat(Mapper.mapShowStatus(status = null, ended = true)).isEqualTo(ShowStatus.ENDED)
    }

    @Test
    fun `mapShowStatus with ended string returns ENDED`() {
        assertThat(Mapper.mapShowStatus(status = "ended", ended = false)).isEqualTo(ShowStatus.ENDED)
    }

    @Test
    fun `mapShowStatus with ongoing returns ONGOING`() {
        assertThat(Mapper.mapShowStatus(status = "ongoing", ended = false)).isEqualTo(ShowStatus.ONGOING)
    }

    @Test
    fun `mapShowStatus with unknown returns UNKNOWN`() {
        assertThat(Mapper.mapShowStatus(status = null, ended = false)).isEqualTo(ShowStatus.UNKNOWN)
        assertThat(Mapper.mapShowStatus(status = "cancelled", ended = false)).isEqualTo(ShowStatus.UNKNOWN)
    }
}
