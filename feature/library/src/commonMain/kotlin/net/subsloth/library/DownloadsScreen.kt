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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.library.generated.resources.Res
import subsloth.feature.library.generated.resources.downloads_active
import subsloth.feature.library.generated.resources.downloads_cancel
import subsloth.feature.library.generated.resources.downloads_completed
import subsloth.feature.library.generated.resources.downloads_delete
import subsloth.feature.library.generated.resources.downloads_delete_all
import subsloth.feature.library.generated.resources.downloads_delete_all_message
import subsloth.feature.library.generated.resources.downloads_delete_all_title
import subsloth.feature.library.generated.resources.downloads_delete_cancel
import subsloth.feature.library.generated.resources.downloads_delete_confirm
import subsloth.feature.library.generated.resources.downloads_delete_watched
import subsloth.feature.library.generated.resources.downloads_delete_watched_message
import subsloth.feature.library.generated.resources.downloads_delete_watched_title
import subsloth.feature.library.generated.resources.downloads_empty
import subsloth.feature.library.generated.resources.downloads_failed_unavailable
import subsloth.feature.library.generated.resources.downloads_pause
import subsloth.feature.library.generated.resources.downloads_queued_paused
import subsloth.feature.library.generated.resources.downloads_reason_ambiguous_quality
import subsloth.feature.library.generated.resources.downloads_reason_download_failed
import subsloth.feature.library.generated.resources.downloads_reason_insufficient_storage
import subsloth.feature.library.generated.resources.downloads_reason_missing_local_file
import subsloth.feature.library.generated.resources.downloads_reason_needs_wifi
import subsloth.feature.library.generated.resources.downloads_reason_subtitle_unavailable
import subsloth.feature.library.generated.resources.downloads_reason_unavailable
import subsloth.feature.library.generated.resources.downloads_remove
import subsloth.feature.library.generated.resources.downloads_resume
import subsloth.feature.library.generated.resources.downloads_retry
import subsloth.feature.library.generated.resources.downloads_season_queues
import subsloth.feature.library.generated.resources.downloads_title
import subsloth.feature.library.generated.resources.downloads_unknown_quality

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
    var showDeleteConfirmation by remember { mutableStateOf<DeleteConfirmationType?>(null) }

    DownloadsContentBody(
        state = state,
        modifier = modifier,
        onPause = onPause,
        onResume = onResume,
        onCancel = onCancel,
        onRetry = onRetry,
        onRemove = onRemove,
        onDeleteAllCompleted = { showDeleteConfirmation = DeleteConfirmationType.ALL },
        onDeleteWatchedCompleted = { showDeleteConfirmation = DeleteConfirmationType.WATCHED },
    )

    showDeleteConfirmation?.let { type ->
        DeleteConfirmationDialog(
            type = type,
            onConfirm = {
                when (type) {
                    DeleteConfirmationType.ALL -> onDeleteAllCompleted()
                    DeleteConfirmationType.WATCHED -> onDeleteWatchedCompleted()
                }
                showDeleteConfirmation = null
            },
            onDismiss = { showDeleteConfirmation = null },
        )
    }
}

private enum class DeleteConfirmationType { ALL, WATCHED }

@Composable
private fun DeleteConfirmationDialog(type: DeleteConfirmationType, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = when (type) {
        DeleteConfirmationType.ALL -> stringResource(Res.string.downloads_delete_all_title)
        DeleteConfirmationType.WATCHED -> stringResource(Res.string.downloads_delete_watched_title)
    }
    val message = when (type) {
        DeleteConfirmationType.ALL -> stringResource(Res.string.downloads_delete_all_message)
        DeleteConfirmationType.WATCHED -> stringResource(Res.string.downloads_delete_watched_message)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(Res.string.downloads_delete_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(Res.string.downloads_delete_cancel))
            }
        },
    )
}

@Composable
private fun DownloadsContentBody(
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
                text = stringResource(Res.string.downloads_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        if (state.active.isNotEmpty()) {
            item(key = "active_header") {
                SectionHeader(stringResource(Res.string.downloads_active))
            }
            state.active.forEach { item ->
                item(key = "active_${item.state.localId.value}") {
                    DownloadRow(
                        download = item,
                        actions = {
                            TextButton(onClick = { onPause(item.state.localId.value) }) {
                                Text(stringResource(Res.string.downloads_pause))
                            }
                            TextButton(onClick = { onCancel(item.state.localId.value) }) {
                                Text(stringResource(Res.string.downloads_cancel))
                            }
                        },
                    )
                }
            }
        }

        if (state.queuedOrPaused.isNotEmpty()) {
            item(key = "queued_paused_header") {
                SectionHeader(stringResource(Res.string.downloads_queued_paused))
            }
            state.queuedOrPaused.forEach { item ->
                item(key = "qp_${item.state.localId.value}") {
                    DownloadRow(
                        download = item,
                        actions = {
                            when (item.state) {
                                is DownloadState.Paused -> {
                                    TextButton(onClick = { onResume(item.state.localId.value) }) {
                                        Text(stringResource(Res.string.downloads_resume))
                                    }
                                    TextButton(onClick = { onCancel(item.state.localId.value) }) {
                                        Text(stringResource(Res.string.downloads_cancel))
                                    }
                                }

                                is DownloadState.Queued -> {
                                    TextButton(onClick = { onCancel(item.state.localId.value) }) {
                                        Text(stringResource(Res.string.downloads_cancel))
                                    }
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
                SectionHeader(stringResource(Res.string.downloads_failed_unavailable))
            }
            state.failedOrUnavailable.forEach { item ->
                item(key = "fu_${item.state.localId.value}") {
                    DownloadRow(
                        download = item,
                        actions = {
                            TextButton(onClick = { onRetry(item.state.localId.value) }) {
                                Text(stringResource(Res.string.downloads_retry))
                            }
                            TextButton(onClick = { onRemove(item.state.localId.value) }) {
                                Text(stringResource(Res.string.downloads_remove))
                            }
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
                    SectionHeader(stringResource(Res.string.downloads_completed))
                    Row {
                        TextButton(onClick = onDeleteWatchedCompleted) {
                            Text(stringResource(Res.string.downloads_delete_watched))
                        }
                        TextButton(onClick = onDeleteAllCompleted) {
                            Text(stringResource(Res.string.downloads_delete_all))
                        }
                    }
                }
            }
            state.completed.forEach { item ->
                item(key = "com_${item.state.localId.value}") {
                    DownloadRow(
                        download = item,
                        actions = {
                            TextButton(onClick = { onRemove(item.state.localId.value) }) {
                                Text(stringResource(Res.string.downloads_delete))
                            }
                        },
                    )
                }
            }
        }

        if (state.seasonQueues.isNotEmpty()) {
            item(key = "season_queues_header") {
                SectionHeader(stringResource(Res.string.downloads_season_queues))
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
                        text = stringResource(Res.string.downloads_empty),
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
                text = download.state.quality.label
                    ?: stringResource(Res.string.downloads_unknown_quality),
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
                    text = "+${queue.items.size - 5} more\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun formatMediaId(id: Media.MediaId): String = when (id) {
    is Media.MediaId.Movie -> "Movie ${id.value.value}"
    is Media.MediaId.Show -> "Show ${id.value.value}"
    is Media.MediaId.Episode -> "Episode ${id.value.value}"
}

@Composable
private fun formatFailureReason(reason: DownloadFailureReason): String = when (reason) {
    is DownloadFailureReason.AmbiguousQuality -> stringResource(Res.string.downloads_reason_ambiguous_quality)
    is DownloadFailureReason.DownloadFailed -> stringResource(Res.string.downloads_reason_download_failed)
    is DownloadFailureReason.InsufficientStorage -> stringResource(Res.string.downloads_reason_insufficient_storage)
    is DownloadFailureReason.MissingLocalFile -> stringResource(Res.string.downloads_reason_missing_local_file)
    is DownloadFailureReason.NeedsWifi -> stringResource(Res.string.downloads_reason_needs_wifi)
    is DownloadFailureReason.SubtitleUnavailable -> stringResource(Res.string.downloads_reason_subtitle_unavailable)
    is DownloadFailureReason.Unavailable -> stringResource(Res.string.downloads_reason_unavailable)
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
