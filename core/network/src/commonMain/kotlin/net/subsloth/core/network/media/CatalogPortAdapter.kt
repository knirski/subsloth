package net.subsloth.core.network.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import net.subsloth.core.domain.port.CatalogCachePort
import net.subsloth.core.domain.port.CatalogPort
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.network.error.NetworkErrorClassifier
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.mapper.Mapper

/**
 * Production implementation of [CatalogPort].
 *
 * Delegates cache reads to [CatalogCachePort] for [listCatalog] and
 * fetches full details from the remote API via [Api] for [getDetails].
 */
class CatalogPortAdapter(private val catalogCache: CatalogCachePort, private val api: Api) : CatalogPort {

    override suspend fun listCatalog(): Outcome<List<Media>> = try {
        val items = catalogCache.catalogItems("movie")
            .combine(catalogCache.catalogItems("show")) { movies, shows ->
                buildList {
                    addAll(movies)
                    addAll(shows)
                }
            }
            .first()
        Outcome.Success(items)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(NetworkErrorClassifier.classifyToNetwork(e))
    }

    override suspend fun getDetails(id: Media.MediaId): Outcome<MediaDetails> = when (id) {
        is Media.MediaId.Movie -> fetchMovieDetails(id.value.value)
        is Media.MediaId.Show -> fetchShowDetails(id.value.value)
        is Media.MediaId.Episode -> error("Episode details not available via CatalogPort")
    }

    private suspend fun fetchMovieDetails(movieId: Int): Outcome<MediaDetails> = try {
        when (val result = Mapper.mapMovieDetails(api.getMovie(movieId))) {
            is Outcome.Success -> Outcome.Success(result.value)
            is Outcome.Failure -> Outcome.Failure(result.error)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(NetworkErrorClassifier.classifyToNetwork(e))
    }

    private suspend fun fetchShowDetails(showId: Int): Outcome<MediaDetails> = try {
        when (val result = Mapper.mapShowDetails(api.getShow(showId))) {
            is Outcome.Success -> Outcome.Success(result.value)
            is Outcome.Failure -> Outcome.Failure(result.error)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(NetworkErrorClassifier.classifyToNetwork(e))
    }
}
