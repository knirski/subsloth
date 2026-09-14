package net.subsloth.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.ui.CompactMediaRow
import net.subsloth.core.ui.SubSlothBackButton
import net.subsloth.core.ui.tvSafeHorizontalPadding
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.library.generated.resources.Res
import subsloth.feature.library.generated.resources.library_available_offline
import subsloth.feature.library.generated.resources.library_collections
import subsloth.feature.library.generated.resources.library_continue_watching
import subsloth.feature.library.generated.resources.library_empty_logged_in
import subsloth.feature.library.generated.resources.library_empty_logged_out
import subsloth.feature.library.generated.resources.library_favorites
import subsloth.feature.library.generated.resources.library_offline_title
import subsloth.feature.library.generated.resources.library_title
import subsloth.feature.library.generated.resources.library_watch_later

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
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
            is LibraryUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is LibraryUiState.Content -> {
                LibraryContent(
                    state = s,
                    modifier = Modifier,
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }
    }
}

@Composable
fun LibraryContent(
    state: LibraryUiState.Content,
    modifier: Modifier = Modifier,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    LibraryListContent(
        state = state,
        modifier = modifier,
        onMovieClick = onMovieClick,
        onShowClick = onShowClick,
    )
}

@Composable
private fun LibraryListContent(
    state: LibraryUiState.Content,
    modifier: Modifier = Modifier,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    val continueWatchingLabel = stringResource(Res.string.library_continue_watching)
    val favoritesLabel = stringResource(Res.string.library_favorites)
    val watchLaterLabel = stringResource(Res.string.library_watch_later)
    val collectionsLabel = stringResource(Res.string.library_collections)
    val availableOfflineLabel = stringResource(Res.string.library_available_offline)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = if (state.isLoggedIn) {
                    stringResource(Res.string.library_title)
                } else {
                    stringResource(Res.string.library_offline_title)
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        mediaSection(
            sectionKey = "continue_watching",
            label = continueWatchingLabel,
            items = state.continueWatching,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
        mediaSection(
            sectionKey = "favorites",
            label = favoritesLabel,
            items = state.favorites,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
        mediaSection(
            sectionKey = "watch_later",
            label = watchLaterLabel,
            items = state.watchLater,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
        mediaSection(
            sectionKey = "custom",
            label = collectionsLabel,
            items = state.custom,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
        mediaSection(
            sectionKey = "available_offline",
            label = availableOfflineLabel,
            items = state.availableOffline,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )

        val isEmpty = state.continueWatching.isEmpty() && state.favorites.isEmpty() &&
            state.watchLater.isEmpty() && state.availableOffline.isEmpty() && state.custom.isEmpty()
        if (isEmpty) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.isLoggedIn) {
                            stringResource(Res.string.library_empty_logged_in)
                        } else {
                            stringResource(Res.string.library_empty_logged_out)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.mediaSection(
    sectionKey: String,
    label: String,
    items: List<Media>,
    onMovieClick: (Media.MediaId.Movie) -> Unit,
    onShowClick: (Media.MediaId.Show) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "${sectionKey}_label", contentType = "label") {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    items(items, key = { media -> "${sectionKey}_${media.id}" }, contentType = { it::class }) { media ->
        LibraryMediaRow(media = media, onClick = mediaClick(media, onMovieClick, onShowClick))
    }
}

@Composable
private fun LibraryMediaRow(media: Media, onClick: () -> Unit = {}) {
    CompactMediaRow(
        title = media.title,
        subtitle = media.year?.toString(),
        glyph = if (media is ShowSummary) "📺" else "🎬",
        posterUrl = media.posterUrl,
        onClick = onClick,
    )
}

private fun mediaClick(
    media: Media,
    onMovieClick: (Media.MediaId.Movie) -> Unit,
    onShowClick: (Media.MediaId.Show) -> Unit,
): () -> Unit = {
    when (val mid = media.id) {
        is Media.MediaId.Movie -> onMovieClick(mid)
        is Media.MediaId.Show -> onShowClick(mid)
        is Media.MediaId.Episode -> {}
    }
}
