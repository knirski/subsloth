package net.subsloth.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.ui.CompactMediaRow
import net.subsloth.core.ui.tvInitialFocus
import net.subsloth.core.ui.tvSafeHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
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
                        Text(
                            text = "🔍",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { contentDescription = "Search" },
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
            HomeTab.HOME -> MediaListContent(
                sections =
                buildList {
                    if (state.continueWatching.isNotEmpty()) {
                        add(MediaListSection(label = "Continue Watching", items = state.continueWatching))
                    }
                    if (state.availableOffline.isNotEmpty()) {
                        add(MediaListSection(label = "Available Offline", items = state.availableOffline))
                    }
                },
                emptyText = "Nothing in progress and nothing downloaded yet.",
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )

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

            HomeTab.FAVORITES -> MediaListContent(
                sections = listOf(MediaListSection(label = null, items = state.favorites)),
                emptyText = "No favorites yet. Tap Favorite on a movie or show to add one.",
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )

            HomeTab.WATCH_LATER -> MediaListContent(
                sections = listOf(MediaListSection(label = null, items = state.watchLater)),
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

private data class MediaListSection(val label: String?, val items: List<Media>)

@Composable
private fun MediaListContent(
    sections: List<MediaListSection>,
    emptyText: String,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    if (sections.all { it.items.isEmpty() }) {
        EmptyTabContent(emptyText)
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tvSafeHorizontalPadding(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            section.label?.let { label ->
                item(key = "label_$label", contentType = "label") { SectionLabel(label) }
            }
            items(section.items, key = { media -> "${section.label ?: "row"}_${media.id.key}" }) { media ->
                MediaListRow(media = media, onClick = mediaClick(media, onMovieClick, onShowClick))
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            row.label?.let { label ->
                item(key = "label_$label", contentType = "label") { SectionLabel(label) }
            }
            items(row.items, key = { media -> "${row.label ?: "row"}_${media.id.key}" }) { media ->
                MediaListRow(media = media, onClick = mediaClick(media, onMovieClick, onShowClick))
            }
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

/** Test tag on every catalog media row, shared with UI and E2E tests. */
const val MEDIA_CARD_TEST_TAG = "media_card"

@Composable
fun MediaListRow(media: Media, onClick: () -> Unit = {}) {
    CompactMediaRow(
        title = media.title,
        subtitle = media.subtitleLine(),
        glyph = if (media is ShowSummary) "📺" else "🎬",
        posterUrl = media.posterUrl,
        onClick = onClick,
        testTag = MEDIA_CARD_TEST_TAG,
    )
}

/** One metadata line: year, rating, then type-specific context. */
private fun Media.subtitleLine(): String? {
    val parts = mutableListOf<String>()
    year?.let { parts += it.toString() }
    rating?.let { parts += "★ $it" }
    when (this) {
        is MovieSummary -> if (genres.isNotEmpty()) parts += genres.joinToString(", ")
        is ShowSummary -> parts += status.name.lowercase().replaceFirstChar { it.uppercase() }
    }
    return parts.joinToString(" · ").ifBlank { null }
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
