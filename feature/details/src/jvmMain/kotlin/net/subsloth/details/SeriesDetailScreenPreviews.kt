package net.subsloth.details

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowStatus

// Previews
@Preview
@Composable
private fun ShowDetailContentPreview() {
    ShowDetailContent(
        state = ShowDetailUiState.Content(
            details = ShowDetails(
                id = Media.MediaId.Show(ShowId(1)),
                title = "Sample Show",
                plot = "A sample plot",
                description = "A sample description",
                availability = Availability.Available,
                rating = 7.5,
                year = 2023,
                genres = persistentListOf(),
                durationMinutes = 45,
                qualities = persistentListOf(),
                subtitles = persistentListOf(),
                slug = null,
                imdbId = null,
                tmdbId = null,
                countries = persistentListOf(),
                posterUrl = null,
                backdropUrl = null,
                status = ShowStatus.ONGOING,
                popularity = null,
                seasons = persistentListOf(),
            ),
            selectedSeason = 1,
        ),
    )
}
