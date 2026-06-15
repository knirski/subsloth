package net.subsloth.core.domain.policy

import net.subsloth.core.model.progress.PlaybackProgress

/**
 * Pure policies for playback completion and watched state.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object CompletionPolicy {
    /**
     * Playback reaching at least this fraction of known duration marks the
     * item as locally completed/watched.
     */
    private const val COMPLETION_THRESHOLD: Double = 0.95

    /**
     * Fraction threshold for treating a partially-played item as "watched"
     * for the purpose of library or download completion grouping.
     * Distinct from [COMPLETION_THRESHOLD] so a "continue watching" cut-off
     * can differ from a "fully completed" cut-off.
     */
    const val WATCHED_THRESHOLD: Double = 0.9

    /**
     * Returns `true` when the playback position has reached or exceeded
     * the completion threshold of the known duration.
     *
     * Unknown duration (durationSeconds <= 0) is never completed via
     * position alone and requires an explicit playback-ended event.
     */
    fun isCompleted(positionSeconds: Long, durationSeconds: Long): Boolean {
        if (durationSeconds <= 0L) return false
        return positionSeconds.toDouble() / durationSeconds.toDouble() >= COMPLETION_THRESHOLD
    }

    /**
     * Applies an explicit watched toggle, setting [PlaybackProgress.isWatched]
     * to `true`.
     */
    fun applyExplicitWatched(progress: PlaybackProgress): PlaybackProgress = progress.copy(isWatched = true)

    /**
     * Applies an explicit unwatched toggle, setting [PlaybackProgress.isWatched]
     * to `false`.
     */
    fun applyExplicitUnwatched(progress: PlaybackProgress): PlaybackProgress = progress.copy(isWatched = false)
}
