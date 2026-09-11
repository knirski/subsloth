package net.subsloth.details

/**
 * The label a detail screen's primary play button should show for a
 * resumable progress fraction.
 */
sealed interface DetailPlayAction {
    /** No resumable progress: start from the beginning. */
    data object Play : DetailPlayAction

    /** Resumable progress whose truncated percentage is below 1%. */
    data object Resume : DetailPlayAction

    /** Resumable progress at [percent] (1..100). */
    data class ResumeAt(val percent: Int) : DetailPlayAction
}

/**
 * Maps stored [progressFraction] to the play button label decision.
 *
 * Null, zero, and negative fractions fall back to [DetailPlayAction.Play].
 * Positive fractions whose truncated percentage is `0` become
 * [DetailPlayAction.Resume] so a few seconds into a long movie still
 * reads "Resume" instead of "Play".
 */
fun detailPlayAction(progressFraction: Double?): DetailPlayAction {
    if (progressFraction == null || progressFraction <= 0.0) return DetailPlayAction.Play
    val percent = (progressFraction * 100).toInt()
    return if (percent > 0) DetailPlayAction.ResumeAt(percent) else DetailPlayAction.Resume
}
