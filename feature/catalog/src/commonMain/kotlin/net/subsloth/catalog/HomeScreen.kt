package net.subsloth.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.ui.WindowWidthClass
import net.subsloth.core.ui.currentWindowWidthClass
import net.subsloth.core.ui.tvInitialFocus
import net.subsloth.core.ui.tvSafeHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onTabSelected: (HomeTab) -> Unit = {},
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
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.tvInitialFocus(),
                    ) {
                        Text("🔍", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onLibraryClick) {
                        Text(
                            text = "📚",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { contentDescription = "Library" },
                        )
                    }
                    IconButton(onClick = onDownloadsClick) {
                        Text(
                            text = "⬇",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { contentDescription = "Downloads" },
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Text(
                            text = "⚙",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { contentDescription = "Settings" },
                        )
                    }
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
                    onTabSelected = onTabSelected,
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
    onTabSelected: (HomeTab) -> Unit = {},
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
            HomeTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label) },
                )
            }
        }

        when (state.selectedTab) {
            HomeTab.HOME -> HomeDashboard(state, onMovieClick = onMovieClick, onShowClick = onShowClick)

            HomeTab.MOVIES -> MediaRowsContent(
                rows = state.rows.filter { it.isMovieRow() },
                showMoviesUnavailableNotice = state.moviesUnavailable,
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )

            HomeTab.SHOWS -> MediaRowsContent(
                rows = state.rows.filter { it.isShowRow() },
                showMoviesUnavailableNotice = false,
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )

            HomeTab.FAVORITES -> PersonalRowContent(
                mediaItems = state.favorites,
                emptyText = "No favorites yet. Tap Favorite on a movie or show to add one.",
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )

            HomeTab.WATCH_LATER -> PersonalRowContent(
                mediaItems = state.watchLater,
                emptyText = "Nothing on your Watch Later list yet.",
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )
        }
    }
}

private val HomeTab.label: String
    get() = when (this) {
        HomeTab.HOME -> "Home"
        HomeTab.MOVIES -> "Movies"
        HomeTab.SHOWS -> "Shows"
        HomeTab.FAVORITES -> "Favorites"
        HomeTab.WATCH_LATER -> "Watch Later"
    }

private fun HomeRow<*>.isMovieRow(): Boolean = when (this) {
    is HomeRow.Movies -> true
    is HomeRow.Shows -> false
    is HomeRow.Recency -> items.firstOrNull() is MovieSummary
}

private fun HomeRow<*>.isShowRow(): Boolean = when (this) {
    is HomeRow.Movies -> false
    is HomeRow.Shows -> true
    is HomeRow.Recency -> items.firstOrNull() is ShowSummary
}

@Composable
private fun HomeDashboard(
    state: HomeUiState.Content,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    if (state.continueWatching.isEmpty() && state.availableOffline.isEmpty()) {
        EmptyTabContent("Nothing in progress and nothing downloaded yet.")
        return
    }
    if (currentWindowWidthClass() == WindowWidthClass.EXPANDED) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.continueWatching.isNotEmpty()) {
                item(key = "continue_watching_label", span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel("Continue Watching")
                }
                items(state.continueWatching, key = { it.id.key }) { media ->
                    MediaCard(
                        media = media,
                        fillWidth = true,
                        onClick = mediaClick(media, onMovieClick, onShowClick),
                    )
                }
            }
            if (state.availableOffline.isNotEmpty()) {
                item(key = "available_offline_label", span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel("Available Offline")
                }
                items(state.availableOffline, key = { it.id.key }) { media ->
                    MediaCard(
                        media = media,
                        fillWidth = true,
                        onClick = mediaClick(media, onMovieClick, onShowClick),
                    )
                }
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.continueWatching.isNotEmpty()) {
            item(key = "continue_watching") {
                HomeRowSection(
                    row = HomeRow.Recency(state.continueWatching, label = "Continue Watching"),
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }
        if (state.availableOffline.isNotEmpty()) {
            item(key = "available_offline") {
                HomeRowSection(
                    row = HomeRow.Recency(state.availableOffline, label = "Available Offline"),
                    onMovieClick = onMovieClick,
                    onShowClick = onShowClick,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MediaRowsContent(
    rows: List<HomeRow<*>>,
    showMoviesUnavailableNotice: Boolean,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showMoviesUnavailableNotice) {
            item(key = "movies_unavailable", contentType = "notice") {
                Text(
                    text = MOVIES_UNAVAILABLE_NOTICE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        rows.forEach { row ->
            item(key = row.label, contentType = row::class) {
                HomeRowSection(row = row, onMovieClick = onMovieClick, onShowClick = onShowClick)
            }
        }
    }
}

@Composable
private fun PersonalRowContent(
    mediaItems: List<Media>,
    emptyText: String,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    if (mediaItems.isEmpty()) {
        EmptyTabContent(emptyText)
        return
    }
    if (currentWindowWidthClass() == WindowWidthClass.EXPANDED) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(mediaItems, key = { it.id.key }) { media ->
                MediaCard(
                    media = media,
                    fillWidth = true,
                    onClick = mediaClick(media, onMovieClick, onShowClick),
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
    ) {
        item(key = "personal_row") {
            HomeRowSection(
                row = HomeRow.Recency(mediaItems.toImmutableList(), label = null),
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )
        }
    }
}

@Composable
private fun EmptyTabContent(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown when the catalog has shows but no movies (missing movie entitlement). */
private const val MOVIES_UNAVAILABLE_NOTICE = "Movies aren't available on this account."

/** Test tag on every catalog media card, shared with UI and E2E tests. */
const val MEDIA_CARD_TEST_TAG = "media_card"

@Composable
private fun HomeRowSection(
    row: HomeRow<*>,
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
                        onClick = mediaClick(media, onMovieClick, onShowClick),
                    )
                }
            }
        }
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
fun MediaCard(media: Media, modifier: Modifier = Modifier, onClick: () -> Unit = {}, fillWidth: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(160.dp))
            .testTag(MEDIA_CARD_TEST_TAG),
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
