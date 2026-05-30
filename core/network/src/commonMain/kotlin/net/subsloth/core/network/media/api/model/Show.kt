package net.subsloth.core.network.media.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShowSummary(
    val id: Int,
    val slug: String? = null,
    val name: String? = null,
    val title: String? = null,
    val plot: String? = null,
    val description: String? = null,
    val poster: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("poster_thumb") val posterThumb: String? = null,
    val backdrop: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val fanart: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    val year: String? = null,
    @SerialName("release_year") val releaseYear: String? = null,
    val genres: List<String>? = null,
    @SerialName("array_genres") val arrayGenres: List<String>? = null,
    val countries: List<String>? = null,
    @SerialName("array_countries") val arrayCountries: List<String>? = null,
    val status: String? = null,
    val ended: Boolean? = null,
    val duration: Int? = null,
    val length: Int? = null,
    @SerialName("newest_video") val newestVideo: Long? = null,
    val popularity: Int? = null,
    @SerialName("user_popularity") val userPopularity: Int? = null,
)

@Serializable
data class Episode(
    val id: Int,
    @SerialName("video_id") val videoId: Int? = null,
    @SerialName("show_id") val showId: Int? = null,
    val season: Int? = null,
    val number: Int? = null,
    val episode: Int? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("show_name") val showName: String? = null,
    val plot: String? = null,
    val description: String? = null,
    val airdate: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("premiere_date") val premiereDate: String? = null,
    val available: Boolean? = null,
    val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val resolution: String? = null,
    val qualities: List<VideoQuality>? = null,
    val subtitles: List<SubtitleTrack>? = null,
    val duration: Int? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class Show(
    val id: Int,
    val slug: String? = null,
    val name: String? = null,
    val title: String? = null,
    val plot: String? = null,
    val description: String? = null,
    val poster: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("poster_thumb") val posterThumb: String? = null,
    val backdrop: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val fanart: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    val year: String? = null,
    @SerialName("release_year") val releaseYear: String? = null,
    val genres: List<String>? = null,
    @SerialName("array_genres") val arrayGenres: List<String>? = null,
    val countries: List<String>? = null,
    @SerialName("array_countries") val arrayCountries: List<String>? = null,
    val status: String? = null,
    val ended: Boolean? = null,
    val duration: Int? = null,
    val length: Int? = null,
    @SerialName("newest_video") val newestVideo: Long? = null,
    val popularity: Int? = null,
    @SerialName("user_popularity") val userPopularity: Int? = null,
    val seasons: Int? = null,
    val episodes: List<Episode>? = null,
)

@Serializable
data class ShowListResponse(
    val shows: List<ShowSummary>,
    val meta: PaginationMeta? = null,
)
