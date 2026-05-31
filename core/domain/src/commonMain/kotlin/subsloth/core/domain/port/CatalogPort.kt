package subsloth.core.domain.port

import subsloth.core.model.media.Media
import subsloth.core.model.media.MediaDetails

/**
 * Port for reading catalog and detail data from the network or cache.
 *
 * Implementations are provided by the Android/network shell.
 */
interface CatalogPort {
    /**
     * Returns the catalog of available movies and shows.
     */
    suspend fun listCatalog(): Result<List<Media>>

    /**
     * Returns full details for a media item.
     */
    suspend fun getDetails(id: Media.MediaId): Result<MediaDetails>
}
