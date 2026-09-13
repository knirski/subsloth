package net.subsloth.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import net.subsloth.core.ui.SubSlothBackButton
import net.subsloth.core.ui.WindowWidthClass
import net.subsloth.core.ui.currentWindowWidthClass
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
    if (currentWindowWidthClass() == WindowWidthClass.EXPANDED) {
        LibraryGridContent(
            state = state,
            modifier = modifier,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
    } else {
        LibraryListContent(
            state = state,
            modifier = modifier,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
    }
}

@Composable
private fun LibraryListContent(
    state: LibraryUiState.Content,
    modifier: Modifier = Modifier,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        if (state.continueWatching.isNotEmpty()) {
            item(key = "continue_watching") {
                LibraryRowSection(
                    label = stringResource(Res.string.library_continue_watching),
                    items = state.continueWatching,
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }

        if (state.favorites.isNotEmpty()) {
            item(key = "favorites") {
                LibraryRowSection(
                    label = stringResource(Res.string.library_favorites),
                    items = state.favorites,
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }

        if (state.watchLater.isNotEmpty()) {
            item(key = "watch_later") {
                LibraryRowSection(
                    label = stringResource(Res.string.library_watch_later),
                    items = state.watchLater,
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }

        if (state.custom.isNotEmpty()) {
            item(key = "custom") {
                LibraryRowSection(
                    label = stringResource(Res.string.library_collections),
                    items = state.custom,
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }

        if (state.availableOffline.isNotEmpty()) {
            item(key = "available_offline") {
                LibraryRowSection(
                    label = stringResource(Res.string.library_available_offline),
                    items = state.availableOffline,
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }

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

@Composable
private fun LibraryGridContent(
    state: LibraryUiState.Content,
    modifier: Modifier = Modifier,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    val isEmpty = state.continueWatching.isEmpty() && state.favorites.isEmpty() &&
        state.watchLater.isEmpty() && state.availableOffline.isEmpty() && state.custom.isEmpty()
    val continueWatchingLabel = stringResource(Res.string.library_continue_watching)
    val favoritesLabel = stringResource(Res.string.library_favorites)
    val watchLaterLabel = stringResource(Res.string.library_watch_later)
    val collectionsLabel = stringResource(Res.string.library_collections)
    val availableOfflineLabel = stringResource(Res.string.library_available_offline)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "library_title", span = { GridItemSpan(maxLineSpan) }) {
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

        libraryGridSection(
            label = continueWatchingLabel,
            items = state.continueWatching,
            sectionKey = "continue_watching",
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
        libraryGridSection(
            label = favoritesLabel,
            items = state.favorites,
            sectionKey = "favorites",
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
        libraryGridSection(
            label = watchLaterLabel,
            items = state.watchLater,
            sectionKey = "watch_later",
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
        libraryGridSection(
            label = collectionsLabel,
            items = state.custom,
            sectionKey = "custom",
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
        libraryGridSection(
            label = availableOfflineLabel,
            items = state.availableOffline,
            sectionKey = "available_offline",
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )

        if (isEmpty) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
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

private fun LazyGridScope.libraryGridSection(
    label: String,
    items: List<Media>,
    sectionKey: String,
    onMovieClick: (Media.MediaId.Movie) -> Unit,
    onShowClick: (Media.MediaId.Show) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "${sectionKey}_label", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    items(items, key = { media -> "${sectionKey}_${media.id}" }) { media ->
        LibraryMediaCard(
            media = media,
            fillWidth = true,
            onClick = mediaClick(media, onMovieClick, onShowClick),
        )
    }
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

@Composable
private fun LibraryRowSection(
    label: String,
    items: List<Media>,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(items.size, key = { index -> "${label}_${items[index].id}" }) { index ->
                val media = items[index]
                LibraryMediaCard(
                    media = media,
                    onClick = {
                        when (val mid = media.id) {
                            is Media.MediaId.Movie -> onMovieClick(mid)
                            is Media.MediaId.Show -> onShowClick(mid)
                            is Media.MediaId.Episode -> {}
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryMediaCard(
    media: Media,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    fillWidth: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(160.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            media.year?.let { year ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
