package net.subsloth.core.domain.port

import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.SyncError

/**
 * Port for synchronizing the catalog with the remote API.
 *
 * Implementations fetch fresh data from the API, update the local cache,
 * and return typed [SyncError]s on failure via the [Outcome] wrapper.
 */
interface CatalogSyncPort {
    /**
     * Fetches fresh catalog from the API and updates the local cache.
     * Returns [Outcome.Success] on success, or [Outcome.Failure] with a
     * typed [SyncError] on failure.
     */
    suspend fun sync(): Outcome<Unit>

    /**
     * Whether the local cache is older than the staleness threshold.
     */
    suspend fun isStale(): Boolean
}
