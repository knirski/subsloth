package subsloth.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import subsloth.core.model.identifier.RegionCode
import kotlin.time.Instant

/**
 * Describes whether media content is currently playable or downloadable.
 */
@Stable
sealed interface Availability {
    /** Available now for streaming and optional download. */
    @Immutable
    data object Available : Availability

    /** No longer available on the service. */
    @Immutable
    data object Expired : Availability

    /** Scheduled for future release; not yet playable. */
    @Stable
    sealed interface Upcoming : Availability {
        /** Release date is unknown. */
        @Immutable
        data object UnknownDate : Upcoming

        /** Scheduled for release at the given instant. */
        @Immutable
        data class At(val availableAtEpochSeconds: Instant) : Upcoming
    }

    /** Available only in specific geographic regions. */
    @Stable
    sealed interface GeoRestricted : Availability {
        /** Restriction applies but region list is unknown. */
        @Immutable
        data object UnknownRegions : GeoRestricted

        /** Restricted to the specified set of regions. */
        @Immutable
        data class Known(val allowedRegions: ImmutableSet<RegionCode> = persistentSetOf()) : GeoRestricted
    }
}
