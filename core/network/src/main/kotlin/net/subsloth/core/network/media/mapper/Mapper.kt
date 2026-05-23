@file:Suppress("TooManyFunctions", "ReturnCount")

package net.subsloth.core.network.media.mapper

import net.subsloth.core.model.Availability
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ExternalIdSource
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.media.Episode as DomainEpisode
import net.subsloth.core.model.media.Quality as DomainQuality
import net.subsloth.core.model.media.Subtitle as DomainSubtitle
import net.subsloth.core.network.media.api.model.Episode as DtoEpisode
import net.subsloth.core.network.media.api.model.Movie as DtoMovie
import net.subsloth.core.network.media.api.model.MovieSummary as DtoMovieSummary
import net.subsloth.core.network.media.api.model.Show as DtoShow
import net.subsloth.core.network.media.api.model.ShowSummary as DtoShowSummary
import net.subsloth.core.network.media.api.model.SubtitleTrack as DtoSubtitleTrack
import net.subsloth.core.network.media.api.model.VideoQuality as DtoVideoQuality

/**
 * Mapper from Media API DTOs to stable domain models.
 *
 * All mapping functions are pure: they accept DTOs and return either
 * domain models or typed errors via [Result]. Ephemeral URLs (stream,
 * download, subtitle) are carried through to domain types that explicitly
 * allow them ([DomainQuality], [DomainSubtitle]), and excluded from
 * persistent domain records ([QualityDescriptor]).
 *
 * @suppress TooManyFunctions — grouping related mapping logic in one
 *   object is clearer than scattering it across multiple files.
 */
object Mapper {
    private const val SECONDS_PER_MINUTE = 60L

    // ── Movie List → Domain Media List ───────────────────────────────────

    /**
     * Maps a list of movie summaries from the catalog endpoint to domain
     * [Media] items. Items that fail to map are counted in [MappingResult.skipped]
     * so callers can surface partial-failure information.
     */
    fun mapMovies(dtos: List<DtoMovieSummary>): MappingResult<Media> {
        val results = dtos.mapNotNull(::mapMovieSummary)
        return MappingResult(results, dtos.size - results.size)
    }

    // ── Movie Summary → Domain MovieSummary ──────────────────────────────

    fun mapMovieSummary(dto: DtoMovieSummary): MovieSummary? {
        val title = dto.title ?: dto.name ?: return null
        return MovieSummary(
            id = Media.MediaId.Movie(MovieId(dto.id)),
            title = title,
            plot = dto.plot ?: dto.description,
            availability = mapAvailability(dto.updatedAt),
            rating = dto.imdbRating ?: dto.rating,
            year = dto.year ?: dto.releaseYear,
            genres = dto.arrayGenres ?: parseGenres(dto.genres),
            durationMinutes = dto.duration,
            slug = dto.slug,
            imdbId = dto.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
            backdropUrl = dto.backdropUrl ?: dto.backdrop,
            updatedAtEpochSeconds = dto.updatedAt,
        )
    }

    // ── Movie Detail → Domain MovieDetails ───────────────────────────────

    fun mapMovieDetails(dto: DtoMovie): Result<MovieDetails> {
        val title =
            dto.title ?: dto.name
                ?: return Result.failure(DomainResultException(DecodeError.MissingFields(listOf("title"))))
        return Result.success(
            MovieDetails(
                id = Media.MediaId.Movie(MovieId(dto.id)),
                title = title,
                plot = dto.plot,
                description = dto.description ?: dto.desc,
                availability = mapAvailability(dto.updatedAt),
                rating = dto.imdbRating ?: dto.rating,
                year = dto.year ?: dto.releaseYear,
                genres = dto.arrayGenres ?: parseGenres(dto.genres),
                durationMinutes = dto.duration,
                qualities = mapQualities(dto.qualities),
                subtitles = mapSubtitleTracks(dto.subtitles),
                slug = dto.slug,
                imdbId = dto.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
                tmdbId = dto.tmdbId?.let { ExternalId(it.toString(), ExternalIdSource.TMDB) },
                countries =
                dto.countries
                    ?.split(",")
                    ?.map(String::trim)
                    .orEmpty(),
                posterUrl = dto.posterUrl ?: dto.poster,
                backdropUrl = dto.backdropUrl ?: dto.backdrop,
            ),
        )
    }

    // ── Show List → Domain Media List ────────────────────────────────────

    /**
     * Maps a list of show summaries from the catalog endpoint to domain
     * [Media] items. Items that fail to map are counted in [MappingResult.skipped]
     * so callers can surface partial-failure information.
     */
    fun mapShows(dtos: List<DtoShowSummary>): MappingResult<Media> {
        val results = dtos.mapNotNull(::mapShowSummary)
        return MappingResult(results, dtos.size - results.size)
    }

    // ── Show Summary → Domain ShowSummary ────────────────────────────────

    fun mapShowSummary(dto: DtoShowSummary): ShowSummary? {
        val title = dto.title ?: dto.name ?: return null
        return ShowSummary(
            id = Media.MediaId.Show(ShowId(dto.id)),
            title = title,
            plot = dto.plot ?: dto.description,
            availability = mapAvailability(dto.newestVideo),
            rating = dto.imdbRating,
            year = (dto.year ?: dto.releaseYear)?.toIntOrNull(),
            genres = dto.arrayGenres ?: dto.genres.orEmpty(),
            durationMinutes = dto.duration ?: dto.length,
            slug = dto.slug,
            imdbId = dto.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
            backdropUrl = dto.backdropUrl ?: dto.backdrop ?: dto.fanart,
            status = mapShowStatus(dto.status, dto.ended),
            countries = dto.arrayCountries ?: dto.countries.orEmpty(),
            newestVideoEpochSeconds = dto.newestVideo,
        )
    }

    // ── Show Detail → Domain ShowDetails ─────────────────────────────────

    fun mapShowDetails(dto: DtoShow): Result<ShowDetails> {
        val title =
            dto.title ?: dto.name
                ?: return Result.failure(DomainResultException(DecodeError.MissingFields(listOf("title"))))

        val episodes =
            dto.episodes
                ?.mapNotNull { mapEpisode(it).getOrNull() }
                .orEmpty()
        val seasons = groupEpisodesBySeason(episodes)

        return Result.success(
            ShowDetails(
                id = Media.MediaId.Show(ShowId(dto.id)),
                title = title,
                plot = dto.plot,
                description = dto.description,
                availability = mapAvailability(dto.newestVideo),
                rating = dto.imdbRating,
                year = (dto.year ?: dto.releaseYear)?.toIntOrNull(),
                genres = dto.arrayGenres ?: dto.genres.orEmpty(),
                durationMinutes = dto.duration ?: dto.length,
                qualities = emptyList(),
                subtitles = extractShowSubtitles(episodes),
                slug = dto.slug,
                imdbId = dto.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
                tmdbId = dto.tmdbId?.let { ExternalId(it.toString(), ExternalIdSource.TMDB) },
                countries = dto.arrayCountries ?: dto.countries.orEmpty(),
                posterUrl = dto.posterUrl ?: dto.poster,
                backdropUrl = dto.backdropUrl ?: dto.backdrop ?: dto.fanart,
                status = mapShowStatus(dto.status, dto.ended),
                popularity = dto.popularity ?: dto.userPopularity,
                seasons = seasons,
            ),
        )
    }

    // ── Episode → Domain Episode ─────────────────────────────────────────

    fun mapEpisode(dto: DtoEpisode): Result<DomainEpisode> {
        val showId =
            dto.showId
                ?: return Result.failure(DomainResultException(DecodeError.MissingFields(listOf("show_id"))))
        return Result.success(
            DomainEpisode(
                id = EpisodeId(dto.id),
                showId = ShowId(showId),
                seasonNumber = dto.season ?: 0,
                episodeNumber = dto.episode ?: dto.number ?: 0,
                title = dto.title ?: dto.name ?: "Episode ${dto.episode ?: dto.number ?: dto.id}",
                plot = dto.plot ?: dto.description,
                durationSeconds = dto.duration?.toLong()?.times(SECONDS_PER_MINUTE),
                availability = mapEpisodeAvailability(dto.available),
                imdbId = null,
                qualities = mapQualities(dto.qualities),
                subtitles = mapSubtitleTracks(dto.subtitles),
                airDateEpochSeconds = dto.airDate?.toEpochSeconds() ?: dto.airdate?.toEpochSeconds(),
                premiereDateEpochSeconds = dto.premiereDate?.toEpochSeconds(),
            ),
        )
    }

    // ── Availability ─────────────────────────────────────────────────────

    /**
     * Maps availability based on the presence of an `updated_at` timestamp.
     * Items without an update timestamp are treated as expired/unavailable.
     */
    fun mapAvailability(updatedAtEpochSeconds: Long?): Availability =
        if (updatedAtEpochSeconds != null && updatedAtEpochSeconds > 0L) {
            Availability.Available
        } else {
            Availability.Expired
        }

    /**
     * Maps the Media `available` boolean field to domain [Availability].
     */
    fun mapEpisodeAvailability(available: Boolean?): Availability = when (available) {
        true -> Availability.Available
        false -> Availability.Upcoming(availableAtEpochSeconds = null)
        null -> Availability.Expired
    }

    // ── Quality Mappers ──────────────────────────────────────────────────

    fun mapQualities(dtos: List<DtoVideoQuality>?): List<DomainQuality> = dtos?.mapNotNull(::mapQuality).orEmpty()

    fun mapQuality(dto: DtoVideoQuality): DomainQuality? {
        val resolution = parseResolution(dto.resolution, dto.width, dto.height) ?: return null
        return DomainQuality(
            info =
            QualityDescriptor(
                resolution = resolution,
                label = dto.label ?: dto.resolution,
                bitrate = dto.bitrate,
                mimeType = null,
            ),
            url = dto.url,
            downloadUrl = null,
        )
    }

    // ── Subtitle Mappers ─────────────────────────────────────────────────

    fun mapSubtitleTracks(dtos: List<DtoSubtitleTrack>?): List<DomainSubtitle> =
        dtos?.mapNotNull(::mapSubtitleTrack).orEmpty()

    fun mapSubtitleTrack(dto: DtoSubtitleTrack): DomainSubtitle? {
        val languageCode = dto.code ?: dto.lang ?: dto.language ?: return null
        return DomainSubtitle(
            language = LanguageCode(languageCode),
            languageDisplayName = dto.label ?: dto.language,
            url = dto.url,
            downloadUrl = dto.downloadUrl,
            format = parseSubtitleFormat(dto.format),
        )
    }

    // ── Show Status ──────────────────────────────────────────────────────

    fun mapShowStatus(status: String?, ended: Boolean?): ShowStatus = when {
        ended == true -> ShowStatus.ENDED
        status.equals("ended", ignoreCase = true) -> ShowStatus.ENDED
        status.equals("ongoing", ignoreCase = true) -> ShowStatus.ONGOING
        status.equals("upcoming", ignoreCase = true) -> ShowStatus.UPCOMING
        else -> ShowStatus.UNKNOWN
    }

    // ── Private Helpers ──────────────────────────────────────────────────

    /**
     * Parses a comma-separated genres string into a list.
     */
    private fun parseGenres(genres: String?): List<String> = genres
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()

    /**
     * Parses a resolution string (e.g. "1920x1080") or uses width/height.
     */
    private fun parseResolution(resolution: String?, width: Int?, height: Int?): Resolution? =
        parseFromDimensions(width, height)
            ?: parseFromString(resolution)

    private fun parseFromDimensions(width: Int?, height: Int?): Resolution? {
        if (width == null || height == null) return null
        if (width <= 0 || height <= 0) return null
        return Resolution(width, height)
    }

    private fun parseFromString(resolution: String?): Resolution? {
        val parts = resolution?.split("x")?.takeIf { it.size == 2 } ?: return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        return if (w > 0 && h > 0) Resolution(w, h) else null
    }

    /**
     * Parses the [DtoSubtitleTrack.format] string into [SubtitleFormat].
     */
    private fun parseSubtitleFormat(format: String?): SubtitleFormat = when {
        format.equals("srt", ignoreCase = true) -> SubtitleFormat.SRT
        format.equals("vtt", ignoreCase = true) -> SubtitleFormat.VTT
        format.equals("ass", ignoreCase = true) -> SubtitleFormat.ASS
        format.equals("ssa", ignoreCase = true) -> SubtitleFormat.SSA
        else -> SubtitleFormat.UNKNOWN
    }

    /**
     * Groups flat episode list into [Season] structures.
     */
    private fun groupEpisodesBySeason(episodes: List<DomainEpisode>): List<Season> = episodes
        .groupBy { it.seasonNumber }
        .entries
        .sortedBy { it.key }
        .map { (seasonNumber, seasonEpisodes) ->
            Season(
                seasonNumber = seasonNumber,
                title = "Season $seasonNumber",
                plot = null,
                episodes = seasonEpisodes.sortedBy { it.episodeNumber },
            )
        }

    /**
     * Collects all unique subtitle tracks from a show's episodes.
     */
    private fun extractShowSubtitles(episodes: List<DomainEpisode>): List<DomainSubtitle> = episodes
        .flatMap { it.subtitles }
        .distinctBy { it.language }
        .sortedBy { it.language.value }

    // ── Extension helpers ────────────────────────────────────────────────

    /**
     * Converts a date string in "YYYY-MM-DD" format to epoch seconds using
     * [java.time.LocalDate]. Returns `null` for unparseable strings.
     */
    private fun String?.toEpochSeconds(): Long? {
        if (this == null) return null
        return try {
            java.time.LocalDate
                .parse(this)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toEpochSecond()
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }
    }
}
