package net.subsloth.core.domain.policy

/**
 * Pure policy for bounded stream URL refresh during a playback session.
 *
 * The spec requires at most one same-item URL refresh per playback session.
 * This policy tracks whether a refresh has already been used and prevents
 * further refresh attempts.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object StreamRefreshPolicy {
    /**
     * Returns `true` when a URL refresh is still available for this session.
     *
     * A refresh is available only when [refreshUsed] is `false`.
     * Offline playback never allows refresh regardless of [refreshUsed].
     */
    fun canRefresh(
        refreshUsed: Boolean,
        isOfflinePlayback: Boolean,
    ): Boolean = !refreshUsed && !isOfflinePlayback

    /**
     * Returns `true` — a refresh has been consumed and no further refreshes
     * are allowed in this session.
     */
    fun markRefreshUsed(): Boolean = true
}
