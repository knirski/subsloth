package net.subsloth.core.model.identifier

import androidx.compose.runtime.Immutable

/**
 * An external media identifier such as IMDb ID, TMDB ID, or similar.
 *
 * The [source] discriminates which external registry the [value] belongs to.
 */
@Immutable
data class ExternalId(
    /** The identifier value (e.g. "tt1234567" for IMDb). */
    val value: String,
    /** The external registry this identifier is sourced from. */
    val source: ExternalIdSource,
)

/** Known external media registries. */
@Immutable
enum class ExternalIdSource {
    IMDb,
    TMDB,
    TVDB,
    UNKNOWN,
}
