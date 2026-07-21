package net.subsloth.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSyncing = (state as? HomeUiState.Content)?.isSyncing == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.syncErrors.collect { error ->
            val message = when (error) {
                is SyncError.NoConnectivity -> "No internet connection"
                is SyncError.Timeout -> "Request timed out"
                is SyncError.ServerError -> "Server error (${error.code})"
                is SyncError.Unknown -> "Sync failed"
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Retry",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retrySync()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("SubSloth") },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.sync() },
                            enabled = !isSyncing,
                        ) {
                            Text("⟳", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is HomeUiState.Content -> {
                CatalogContent(
                    state = s,
                    modifier = modifier.padding(padding),
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }
    }
}

@Composable
fun CatalogContent(
    state: HomeUiState.Content,
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
        val rows = state.rows
        rows.forEach { row ->
            item(key = row.label, contentType = row::class) {
                HomeRowSection(row = row, onMovieClick = onMovieClick, onShowClick = onShowClick)
            }
        }
    }
}

@Composable
private fun HomeRowSection(
    row: HomeRow,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    Column {
        row.label?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            row.items.forEach { media ->
                item(key = media.id.key, contentType = media::class) {
                    MediaCard(
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
}

@Composable
fun MediaCard(media: Media, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        modifier = modifier.width(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
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
            media.rating?.let { rating ->
                Text(
                    text = "★ $rating",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            when (media) {
                is MovieSummary -> {
                    if (media.genres.isNotEmpty()) {
                        Text(
                            text = media.genres.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                is ShowSummary -> {
                    Text(
                        text = media.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
