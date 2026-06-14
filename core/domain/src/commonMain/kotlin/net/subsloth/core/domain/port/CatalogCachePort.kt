package net.subsloth.core.domain.port

import kotlinx.coroutines.flow.Flow
import net.subsloth.core.model.media.Media

/**
 * Port for reading and writing the local catalog cache.
 *
 * The catalog is account-agnostic — the same movies and shows exist for all users.
 * Implementations are provided by the network/database shell.
 */
interface CatalogCachePort {
    /**
     * Returns a Flow of cached catalog items for the given content type ("movie" or "show").
     */
    fun catalogItems(contentType: String): Flow<List<Media>>

    /**
     * Atomically replaces the entire catalog cache with the provided items.
     * Deletes all existing cache data first, then inserts the new items.
     */
    suspend fun replaceCatalog(items: List<CachedCatalogItem>)
}

/**
 * Intermediate representation of a catalog item for cache storage.
 * Bridges between domain Media types and database entities.
 */
data class CachedCatalogItem(
    val contentId: String,
    val contentType: String,
    val title: String,
    val plot: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: Double?,
    val durationMinutes: Int?,
    val slug: String?,
    val imdbId: String?,
    val tmdbId: Int?,
    val status: String?,
    val updatedAtEpochSeconds: Long?,
    val newestVideoEpochSeconds: Long?,
    val genres: List<String>,
    val countries: List<String>,
)
