package net.subsloth.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.subsloth.catalog.HomeScreen
import net.subsloth.catalog.HomeViewModel
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.ui.AuthRepairKey
import net.subsloth.core.ui.CatalogKey
import net.subsloth.core.ui.DiagnosticsKey
import net.subsloth.core.ui.DownloadsKey
import net.subsloth.core.ui.LibraryKey
import net.subsloth.core.ui.LoginKey
import net.subsloth.core.ui.MovieDetailKey
import net.subsloth.core.ui.OfflineLibraryKey
import net.subsloth.core.ui.PlayerKey
import net.subsloth.core.ui.SettingsKey
import net.subsloth.core.ui.ShowDetailKey
import net.subsloth.core.ui.subslothNavConfig
import net.subsloth.details.MovieDetailScreen
import net.subsloth.details.MovieDetailViewModel
import net.subsloth.details.SeriesDetailScreen
import net.subsloth.details.ShowDetailViewModel
import net.subsloth.library.DownloadsScreen
import net.subsloth.library.DownloadsViewModel
import net.subsloth.library.LibraryScreen
import net.subsloth.library.LibraryViewModel
import net.subsloth.player.PlayerScreen
import net.subsloth.player.PlayerViewModel
import net.subsloth.settings.DiagnosticsScreen
import net.subsloth.settings.DiagnosticsViewModel
import net.subsloth.settings.SettingsScreen
import net.subsloth.settings.SettingsViewModel

/**
 * Web navigation host for SubSloth.
 *
 * Uses Navigation3 via [androidx.navigation3.runtime] with a
 * [SavedStateConfiguration], just like Android and Desktop.
 * Feature screens are the same KMP composables shared across all platforms.
 */
@Composable
fun WebNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(subslothNavConfig, LoginKey)

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<LoginKey> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "SubSloth",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            entry<CatalogKey> {
                CatalogContent(
                    onMovieClick = { backStack += MovieDetailKey(it.value.value.toString()) },
                    onShowClick = { backStack += ShowDetailKey(it.value.value.toString()) },
                )
            }

            entry<MovieDetailKey> { key ->
                val movieId = key.movieId.toIntOrNull()?.let { Media.MediaId.Movie(MovieId(it)) }
                if (movieId != null) {
                    MovieDetailContent(movieId = movieId)
                }
            }

            entry<ShowDetailKey> { key ->
                val showId = key.showId.toIntOrNull()?.let { Media.MediaId.Show(ShowId(it)) }
                if (showId != null) {
                    ShowDetailContent(showId = showId)
                }
            }

            entry<PlayerKey> { key ->
                PlayerContent(
                    contentId = key.contentId,
                    contentType = key.contentType,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToAuthRepair = { backStack += AuthRepairKey },
                )
            }

            entry<LibraryKey> {
                LibraryContent(
                    onMovieClick = { backStack += MovieDetailKey(it.value.value.toString()) },
                    onShowClick = { backStack += ShowDetailKey(it.value.value.toString()) },
                )
            }

            entry<DownloadsKey> {
                DownloadsContent()
            }

            entry<SettingsKey> {
                SettingsContent(
                    onNavigateToDiagnostics = { backStack += DiagnosticsKey },
                )
            }

            entry<DiagnosticsKey> {
                DiagnosticsContent()
            }

            entry<AuthRepairKey> {
                // Auth repair — wired in auth-persistence-shell
            }

            entry<OfflineLibraryKey> {
                OfflineLibraryContent(
                    onMovieClick = { backStack += MovieDetailKey(it.value.value.toString()) },
                    onShowClick = { backStack += ShowDetailKey(it.value.value.toString()) },
                )
            }
        },
    )
}

@Composable
private fun CatalogContent(onMovieClick: (Media.MediaId.Movie) -> Unit, onShowClick: (Media.MediaId.Show) -> Unit) {
    val storeOwner = remember("catalog_home") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: HomeViewModel = viewModel(key = "catalog_home") { HomeViewModel() }
        HomeScreen(
            viewModel = vm,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
    }
}

@Composable
private fun MovieDetailContent(movieId: Media.MediaId.Movie) {
    val storeOwner = remember(movieId) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: MovieDetailViewModel = viewModel(key = "movie_detail_${movieId.value.value}") {
            MovieDetailViewModel(mediaId = movieId)
        }
        MovieDetailScreen(viewModel = vm)
    }
}

@Composable
private fun ShowDetailContent(showId: Media.MediaId.Show) {
    val storeOwner = remember(showId) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: ShowDetailViewModel = viewModel(key = "show_detail_${showId.value.value}") {
            ShowDetailViewModel(mediaId = showId)
        }
        SeriesDetailScreen(viewModel = vm)
    }
}

@Composable
private fun LibraryContent(onMovieClick: (Media.MediaId.Movie) -> Unit, onShowClick: (Media.MediaId.Show) -> Unit) {
    val storeOwner = remember("library") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: LibraryViewModel = viewModel(key = "library") { LibraryViewModel() }
        LibraryScreen(
            viewModel = vm,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
    }
}

@Composable
private fun DownloadsContent() {
    val storeOwner = remember("downloads") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: DownloadsViewModel = viewModel(key = "downloads") { DownloadsViewModel() }
        DownloadsScreen(viewModel = vm)
    }
}

@Composable
private fun SettingsContent(onNavigateToDiagnostics: () -> Unit) {
    val storeOwner = remember("settings") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: SettingsViewModel = viewModel(key = "settings") { SettingsViewModel() }
        SettingsScreen(
            viewModel = vm,
            onNavigateToDiagnostics = onNavigateToDiagnostics,
        )
    }
}

@Composable
private fun DiagnosticsContent() {
    val storeOwner = remember("diagnostics") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: DiagnosticsViewModel = viewModel(key = "diagnostics") { DiagnosticsViewModel() }
        DiagnosticsScreen(viewModel = vm)
    }
}

@Composable
private fun OfflineLibraryContent(
    onMovieClick: (Media.MediaId.Movie) -> Unit,
    onShowClick: (Media.MediaId.Show) -> Unit,
) {
    val storeOwner = remember("offline_library") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: LibraryViewModel = viewModel(key = "offline_library") {
            LibraryViewModel(isLoggedIn = { false })
        }
        LibraryScreen(
            viewModel = vm,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
    }
}

@Composable
private fun PlayerContent(
    contentId: String,
    contentType: String,
    onNavigateBack: () -> Unit,
    onNavigateToAuthRepair: () -> Unit,
) {
    val mediaId = parseMediaId(contentId, contentType) ?: return
    val storeOwner = remember(contentId) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: PlayerViewModel = viewModel(key = contentId) {
            PlayerViewModel(mediaId = mediaId)
        }
        PlayerScreen(
            viewModel = vm,
            modifier = Modifier,
            onNavigateBack = onNavigateBack,
            onNavigateToAuthRepair = onNavigateToAuthRepair,
        )
    }
}

private fun parseMediaId(contentId: String, contentType: String): Media.MediaId? = when (contentType) {
    "movie" -> contentId.toIntOrNull()?.let { Media.MediaId.Movie(MovieId(it)) }
    "episode" -> contentId.toIntOrNull()?.let { Media.MediaId.Episode(EpisodeId(it)) }
    "show" -> contentId.toIntOrNull()?.let { Media.MediaId.Show(ShowId(it)) }
    else -> null
}
