package net.subsloth.core.domain.policy

import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Quality

/**
 * Pure policies for quality selection, fallback, and label normalization.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object QualityPolicy {
    /** Maximum resolution for phone and tablet default selection. */
    private val PHONE_TABLET_CAP: Resolution = Resolution.FULL_HD

    /**
     * Selects the default quality for a device class.
     *
     * - Phone/tablet: caps at [PHONE_TABLET_CAP] (1080p).
     * - TV: selects the highest available quality.
     *
     * Returns `null` when [qualities] is empty.
     */
    fun selectDefault(
        qualities: List<Quality>,
        isTvDevice: Boolean,
    ): Quality? {
        if (qualities.isEmpty()) return null

        return if (isTvDevice) {
            // TV: highest available
            qualities.maxByOrNull { it.info.resolution.pixelCount }
        } else {
            // Phone/tablet: highest at or below cap
            qualities
                .filter { it.info.resolution.pixelCount <= PHONE_TABLET_CAP.pixelCount }
                .maxByOrNull { it.info.resolution.pixelCount }
                // Fallback to the lowest if nothing meets the cap
                ?: qualities.minByOrNull { it.info.resolution.pixelCount }
        }
    }

    /**
     * Finds the best fallback quality when the [requested] quality is
     * unavailable. Returns the same quality if it exists in [qualities],
     * or the next lower quality if available, or `null` if no fallback
     * exists.
     */
    fun fallback(
        qualities: List<Quality>,
        requested: Resolution,
    ): Quality? {
        if (qualities.isEmpty()) return null

        // Prefer exact match
        val exact = qualities.find { it.info.resolution == requested }
        if (exact != null) return exact

        // Next lower quality
        return qualities
            .filter { it.info.resolution.pixelCount < requested.pixelCount }
            .maxByOrNull { it.info.resolution.pixelCount }
    }

    /**
     * Normalizes a quality label from the Media API.
     *
     * Known labels: `"auto"`, `"1080p"`, `"720p"`, `"480p"`, `"360p"`, `"240p"`.
     * Unknown labels pass through unchanged.
     */
    fun normalizeLabel(label: String): String =
        when (label.lowercase()) {
            "auto" -> "Auto"
            else -> label
        }

    /**
     * Applies a manual in-player quality change.
     *
     * Manual quality changes affect only the current playback session and
     * do not update the account-scoped quality preference.
     *
     * @return the unchanged [currentAccountPreference].
     */
    fun applySessionQualityChange(
        // Accepted for API consistency; in-player changes not yet persisted.
        @Suppress("UnusedParameter")
        sessionQuality: Resolution,
        currentAccountPreference: Resolution,
    ): Resolution = currentAccountPreference
}
