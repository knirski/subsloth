package net.subsloth.core.model.media

import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.Availability

data class Season(val seasonNumber: Int, val title: String?, val plot: String?, val episodes: ImmutableList<Episode>) {
    val episodeCount: Int get() = episodes.size

    val isFullyAvailable: Boolean
        get() = episodes.all { it.availability is Availability.Available }
}
