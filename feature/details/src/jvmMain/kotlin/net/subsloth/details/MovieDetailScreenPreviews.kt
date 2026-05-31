package net.subsloth.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails

// Previews
@Preview
@Composable
private fun MovieDetailContentPreview() {
    MovieDetailContent(
        state = MovieDetailUiState.Content(
            details = MovieDetails(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Sample Movie",
                plot = "A sample plot",
                description = "A sample description",
                availability = Availability.Available,
                rating = 8.5,
                year = 2024,
                genres = persistentListOf(),
                durationMinutes = 120,
                qualities = persistentListOf(),
                subtitles = persistentListOf(),
                slug = null,
                imdbId = null,
                tmdbId = null,
                countries = persistentListOf(),
                posterUrl = null,
                backdropUrl = null,
            ),
        ),
    )
}
