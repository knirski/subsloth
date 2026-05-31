package subsloth.core.domain.policy

import subsloth.core.model.identifier.LanguageCode
import subsloth.core.model.media.Subtitle

/**
 * Pure policies for subtitle selection and fallback.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object SubtitlePolicy {
    /** Default preferred subtitle language. */
    private val DEFAULT_LANGUAGE: LanguageCode = LanguageCode("en")

    /**
     * Selects the default subtitle from the available tracks.
     *
     * - Prefers [preferredLanguage] if available.
     * - Falls back to English ("en") if preferred is unavailable.
     * - Falls back to the first available track if both are absent.
     * - Returns `null` when [subtitles] is empty.
     */
    fun selectDefault(subtitles: List<Subtitle>, preferredLanguage: LanguageCode = DEFAULT_LANGUAGE): Subtitle? {
        if (subtitles.isEmpty()) return null
        return subtitles.find { it.language == preferredLanguage }
            ?: subtitles.find { it.language == DEFAULT_LANGUAGE }
            ?: subtitles.first()
    }

    /**
     * Returns `null` to represent the "subtitles disabled" state.
     *
     * Subtitle disabled is a deliberate policy choice meaning no subtitle
     * is selected, as opposed to an absence of available tracks.
     */
    fun selectDisabled(): Subtitle? = null

    /**
     * Finds the best subtitle fallback when the [preferred] language is
     * unavailable.
     *
     * - Returns the exact match if available.
     * - Falls back to the first available track.
     * - Returns `null` when [subtitles] is empty.
     */
    fun fallback(subtitles: List<Subtitle>, preferred: LanguageCode): Subtitle? {
        if (subtitles.isEmpty()) return null
        return subtitles.find { it.language == preferred }
            ?: subtitles.first()
    }
}
