@file:Suppress("TooManyFunctions", "ReturnCount")

package net.subsloth.core.network.media.mapper

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import net.subsloth.core.model.Availability
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.getOrNull
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
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

object Mapper {
    // ── Movie List → Domain Media List ───────────────────────────────────

    fun mapMovies(dtos: List<DtoMovieSummary>): MappingResult<Media> = mapList(dtos, ::mapMovieSummary)

    // ── Movie Summary → Domain MovieSummary ──────────────────────────────

    fun mapMovieSummary(dto: DtoMovieSummary): MovieSummary? {
        val title = dto.title ?: dto.name ?: return null
        return MovieSummary(
            id = Media.MediaId.Movie(MovieId(dto.id)),
            title = title,
            plot = dto.plot ?: dto.description,
            availability = mapAvailability(dto.updatedAt?.let { Instant.fromEpochSeconds(it) }),
            rating = dto.imdbRating ?: dto.rating,
            year = dto.year ?: dto.releaseYear,
            genres = fallbackList(dto.arrayGenres, dto.genres),
            durationMinutes = dto.duration,
            slug = dto.slug,
            imdbId = dto.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
            backdropUrl = dto.backdropUrl ?: dto.backdrop,
            updatedAtEpochSeconds = dto.updatedAt?.let { Instant.fromEpochSeconds(it) },
        )
    }

    // ── Movie Detail → Domain MovieDetails ───────────────────────────────

    fun mapMovieDetails(dto: DtoMovie): Outcome<MovieDetails> {
        val title =
            dto.title ?: dto.name
                ?: return Outcome.Failure(DecodeError.MissingFields(listOf("title")))
        return Outcome.Success(
            MovieDetails(
                id = Media.MediaId.Movie(MovieId(dto.id)),
                title = title,
                plot = dto.plot,
                description = dto.description ?: dto.desc,
                availability = mapAvailability(dto.updatedAt?.let { Instant.fromEpochSeconds(it) }),
                rating = dto.imdbRating ?: dto.rating,
                year = dto.year ?: dto.releaseYear,
                genres = fallbackList(dto.arrayGenres, dto.genres),
                durationMinutes = dto.duration,
                qualities = mapQualities(dto.qualities),
                subtitles = mapSubtitleTracks(dto.subtitles),
                slug = dto.slug,
                imdbId = dto.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
                tmdbId = dto.tmdbId?.let { ExternalId(it.toString(), ExternalIdSource.TMDB) },
                countries =
                parseCommaSeparated(dto.countries),
                posterUrl = dto.posterUrl ?: dto.poster,
                backdropUrl = dto.backdropUrl ?: dto.backdrop,
            ),
        )
    }

    // ── Show List → Domain Media List ────────────────────────────────────

    fun mapShows(dtos: List<DtoShowSummary>): MappingResult<Media> = mapList(dtos, ::mapShowSummary)

    // ── Show Summary → Domain ShowSummary ────────────────────────────────

    fun mapShowSummary(dto: DtoShowSummary): ShowSummary? {
        val title = dto.title ?: dto.name ?: return null
        return ShowSummary(
            id = Media.MediaId.Show(ShowId(dto.id)),
            title = title,
            plot = dto.plot ?: dto.description,
            availability = mapAvailability(dto.newestVideo?.let { Instant.fromEpochSeconds(it) }),
            rating = dto.imdbRating,
            year = (dto.year ?: dto.releaseYear)?.toIntOrNull(),
            genres = fallbackList(dto.arrayGenres, dto.genres),
            durationMinutes = dto.duration ?: dto.length,
            slug = dto.slug,
            imdbId = dto.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
            backdropUrl = dto.backdropUrl ?: dto.backdrop ?: dto.fanart,
            status = mapShowStatus(dto.status, dto.ended),
            countries = fallbackList(dto.arrayCountries, dto.countries),
            newestVideoEpochSeconds = dto.newestVideo?.let { Instant.fromEpochSeconds(it) },
        )
    }

    // ── Show Detail → Domain ShowDetails ─────────────────────────────────

    fun mapShowDetails(dto: DtoShow): Outcome<ShowDetails> {
        val title =
            dto.title ?: dto.name
                ?: return Outcome.Failure(DecodeError.MissingFields(listOf("title")))

        val episodes: List<DomainEpisode> =
            dto.episodes
                ?.mapNotNull { mapEpisode(it).getOrNull() }
                .orEmpty()
        val seasons = groupEpisodesBySeason(episodes)

        return Outcome.Success(
            ShowDetails(
                id = Media.MediaId.Show(ShowId(dto.id)),
                title = title,
                plot = dto.plot,
                description = dto.description,
                availability = mapAvailability(dto.newestVideo?.let { Instant.fromEpochSeconds(it) }),
                rating = dto.imdbRating,
                year = (dto.year ?: dto.releaseYear)?.toIntOrNull(),
                genres = fallbackList(dto.arrayGenres, dto.genres),
                durationMinutes = dto.duration ?: dto.length,
                qualities = persistentListOf(),
                subtitles = extractShowSubtitles(episodes),
                slug = dto.slug,
                imdbId = dto.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
                tmdbId = dto.tmdbId?.let { ExternalId(it.toString(), ExternalIdSource.TMDB) },
                countries = fallbackList(dto.arrayCountries, dto.countries),
                posterUrl = dto.posterUrl ?: dto.poster,
                backdropUrl = dto.backdropUrl ?: dto.backdrop ?: dto.fanart,
                status = mapShowStatus(dto.status, dto.ended),
                popularity = dto.popularity ?: dto.userPopularity,
                seasons = seasons,
            ),
        )
    }

    // ── Episode → Domain Episode ─────────────────────────────────────────

    fun mapEpisode(dto: DtoEpisode): Outcome<DomainEpisode> {
        val showId =
            dto.showId
                ?: return Outcome.Failure(DecodeError.MissingFields(listOf("show_id")))
        return Outcome.Success(
            DomainEpisode(
                id = EpisodeId(dto.id),
                showId = ShowId(showId),
                seasonNumber = dto.season ?: 0,
                episodeNumber = dto.episode ?: dto.number ?: 0,
                title = dto.title ?: dto.name ?: "Episode ${dto.episode ?: dto.number ?: dto.id}",
                plot = dto.plot ?: dto.description,
                durationSeconds = dto.duration?.minutes?.inWholeSeconds,
                availability = mapEpisodeAvailability(dto.available),
                imdbId = null,
                qualities = mapQualities(dto.qualities),
                subtitles = mapSubtitleTracks(dto.subtitles),
                airDateEpochSeconds = dto.airDate?.toInstant() ?: dto.airdate?.toInstant(),
                premiereDateEpochSeconds = dto.premiereDate?.toInstant(),
            ),
        )
    }

    // ── Availability ─────────────────────────────────────────────────────

    fun mapAvailability(updatedAt: Instant?): Availability = if (updatedAt != null && updatedAt.epochSeconds > 0) {
        Availability.Available
    } else {
        Availability.Expired
    }

    fun mapEpisodeAvailability(available: Boolean?): Availability = when (available) {
        true -> Availability.Available
        false -> Availability.Upcoming.UnknownDate
        null -> Availability.Expired
    }

    // ── Quality Mappers ──────────────────────────────────────────────────

    fun mapQualities(dtos: List<DtoVideoQuality>?): ImmutableList<DomainQuality> =
        dtos?.mapNotNull(::mapQuality).orEmpty().toImmutableList()

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

    fun mapSubtitleTracks(dtos: List<DtoSubtitleTrack>?): ImmutableList<DomainSubtitle> =
        dtos?.mapNotNull(::mapSubtitleTrack).orEmpty().toImmutableList()

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

    private fun <T, R : Any> mapList(
        dtos: List<T>,
        mapper: (T) -> R?,
        onError: DecodeError = DecodeError.MissingFields(listOf("title")),
    ): MappingResult<R> {
        val results = mutableListOf<R>()
        val errors = mutableListOf<DecodeError>()
        for (dto in dtos) {
            val mapped = mapper(dto)
            if (mapped != null) {
                results.add(mapped)
            } else {
                errors.add(onError)
            }
        }
        return MappingResult(results.toImmutableList(), errors.toImmutableList())
    }

    private fun parseCommaSeparated(value: String?): ImmutableList<String> = value
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()
        .toImmutableList()

    private fun fallbackList(arrayField: List<String>?, field: String?): ImmutableList<String> =
        arrayField?.toImmutableList() ?: parseCommaSeparated(field)

    private fun fallbackList(arrayField: List<String>?, field: List<String>?): ImmutableList<String> =
        (arrayField ?: field.orEmpty()).toImmutableList()

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

    private fun parseSubtitleFormat(format: String?): SubtitleFormat = when {
        format.equals("srt", ignoreCase = true) -> SubtitleFormat.SRT
        format.equals("vtt", ignoreCase = true) -> SubtitleFormat.VTT
        format.equals("ass", ignoreCase = true) -> SubtitleFormat.ASS
        format.equals("ssa", ignoreCase = true) -> SubtitleFormat.SSA
        else -> SubtitleFormat.UNKNOWN
    }

    private fun groupEpisodesBySeason(episodes: List<DomainEpisode>): ImmutableList<Season> = episodes
        .groupBy { it.seasonNumber }
        .entries
        .sortedBy { it.key }
        .map { (seasonNumber, seasonEpisodes) ->
            Season(
                seasonNumber = seasonNumber,
                title = "Season $seasonNumber",
                plot = null,
                episodes = seasonEpisodes.sortedBy { it.episodeNumber }.toImmutableList(),
            )
        }.toImmutableList()

    private fun extractShowSubtitles(episodes: List<DomainEpisode>): ImmutableList<DomainSubtitle> = episodes
        .flatMap { it.subtitles }
        .distinctBy { it.language }
        .sortedBy { it.language.value }
        .toImmutableList()

    private fun String?.toInstant(): Instant? {
        if (this == null) return null
        return try {
            val epochDays = LocalDate.parse(this).toEpochDays()
            Instant.fromEpochSeconds(epochDays.days.inWholeSeconds)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
