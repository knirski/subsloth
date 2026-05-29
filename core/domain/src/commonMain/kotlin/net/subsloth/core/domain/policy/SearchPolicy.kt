package net.subsloth.core.domain.policy

import net.subsloth.core.model.media.Media

/**
 * Pure policies for searching and filtering media items.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object SearchPolicy {
    private val WHITESPACE_REGEX: Regex = Regex("\\s+")

    /**
     * Returns `true` when the [query] tokens all match the [media] item.
     *
     * Matching is case-insensitive and matches against [title] and [plot]
     * fields separately. All query tokens must be present (AND semantics)
     * for a match. An empty or blank query never matches.
     */
    fun matches(
        media: Media,
        query: String,
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false

        val tokens = trimmed.split(WHITESPACE_REGEX)
        val titleLower = media.title.lowercase()
        val plotLower = media.plot?.lowercase()

        return tokens.all { token ->
            val lowerToken = token.lowercase()
            lowerToken in titleLower || plotLower?.contains(lowerToken) == true
        }
    }

    /**
     * Filters a list of [items] to only those that match the [query].
     *
     * Query tokens are pre-processed once — prefer this over calling
     * [matches] for each item in a loop.
     * Returns an empty list when no items match or the query is blank.
     */
    fun filter(
        items: List<Media>,
        query: String,
    ): List<Media> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val lowerTokens = trimmed.split(WHITESPACE_REGEX).map { it.lowercase() }
        return items.filter { media ->
            val titleLower = media.title.lowercase()
            val plotLower = media.plot?.lowercase()
            lowerTokens.all { token ->
                token in titleLower || plotLower?.contains(token) == true
            }
        }
    }
}
