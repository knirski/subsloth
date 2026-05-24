package net.subsloth.core.domain.policy

import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Quality

/**
 * Pure policy for current-playback quality fallback.
 *
 * The spec requires at most one nearest-lower quality fallback per playback
 * session. This policy tracks whether a fallback has already been used and
 * selects the best fallback quality when allowed.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object QualityFallbackPolicy {
    /**
     * Returns `true` when a quality fallback is still available for this
     * session.
     *
     * A fallback is available only when [fallbackUsed] is `false`.
     */
    fun canFallback(fallbackUsed: Boolean): Boolean = !fallbackUsed

    /**
     * Selects the nearest lower quality from [availableQualities] relative
     * to [currentResolution].
     *
     * Returns `null` when no lower quality exists or when [fallbackUsed]
     * is `true` (fallback already consumed).
     */
    fun selectFallback(
        availableQualities: List<Quality>,
        currentResolution: Resolution,
        fallbackUsed: Boolean,
    ): Quality? {
        if (fallbackUsed || availableQualities.isEmpty()) return null

        return availableQualities
            .filter { it.info.resolution.pixelCount < currentResolution.pixelCount }
            .maxByOrNull { it.info.resolution.pixelCount }
    }
}
