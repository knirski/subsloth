package net.subsloth.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.Availability
import net.subsloth.core.model.media.EpisodeDetails
import net.subsloth.core.ui.DownloadUnavailableNotice
import net.subsloth.core.ui.LocalDownloadActionsEnabled
import net.subsloth.core.ui.SubSlothBackButton
import net.subsloth.core.ui.tvSafeHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    viewModel: EpisodeDetailViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onPlayClick: (EpisodeDetails) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = modifier.fillMaxSize()) {
        when (val s = state) {
            is EpisodeDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "…")
                }
            }

            is EpisodeDetailUiState.Error -> EpisodeDetailErrorContent(
                modifier = Modifier.fillMaxSize(),
                onNavigateBack = onNavigateBack,
            )

            is EpisodeDetailUiState.Content -> EpisodeDetailContent(
                details = s.details,
                isWatched = s.isWatched,
                downloadStatus = s.downloadStatus,
                downloadProgressPercent = s.downloadProgressPercent,
                progressFraction = s.progressFraction,
                modifier = Modifier.fillMaxSize(),
                onNavigateBack = onNavigateBack,
                onPlayClick = onPlayClick,
                onDownloadClick = viewModel::toggleDownload,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeDetailContent(
    details: EpisodeDetails,
    isWatched: Boolean,
    downloadStatus: DownloadStatus,
    downloadProgressPercent: Int?,
    progressFraction: Double?,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onPlayClick: (EpisodeDetails) -> Unit,
    onDownloadClick: () -> Unit,
) {
    val isAvailable = details.availability is Availability.Available

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Text(
                    text = "S${details.seasonNumber} · E${details.episodeNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                SubSlothBackButton(onClick = onNavigateBack)
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tvSafeHorizontalPadding(16.dp), vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (isWatched) {
                    Text(
                        text = "✓ Watched",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = details.availability.label(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isAvailable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            details.plot?.let { plot ->
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (details.subtitles.isNotEmpty()) {
                Text(
                    text = "Subtitles: ${details.subtitles.joinToString { subtitle -> subtitle.language.value }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (details.qualities.isNotEmpty()) {
                Text(
                    text = "Quality: ${details.qualities.maxByOrNull {
                        it.info.resolution.pixelCount
                    }?.info?.label ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isAvailable) {
                val playLabel = when (val action = detailPlayAction(progressFraction)) {
                    is DetailPlayAction.Play -> "Play"
                    is DetailPlayAction.Resume -> "Resume"
                    is DetailPlayAction.ResumeAt -> "Resume ${action.percent}%"
                }
                Button(
                    onClick = { onPlayClick(details) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = playLabel)
                }
            } else {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Not available")
                }
            }

            if (LocalDownloadActionsEnabled.current) {
                val downloadLabel = when (downloadStatus) {
                    DownloadStatus.DOWNLOADED -> "Downloaded"
                    DownloadStatus.QUEUED -> "Queued"
                    DownloadStatus.DOWNLOADING -> "Downloading ${downloadProgressPercent ?: 0}%"
                    DownloadStatus.FAILED -> "Retry download"
                    DownloadStatus.NOT_DOWNLOADED -> "Download"
                }
                OutlinedButton(
                    onClick = onDownloadClick,
                    enabled =
                    downloadStatus != DownloadStatus.QUEUED &&
                        downloadStatus != DownloadStatus.DOWNLOADING,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = downloadLabel)
                }
            } else {
                DownloadUnavailableNotice(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EpisodeDetailErrorContent(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = tvSafeHorizontalPadding(24.dp), vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Episode not available",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onNavigateBack) {
            Text(text = "Back")
        }
    }
}

private fun Availability.label(): String = when (this) {
    is Availability.Available -> "Available"
    is Availability.Expired -> "Expired"
    is Availability.GeoRestricted -> "Not available in your region"
    is Availability.Upcoming -> "Upcoming"
}
