package subsloth.core.network.media.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieSummary(
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
    @SerialName("imdb_id") val imdbId: String? = null,
    val rating: Double? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    val year: Int? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    val genres: String? = null,
    @SerialName("array_genres") val arrayGenres: List<String>? = null,
    val countries: String? = null,
    val duration: Int? = null,
    val resolution: String? = null,
    val subtitles: List<SubtitleTrack>? = null,
    val desc: String? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class Movie(
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
    @SerialName("imdb_id") val imdbId: String? = null,
    val rating: Double? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    val year: Int? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    val genres: String? = null,
    @SerialName("array_genres") val arrayGenres: List<String>? = null,
    val countries: String? = null,
    val duration: Int? = null,
    val resolution: String? = null,
    val subtitles: List<SubtitleTrack>? = null,
    val desc: String? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    val trailer: String? = null,
    @SerialName("trailer_url") val trailerUrl: String? = null,
    val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val qualities: List<VideoQuality>? = null,
    val videos: List<VideoSource>? = null,
)

@Serializable
data class MovieListResponse(val movies: List<MovieSummary>, val meta: PaginationMeta? = null)
