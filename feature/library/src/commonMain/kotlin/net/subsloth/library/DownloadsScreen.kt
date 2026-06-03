package net.subsloth.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.SeasonQueueItemExecution
import net.subsloth.core.model.media.Media

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is DownloadsUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is DownloadsUiState.Content -> {
            DownloadsContent(
                state = s,
                modifier = modifier,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onCancel = viewModel::cancel,
                onRetry = viewModel::retry,
                onRemove = viewModel::remove,
                onDeleteAllCompleted = viewModel::deleteAllCompleted,
                onDeleteWatchedCompleted = viewModel::deleteWatchedCompleted,
            )
        }
    }
}

@Composable
internal fun DownloadsContent(
    state: DownloadsUiState.Content,
    modifier: Modifier = Modifier,
    onPause: (String) -> Unit = {},
    onResume: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onDeleteAllCompleted: () -> Unit = {},
    onDeleteWatchedCompleted: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        if (state.active.isNotEmpty()) {
            item(key = "active_header") {
                SectionHeader("Active")
            }
            state.active.forEach { item ->
                item(key = "active_${item.state.localId.value}") {
                    DownloadRow(
                        download = item,
                        actions = {
                            TextButton(onClick = { onPause(item.state.localId.value) }) { Text("Pause") }
                            TextButton(onClick = { onCancel(item.state.localId.value) }) { Text("Cancel") }
                        },
                    )
                }
            }
        }

        if (state.queuedOrPaused.isNotEmpty()) {
            item(key = "queued_paused_header") {
                SectionHeader("Queued / Paused")
            }
            state.queuedOrPaused.forEach { item ->
                item(key = "qp_${item.state.localId.value}") {
                    DownloadRow(
                        download = item,
                        actions = {
                            when (item.state) {
                                is DownloadState.Paused -> {
                                    TextButton(onClick = { onResume(item.state.localId.value) }) { Text("Resume") }
                                    TextButton(onClick = { onCancel(item.state.localId.value) }) { Text("Cancel") }
                                }

                                is DownloadState.Queued -> {
                                    TextButton(onClick = { onCancel(item.state.localId.value) }) { Text("Cancel") }
                                }

                                else -> {}
                            }
                        },
                    )
                }
            }
        }

        if (state.failedOrUnavailable.isNotEmpty()) {
            item(key = "failed_header") {
                SectionHeader("Failed / Unavailable")
            }
            state.failedOrUnavailable.forEach { item ->
                item(key = "fu_${item.state.localId.value}") {
                    DownloadRow(
                        download = item,
                        actions = {
                            TextButton(onClick = { onRetry(item.state.localId.value) }) { Text("Retry") }
                            TextButton(onClick = { onRemove(item.state.localId.value) }) { Text("Remove") }
                        },
                    )
                }
            }
        }

        if (state.completed.isNotEmpty()) {
            item(key = "completed_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionHeader("Completed")
                    Row {
                        TextButton(onClick = onDeleteWatchedCompleted) { Text("Delete watched") }
                        TextButton(onClick = onDeleteAllCompleted) { Text("Delete all") }
                    }
                }
            }
            state.completed.forEach { item ->
                item(key = "com_${item.state.localId.value}") {
                    DownloadRow(
                        download = item,
                        actions = {
                            TextButton(onClick = { onRemove(item.state.localId.value) }) { Text("Delete") }
                        },
                    )
                }
            }
        }

        if (state.seasonQueues.isNotEmpty()) {
            item(key = "season_queues_header") {
                SectionHeader("Season Queues")
            }
            state.seasonQueues.forEach { queue ->
                item(key = "sq_${queue.queueId.value}") {
                    SeasonQueueCard(queue = queue)
                }
            }
        }

        if (state.active.isEmpty() && state.queuedOrPaused.isEmpty() &&
            state.failedOrUnavailable.isEmpty() && state.completed.isEmpty() &&
            state.seasonQueues.isEmpty()
        ) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No downloads yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun DownloadRow(
    download: DownloadGroupItem,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = formatMediaId(download.state.mediaId),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = download.state.quality.label ?: "Unknown quality",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val s = download.state) {
                is DownloadState.Active -> {
                    download.progressFraction?.let { fraction ->
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { fraction.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = "${s.progressPercent}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is DownloadState.Completed -> {
                    s.sizeBytes?.let { size ->
                        Text(
                            text = formatSize(size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is DownloadState.Failed -> {
                    Text(
                        text = "Failed: ${formatFailureReason(s.reason)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is DownloadState.Paused -> {
                    Text(
                        text = "Paused: ${formatFailureReason(s.reason)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is DownloadState.Unavailable -> {
                    Text(
                        text = "Unavailable: ${formatFailureReason(s.reason)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is DownloadState.Queued -> {
                    Text(
                        text = "Queued",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is DownloadState.Partial -> {
                    Text(
                        text = "Partial",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is DownloadState.Removed -> {
                    Text(
                        text = "Removed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun SeasonQueueCard(queue: SeasonDownloadQueue, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Season ${queue.seasonNumber}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${queue.items.size} episodes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            queue.items.take(5).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Episode ${item.mediaId.value.value}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val statusText = when (val execution = item.execution) {
                        is SeasonQueueItemExecution.Pending -> "Pending"
                        is SeasonQueueItemExecution.Downloading -> "${execution.progressPercent}%"
                        is SeasonQueueItemExecution.Completed -> "Done"
                        is SeasonQueueItemExecution.Failed -> "Failed"
                        is SeasonQueueItemExecution.Cancelled -> "Cancelled"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (queue.items.size > 5) {
                Text(
                    text = "+${queue.items.size - 5} more...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatMediaId(id: Media.MediaId): String = when (id) {
    is Media.MediaId.Movie -> "Movie ${id.value.value}"
    is Media.MediaId.Show -> "Show ${id.value.value}"
    is Media.MediaId.Episode -> "Episode ${id.value.value}"
}

private fun formatFailureReason(reason: DownloadFailureReason): String = when (reason) {
    is DownloadFailureReason.AmbiguousQuality -> "Ambiguous quality"
    is DownloadFailureReason.DownloadFailed -> "Download failed"
    is DownloadFailureReason.InsufficientStorage -> "Insufficient storage"
    is DownloadFailureReason.MissingLocalFile -> "Missing local file"
    is DownloadFailureReason.NeedsWifi -> "Wi-Fi required"
    is DownloadFailureReason.SubtitleUnavailable -> "Subtitle unavailable"
    is DownloadFailureReason.Unavailable -> "Unavailable"
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "${(gb * 10).toLong() / 10.0} GB"
        mb >= 1.0 -> "${mb.toLong()} MB"
        else -> "${kb.toLong()} KB"
    }
}
