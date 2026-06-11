package net.subsloth.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is LibraryUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is LibraryUiState.Content -> {
            LibraryContent(
                state = s,
                modifier = modifier,
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )
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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
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
private fun LibraryMediaCard(media: Media, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        modifier = modifier.width(160.dp),
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
