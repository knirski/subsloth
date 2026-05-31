package net.subsloth.core.domain.policy

import net.subsloth.core.model.progress.PlaybackProgress

/**
 * Pure policies for playback resume behavior.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object ResumePolicy {
    /** Progress below this threshold (in seconds) is ignored for resume. */
    private const val MIN_RESUME_THRESHOLD_SECONDS: Long = 30

    /**
     * Progress at or above this fraction of known duration is treated as
     * completed for resume purposes (starts from beginning).
     */
    private const val COMPLETION_THRESHOLD: Double = 0.95

    /**
     * Returns a resumable position in seconds, or `null` if the progress
     * should be ignored (below threshold, completed, or invalid).
     *
     * Rules:
     * - Progress below [MIN_RESUME_THRESHOLD_SECONDS] is ignored.
     * - Progress at or beyond [COMPLETION_THRESHOLD] of known duration is
     *   treated as completed (starts from beginning).
     * - Unknown duration (durationSeconds <= 0) uses only the lower
     *   threshold and never infers completion from position alone.
     */
    fun resumablePosition(progress: PlaybackProgress): Long? {
        val position = progress.positionSeconds
        val duration = progress.durationSeconds

        return when {
            position < MIN_RESUME_THRESHOLD_SECONDS -> null
            duration <= 0L -> position
            position.toDouble() / duration.toDouble() >= COMPLETION_THRESHOLD -> null
            else -> position
        }
    }

    /**
     * Determines the latest resumable point between [accountProgress] and
     * [offlineProgress], applying resume thresholds independently to each.
     *
     * Returns the later valid resumable position, or `null` if neither
     * progress value qualifies for resume.
     */
    fun latestResumablePoint(accountProgress: PlaybackProgress, offlineProgress: PlaybackProgress): Long? {
        val accountPos = resumablePosition(accountProgress)
        val offlinePos = resumablePosition(offlineProgress)

        return when {
            accountPos != null && offlinePos != null -> maxOf(accountPos, offlinePos)
            accountPos != null -> accountPos
            offlinePos != null -> offlinePos
            else -> null
        }
    }
}
