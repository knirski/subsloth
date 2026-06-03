package net.subsloth.library

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.media.Media

@Composable
fun LibraryScreenPreview() {
    LibraryContent(
        state = LibraryUiState.Content(
            isLoggedIn = true,
            continueWatching = persistentListOf(),
            favorites = persistentListOf(),
            watchLater = persistentListOf(),
            availableOffline = persistentListOf(),
            custom = persistentListOf(),
            showCatalogLink = true,
        ),
    )
}

@Composable
fun DownloadsScreenPreview() {
    DownloadsContent(
        state = DownloadsUiState.Content(
            active = persistentListOf(),
            queuedOrPaused = persistentListOf(),
            failedOrUnavailable = persistentListOf(),
            completed = persistentListOf(),
            seasonQueues = persistentListOf(),
        ),
    )
}
