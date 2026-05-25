package net.subsloth.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import net.subsloth.core.model.identifier.RegionCode
import kotlin.time.Instant

@Stable
sealed interface Availability {
    @Immutable
    data object Available : Availability

    @Immutable
    data object Expired : Availability

    @Stable
    sealed interface Upcoming : Availability {
        @Immutable
        data object UnknownDate : Upcoming

        @Immutable
        data class At(
            val availableAtEpochSeconds: Instant,
        ) : Upcoming
    }

    @Stable
    sealed interface GeoRestricted : Availability {
        @Immutable
        data object UnknownRegions : GeoRestricted

        @Immutable
        data class Known(
            val allowedRegions: ImmutableSet<RegionCode> = persistentSetOf(),
        ) : GeoRestricted
    }
}
