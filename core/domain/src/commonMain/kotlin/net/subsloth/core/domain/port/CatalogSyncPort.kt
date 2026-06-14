package net.subsloth.core.domain.port

import net.subsloth.core.model.error.SyncError

/**
 * Port for synchronizing the catalog with the remote API.
 *
 * Implementations fetch fresh data from the API, update the local cache,
 * and return typed errors on failure.
 */
interface CatalogSyncPort {
    /**
     * Fetches fresh catalog from the API and updates the local cache.
     * Returns [Result.success] on success, or [Result.failure] with a [SyncError] on failure.
     */
    suspend fun sync(): Result<Unit>

    /**
     * Whether the local cache is older than the staleness threshold.
     */
    suspend fun isStale(): Boolean
}
