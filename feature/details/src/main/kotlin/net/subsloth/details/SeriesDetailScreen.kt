package net.subsloth.details

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.Availability
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowStatus

@Composable
fun SeriesDetailScreen(viewModel: ShowDetailViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is DetailUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is DetailUiState.ShowContent -> {
            ShowDetailContent(
                state = s,
                onSeasonSelect = { viewModel.selectSeason(it) },
                modifier = modifier,
            )
        }
        is DetailUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.error.toDisplayString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        else -> {
            Text("Unexpected state")
        }
    }
}

@Composable
internal fun ShowDetailContent(
    state: DetailUiState.ShowContent,
    modifier: Modifier = Modifier,
    onSeasonSelect: (Int) -> Unit = {},
) {
    val details = state.details

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = details.title,
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            details.year?.let { year ->
                Text(text = year.toString(), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
            }
            details.rating?.let { rating ->
                Text(
                    text = "★ $rating",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (details.status) {
                    ShowStatus.ONGOING -> "Ongoing"
                    ShowStatus.ENDED -> "Ended"
                    ShowStatus.UPCOMING -> "Upcoming"
                    ShowStatus.UNKNOWN -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (details.genres.isNotEmpty()) {
            Text(
                text = details.genres.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        details.plot?.let { plot ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = plot, style = MaterialTheme.typography.bodyMedium)
        }

        if (details.seasons.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            SeasonSelector(
                seasons = details.seasons,
                selectedSeason = state.selectedSeason,
                onSeasonSelect = onSeasonSelect,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentSeason = details.seasons.find { it.seasonNumber == state.selectedSeason }
        currentSeason?.let { season ->
            Text(
                text = season.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            season.episodes.forEach { episode ->
                EpisodeRow(episode = episode)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SeasonSelector(
    seasons: List<Season>,
    selectedSeason: Int,
    modifier: Modifier = Modifier,
    onSeasonSelect: (Int) -> Unit = {},
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seasons.forEach { season ->
            FilterChip(
                selected = season.seasonNumber == selectedSeason,
                onClick = { onSeasonSelect(season.seasonNumber) },
                label = { Text(season.title.orEmpty()) },
            )
        }
    }
}

@Composable
fun EpisodeRow(episode: Episode, modifier: Modifier = Modifier) {
    val isUpcoming = episode.availability is Availability.Upcoming

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (isUpcoming) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.title}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isUpcoming) {
                    Text(
                        text = "Upcoming",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            episode.plot?.let { plot ->
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(modifier = Modifier.padding(top = 4.dp)) {
                episode.durationSeconds?.let { seconds ->
                    Text(
                        text = "${seconds / 60} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (episode.subtitles.isNotEmpty()) {
                    Text(
                        text = " · ${episode.subtitles.size} subtitles",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            if (isUpcoming) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This episode is not yet available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SeriesDetailLoadingPreview() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun SeriesDetailErrorPreview() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Failed to load details",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
