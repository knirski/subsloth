package net.subsloth.core.model

import androidx.compose.runtime.Stable

/**
 * Describes whether media content is currently playable or downloadable.
 */
@Stable
sealed interface Availability {
    /** Available now for streaming and optional download. */
    data object Available : Availability

    /** Scheduled for future release; not yet playable. */
    data class Upcoming(
        val availableAtEpochSeconds: Long?,
    ) : Availability

    /** No longer available on the service. */
    data object Expired : Availability

    /** Available only in specific geographic regions. */
    data class GeoRestricted(
        val allowedRegions: List<String>?,
    ) : Availability
}
