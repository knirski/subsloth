@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.subsloth.details

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.subsloth.core.model.Availability
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Season
import net.subsloth.core.ui.DownloadUnavailableNotice
import net.subsloth.core.ui.LocalDownloadActionsEnabled
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.details.generated.resources.*

@Composable
internal fun SeasonDownloadButton(
    state: SeasonDownloadState,
    modifier: Modifier = Modifier,
    onDownloadClick: () -> Unit = {},
) {
    if (!LocalDownloadActionsEnabled.current) {
        DownloadUnavailableNotice(modifier = modifier.fillMaxWidth())
        return
    }
    OutlinedButton(
        onClick = onDownloadClick,
        enabled = state != SeasonDownloadState.Queued && state != SeasonDownloadState.Downloading,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = when (state) {
                SeasonDownloadState.Idle -> stringResource(Res.string.detail_download_season)
                SeasonDownloadState.Queued -> stringResource(Res.string.detail_download_season_queued)
                SeasonDownloadState.Downloading -> stringResource(Res.string.detail_download_season_downloading)
                SeasonDownloadState.Completed -> stringResource(Res.string.detail_download_season_again)
                SeasonDownloadState.Failed -> stringResource(Res.string.detail_download_season_retry)
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SeasonSelector(
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
internal fun SeasonEpisodesSection(
    season: Season,
    watchedEpisodeIds: List<Int>,
    episodeProgress: Map<Int, Double>,
    onEpisodeClick: (Episode) -> Unit,
) {
    val watchedCount = season.episodes.count { episode -> watchedEpisodeIds.contains(episode.id.value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = season.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
        SeasonWatchedSummary(watchedCount = watchedCount, total = season.episodes.size)
    }
    Spacer(modifier = Modifier.height(8.dp))
    season.episodes.forEach { episode ->
        EpisodeRow(
            episode = episode,
            isWatched = watchedEpisodeIds.contains(episode.id.value),
            progressFraction = episodeProgress[episode.id.value],
            onClick = { onEpisodeClick(episode) },
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
internal fun SeasonWatchedSummary(watchedCount: Int, total: Int) {
    if (watchedCount == 0 || total == 0) return
    Text(
        text = if (watchedCount == total) "✓ All watched" else "$watchedCount/$total watched",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
fun EpisodeRow(
    episode: Episode,
    modifier: Modifier = Modifier,
    isWatched: Boolean = false,
    progressFraction: Double? = null,
    onClick: () -> Unit = {},
) {
    val isUpcoming = episode.availability is Availability.Upcoming

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = when {
            isUpcoming -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )

            isWatched -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            )

            else -> CardDefaults.cardColors()
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
                    color = if (isWatched) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isWatched) {
                    Text(
                        text = "✓ Watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (progressFraction != null) {
                    val resumeLabel = when (val action = detailPlayAction(progressFraction)) {
                        is DetailPlayAction.Play -> null
                        is DetailPlayAction.Resume -> "Resume"
                        is DetailPlayAction.ResumeAt -> "Resume ${action.percent}%"
                    }
                    resumeLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
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
                        text = stringResource(Res.string.detail_duration_format, seconds / 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (episode.subtitles.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.detail_episode_count_format, episode.subtitles.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            if (isUpcoming) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.detail_episode_not_available),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
