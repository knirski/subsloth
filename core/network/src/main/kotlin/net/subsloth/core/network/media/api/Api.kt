package net.subsloth.core.network.media.api

import net.subsloth.core.network.media.api.model.Episode
import net.subsloth.core.network.media.api.model.Movie
import net.subsloth.core.network.media.api.model.MovieListResponse
import net.subsloth.core.network.media.api.model.Show
import net.subsloth.core.network.media.api.model.ShowListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface Api {
    @GET("movies")
    suspend fun listMovies(
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int? = null,
        @Query("q") query: String? = null,
        @Query("sort") sort: String? = null,
        @Query("genre") genre: String? = null,
        @Query("country") country: String? = null,
        @Query("subtitles") subtitles: String? = null,
        @Query("year_from") yearFrom: Int? = null,
        @Query("year_to") yearTo: Int? = null,
        @Query("rating_from") ratingFrom: Double? = null,
        @Query("rating_to") ratingTo: Double? = null,
    ): MovieListResponse

    @GET("shows")
    suspend fun listShows(
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int? = null,
        @Query("q") query: String? = null,
        @Query("sort") sort: String? = null,
        @Query("genre") genre: String? = null,
        @Query("country") country: String? = null,
        @Query("subtitles") subtitles: String? = null,
        @Query("year_from") yearFrom: Int? = null,
        @Query("year_to") yearTo: Int? = null,
        @Query("rating_from") ratingFrom: Double? = null,
        @Query("rating_to") ratingTo: Double? = null,
    ): ShowListResponse

    @GET("movies/{id}")
    suspend fun getMovie(@Path("id") id: Int): Movie

    @GET("shows/{id}")
    suspend fun getShow(@Path("id") id: Int): Show

    @GET("episodes/{id}")
    suspend fun getEpisode(@Path("id") id: Int): Episode
}
