package net.subsloth.core.model

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import net.subsloth.core.model.identifier.RegionCode
import kotlin.time.Instant

/**
 * Describes whether media content is currently playable or downloadable.
 */
sealed interface Availability {
    /** Available now for streaming and optional download. */
    data object Available : Availability

    /** No longer available on the service. */
    data object Expired : Availability

    /** Scheduled for future release; not yet playable. */
    sealed interface Upcoming : Availability {
        /** Release date is unknown. */
        data object UnknownDate : Upcoming

        /** Scheduled for release at the given instant. */
        data class At(val availableAtEpochSeconds: Instant) : Upcoming
    }

    /** Available only in specific geographic regions. */
    sealed interface GeoRestricted : Availability {
        /** Restriction applies but region list is unknown. */
        data object UnknownRegions : GeoRestricted

        /** Restricted to the specified set of regions. */
        data class Known(val allowedRegions: ImmutableSet<RegionCode> = persistentSetOf()) : GeoRestricted
    }
}
