package net.subsloth.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlin.time.Instant

/**
 * Describes whether media content is currently playable or downloadable.
 */
@Stable
sealed interface Availability {
    /** Available now for streaming and optional download. */
    @Immutable
    data object Available : Availability

    /** Scheduled for future release; not yet playable. */
    @Immutable
    data class Upcoming(
        val availableAtEpochSeconds: Instant?,
    ) : Availability

    /** No longer available on the service. */
    @Immutable
    data object Expired : Availability

    /** Available only in specific geographic regions. */
    @Immutable
    data class GeoRestricted(
        val allowedRegions: List<String>?,
    ) : Availability
}
