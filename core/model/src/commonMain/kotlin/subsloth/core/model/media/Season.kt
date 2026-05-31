package subsloth.core.model.media

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import subsloth.core.model.Availability

@Immutable
data class Season(val seasonNumber: Int, val title: String?, val plot: String?, val episodes: ImmutableList<Episode>) {
    val episodeCount: Int get() = episodes.size

    val isFullyAvailable: Boolean
        get() = episodes.all { it.availability is Availability.Available }
}
