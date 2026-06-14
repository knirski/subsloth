package net.subsloth.core.domain.policy

/**
 * Pure policies for catalog synchronization staleness checks.
 */
object CatalogSyncPolicy {
    /** Cache is considered stale after 1 hour. */
    const val STALENESS_THRESHOLD_MS = 60 * 60 * 1000L

    /**
     * Returns `true` if the cache should be considered stale.
     *
     * The cache is stale if:
     * - [lastSyncTimestamp] is `null` (never synced), or
     * - The elapsed time since [lastSyncTimestamp] exceeds [STALENESS_THRESHOLD_MS].
     */
    fun isStale(lastSyncTimestamp: Long?, nowEpochMs: Long): Boolean =
        lastSyncTimestamp == null || (nowEpochMs - lastSyncTimestamp) > STALENESS_THRESHOLD_MS
}
