package net.subsloth.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.SeasonQueueExecution
import net.subsloth.core.model.download.SeasonQueueItemExecution
import net.subsloth.core.model.media.Media
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.library.generated.resources.Res
import subsloth.feature.library.generated.resources.downloads_delete_all_message
import subsloth.feature.library.generated.resources.downloads_delete_all_title
import subsloth.feature.library.generated.resources.downloads_delete_cancel
import subsloth.feature.library.generated.resources.downloads_delete_confirm
import subsloth.feature.library.generated.resources.downloads_delete_watched_message
import subsloth.feature.library.generated.resources.downloads_delete_watched_title
import subsloth.feature.library.generated.resources.downloads_reason_ambiguous_quality
import subsloth.feature.library.generated.resources.downloads_reason_download_failed
import subsloth.feature.library.generated.resources.downloads_reason_insufficient_storage
import subsloth.feature.library.generated.resources.downloads_reason_missing_local_file
import subsloth.feature.library.generated.resources.downloads_reason_needs_wifi
import subsloth.feature.library.generated.resources.downloads_reason_subtitle_unavailable
import subsloth.feature.library.generated.resources.downloads_reason_unavailable
import subsloth.feature.library.generated.resources.downloads_resume
import subsloth.feature.library.generated.resources.downloads_unknown_quality

@Composable
internal fun DeleteConfirmationDialog(type: DeleteConfirmationType, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
                    contentColor = MaterialTheme.colorScheme.onError,
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
internal fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
internal fun DownloadRow(
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
                text = download.state.displayTitle ?: formatMediaId(download.state.mediaId),
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
internal fun SeasonQueueCard(queue: SeasonDownloadQueue, modifier: Modifier = Modifier, onResume: () -> Unit = {}) {
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
            if (queue.execution is SeasonQueueExecution.Paused) {
                TextButton(
                    onClick = onResume,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(Res.string.downloads_resume))
                }
            }
        }
    }
}

@Composable
internal fun formatMediaId(id: Media.MediaId): String = when (id) {
    is Media.MediaId.Movie -> "Movie ${id.value.value}"
    is Media.MediaId.Show -> "Show ${id.value.value}"
    is Media.MediaId.Episode -> "Episode ${id.value.value}"
}

@Composable
internal fun formatFailureReason(reason: DownloadFailureReason): String = when (reason) {
    DownloadFailureReason.AmbiguousQuality -> stringResource(Res.string.downloads_reason_ambiguous_quality)
    DownloadFailureReason.DownloadFailed -> stringResource(Res.string.downloads_reason_download_failed)
    DownloadFailureReason.InsufficientStorage -> stringResource(Res.string.downloads_reason_insufficient_storage)
    DownloadFailureReason.MissingLocalFile -> stringResource(Res.string.downloads_reason_missing_local_file)
    DownloadFailureReason.NeedsWifi -> stringResource(Res.string.downloads_reason_needs_wifi)
    DownloadFailureReason.SubtitleUnavailable -> stringResource(Res.string.downloads_reason_subtitle_unavailable)
    DownloadFailureReason.Unavailable -> stringResource(Res.string.downloads_reason_unavailable)
}

internal fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "${(gb * 10).toLong() / 10.0} GB"
        mb >= 1.0 -> "${mb.toLong()} MB"
        else -> "${kb.toLong()} KB"
    }
}
