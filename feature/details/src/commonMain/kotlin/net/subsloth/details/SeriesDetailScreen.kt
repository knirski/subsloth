@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.subsloth.details

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.Availability
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.ui.toDisplayString
import net.subsloth.core.ui.toUiErrorMessage
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.details.generated.resources.*

@Composable
fun SeriesDetailScreen(viewModel: ShowDetailViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is ShowDetailUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is ShowDetailUiState.Content -> {
            ShowDetailContent(
                state = s,
                onSeasonSelect = { viewModel.selectSeason(it) },
                modifier = modifier,
            )
        }

        is ShowDetailUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.error.toUiErrorMessage().toDisplayString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun ShowDetailContent(
    state: ShowDetailUiState.Content,
    modifier: Modifier = Modifier,
    onSeasonSelect: (Int) -> Unit = {},
) {
    val isLandscapeWide = isLandscapeWideScreen()

    if (isLandscapeWide) {
        ShowDetailWideLayout(
            state = state,
            onSeasonSelect = onSeasonSelect,
            modifier = modifier,
        )
    } else {
        ShowDetailCompactLayout(
            state = state,
            onSeasonSelect = onSeasonSelect,
            modifier = modifier,
        )
    }
}

@Composable
private fun ShowDetailWideLayout(state: ShowDetailUiState.Content, modifier: Modifier, onSeasonSelect: (Int) -> Unit) {
    val details = state.details

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .verticalScroll(rememberScrollState()),
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
                        ShowStatus.ONGOING -> stringResource(Res.string.show_ongoing)
                        ShowStatus.ENDED -> stringResource(Res.string.show_ended)
                        ShowStatus.UPCOMING -> stringResource(Res.string.show_upcoming)
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
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (details.seasons.size > 1) {
                SeasonSelector(
                    seasons = details.seasons,
                    selectedSeason = state.selectedSeason,
                    onSeasonSelect = onSeasonSelect,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
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
}

@Composable
private fun ShowDetailCompactLayout(
    state: ShowDetailUiState.Content,
    modifier: Modifier,
    onSeasonSelect: (Int) -> Unit,
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
                    ShowStatus.ONGOING -> stringResource(Res.string.show_ongoing)
                    ShowStatus.ENDED -> stringResource(Res.string.show_ended)
                    ShowStatus.UPCOMING -> stringResource(Res.string.show_upcoming)
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
                        text = stringResource(Res.string.show_upcoming),
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

/**
 * Returns true when the screen is in landscape orientation and wide enough
 * for a two-column layout (≥800dp width in landscape).
 */
@Composable
private fun isLandscapeWideScreen(): Boolean {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { containerSize.width.toDp().value }
    val heightDp = with(density) { containerSize.height.toDp().value }
    return widthDp > heightDp && widthDp >= 800f
}
