package net.subsloth.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.ui.AdaptiveContentFrame
import net.subsloth.core.ui.DownloadUnavailableNotice
import net.subsloth.core.ui.LocalDownloadActionsEnabled
import net.subsloth.core.ui.SubSlothBackButton
import net.subsloth.core.ui.tvSafeHorizontalPadding
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.library.generated.resources.Res
import subsloth.feature.library.generated.resources.downloads_active
import subsloth.feature.library.generated.resources.downloads_cancel
import subsloth.feature.library.generated.resources.downloads_completed
import subsloth.feature.library.generated.resources.downloads_delete
import subsloth.feature.library.generated.resources.downloads_delete_all
import subsloth.feature.library.generated.resources.downloads_delete_watched
import subsloth.feature.library.generated.resources.downloads_empty
import subsloth.feature.library.generated.resources.downloads_failed_unavailable
import subsloth.feature.library.generated.resources.downloads_pause
import subsloth.feature.library.generated.resources.downloads_queued_paused
import subsloth.feature.library.generated.resources.downloads_remove
import subsloth.feature.library.generated.resources.downloads_resume
import subsloth.feature.library.generated.resources.downloads_retry
import subsloth.feature.library.generated.resources.downloads_season_queues
import subsloth.feature.library.generated.resources.downloads_title

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        if (onNavigateBack != null) {
            SubSlothBackButton(
                onClick = onNavigateBack,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }

        when (val s = state) {
            is DownloadsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is DownloadsUiState.Content -> {
                DownloadsContent(
                    state = s,
                    modifier = Modifier,
                    showUnavailableNotice = !LocalDownloadActionsEnabled.current,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onCancel = viewModel::cancel,
                    onRetry = viewModel::retry,
                    onRemove = viewModel::remove,
                    onDeleteAllCompleted = viewModel::deleteAllCompleted,
                    onDeleteWatchedCompleted = viewModel::deleteWatchedCompleted,
                    onResumeQueue = viewModel::resumeQueue,
                )
            }
        }
    }
}

@Composable
fun DownloadsContent(
    state: DownloadsUiState.Content,
    modifier: Modifier = Modifier,
    showUnavailableNotice: Boolean = false,
    onPause: (String) -> Unit = {},
    onResume: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onDeleteAllCompleted: () -> Unit = {},
    onDeleteWatchedCompleted: () -> Unit = {},
    onResumeQueue: (String) -> Unit = {},
) {
    var showDeleteConfirmation by remember { mutableStateOf<DeleteConfirmationType?>(null) }

    AdaptiveContentFrame(modifier = modifier, maxWidth = 960.dp) {
        DownloadsContentBody(
            state = state,
            modifier = Modifier,
            showUnavailableNotice = showUnavailableNotice,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
            onRemove = onRemove,
            onDeleteAllCompleted = { showDeleteConfirmation = DeleteConfirmationType.ALL },
            onDeleteWatchedCompleted = { showDeleteConfirmation = DeleteConfirmationType.WATCHED },
            onResumeQueue = onResumeQueue,
        )
    }

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

internal enum class DeleteConfirmationType { ALL, WATCHED }

@Composable
private fun DownloadsContentBody(
    state: DownloadsUiState.Content,
    modifier: Modifier = Modifier,
    showUnavailableNotice: Boolean = false,
    onPause: (String) -> Unit = {},
    onResume: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onDeleteAllCompleted: () -> Unit = {},
    onDeleteWatchedCompleted: () -> Unit = {},
    onResumeQueue: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(Res.string.downloads_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        if (showUnavailableNotice) {
            item {
                DownloadUnavailableNotice(modifier = Modifier.fillMaxWidth())
            }
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
                    SeasonQueueCard(
                        queue = queue,
                        onResume = { onResumeQueue(queue.queueId.value) },
                    )
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
