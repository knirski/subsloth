package net.subsloth.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.subsloth.auth.AuthRepairScreen
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.catalog.HomeScreen
import net.subsloth.catalog.HomeViewModel
import net.subsloth.catalog.SearchScreen
import net.subsloth.catalog.SearchViewModel
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.domain.port.Session
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.ui.AppNavKey
import net.subsloth.core.ui.AuthRepairKey
import net.subsloth.core.ui.CatalogKey
import net.subsloth.core.ui.DiagnosticsKey
import net.subsloth.core.ui.DownloadsKey
import net.subsloth.core.ui.EpisodeDetailKey
import net.subsloth.core.ui.LibraryKey
import net.subsloth.core.ui.MovieDetailKey
import net.subsloth.core.ui.OfflineLibraryKey
import net.subsloth.core.ui.PlayerKey
import net.subsloth.core.ui.SearchKey
import net.subsloth.core.ui.SettingsKey
import net.subsloth.core.ui.ShowDetailKey
import net.subsloth.core.ui.subslothNavConfig
import net.subsloth.details.EpisodeDetailScreen
import net.subsloth.details.EpisodeDetailViewModel
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
fun WebNavHost(runtime: WebRuntime, modifier: Modifier = Modifier, startDestination: AppNavKey = CatalogKey) {
    val backStack = rememberNavBackStack(subslothNavConfig, startDestination)

    // Browser back/forward buttons drive the in-app back stack. Each history
    // entry records the back-stack depth it represents. A popstate to a
    // shallower depth trims the stack; a forward entry deeper than the stack
    // cannot be reconstructed (its screen was popped in-app), so the entry is
    // realigned to the actual depth to keep later browser Back actions
    // consistent with the app back stack.
    rememberBrowserHistorySync { depth ->
        if (backStack.size > depth + 1) {
            while (backStack.size > depth + 1) backStack.removeLastOrNull()
        } else if (backStack.size <= depth) {
            replaceHistoryDepth(backStack.size - 1)
        }
    }

    val navigate: (AppNavKey) -> Unit = { key ->
        backStack += key
        pushHistoryDepth(backStack.size - 1)
    }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<CatalogKey> {
                CatalogContent(
                    runtime = runtime,
                    onSearchClick = { navigate(SearchKey) },
                    onMovieClick = { navigate(MovieDetailKey(it.value.value.toString())) },
                    onShowClick = { navigate(ShowDetailKey(it.value.value.toString())) },
                )
            }

            entry<SearchKey> {
                SearchContent(
                    runtime = runtime,
                    onMovieClick = { navigate(MovieDetailKey(it.value.value.toString())) },
                    onShowClick = { navigate(ShowDetailKey(it.value.value.toString())) },
                )
            }

            entry<MovieDetailKey> { key ->
                val movieId = key.movieId.toIntOrNull()?.let { Media.MediaId.Movie(MovieId(it)) }
                if (movieId != null) {
                    MovieDetailContent(
                        runtime = runtime,
                        movieId = movieId,
                        onNavigateBack = ::popHistory,
                        onPlayClick = {
                            navigate(PlayerKey(contentId = movieId.value.value.toString(), contentType = "movie"))
                        },
                    )
                }
            }

            entry<ShowDetailKey> { key ->
                val showId = key.showId.toIntOrNull()?.let { Media.MediaId.Show(ShowId(it)) }
                if (showId != null) {
                    ShowDetailContent(
                        runtime = runtime,
                        showId = showId,
                        onNavigateBack = ::popHistory,
                        onPlayClick = {
                            navigate(PlayerKey(contentId = showId.value.value.toString(), contentType = "show"))
                        },
                        onEpisodeClick = { episode -> navigate(EpisodeDetailKey(episode.id.value.toString())) },
                    )
                }
            }

            entry<EpisodeDetailKey> { key ->
                val episodeId = key.episodeId.toIntOrNull()?.let { Media.MediaId.Episode(EpisodeId(it)) }
                if (episodeId != null) {
                    EpisodeDetailContent(
                        runtime = runtime,
                        episodeId = episodeId,
                        onNavigateBack = ::popHistory,
                        onPlayClick = {
                            navigate(PlayerKey(contentId = episodeId.value.value.toString(), contentType = "episode"))
                        },
                    )
                }
            }

            entry<PlayerKey> { key ->
                PlayerContent(
                    runtime = runtime,
                    contentId = key.contentId,
                    contentType = key.contentType,
                    onNavigateBack = ::popHistory,
                    onNavigateToAuthRepair = { navigate(AuthRepairKey) },
                    onNavigateToNextEpisode = { nextId ->
                        val rawId = (nextId as? Media.MediaId.Episode)?.value?.value?.toString()
                        if (rawId != null) navigate(PlayerKey(contentId = rawId, contentType = "episode"))
                    },
                )
            }

            entry<AuthRepairKey> {
                AuthRepairContent(
                    runtime = runtime,
                    onRepaired = ::popHistory,
                )
            }

            entry<LibraryKey> {
                LibraryContent(
                    runtime = runtime,
                    onMovieClick = { navigate(MovieDetailKey(it.value.value.toString())) },
                    onShowClick = { navigate(ShowDetailKey(it.value.value.toString())) },
                )
            }

            entry<DownloadsKey> {
                DownloadsContent(runtime = runtime)
            }

            entry<SettingsKey> {
                SettingsContent(
                    runtime = runtime,
                    onNavigateToDiagnostics = { navigate(DiagnosticsKey) },
                )
            }

            entry<DiagnosticsKey> {
                DiagnosticsContent()
            }

            entry<OfflineLibraryKey> {
                OfflineLibraryContent(
                    runtime = runtime,
                    onMovieClick = { navigate(MovieDetailKey(it.value.value.toString())) },
                    onShowClick = { navigate(ShowDetailKey(it.value.value.toString())) },
                )
            }
        },
    )
}

@Composable
private fun CatalogContent(
    runtime: WebRuntime,
    onSearchClick: () -> Unit,
    onMovieClick: (Media.MediaId.Movie) -> Unit,
    onShowClick: (Media.MediaId.Show) -> Unit,
) {
    val storeOwner = remember("catalog_home") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: HomeViewModel = viewModel(key = "catalog_home") { runtime.createHomeViewModel() }
        HomeScreen(
            viewModel = vm,
            onSearchClick = onSearchClick,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
    }
}

@Composable
private fun SearchContent(
    runtime: WebRuntime,
    onMovieClick: (Media.MediaId.Movie) -> Unit,
    onShowClick: (Media.MediaId.Show) -> Unit,
) {
    val storeOwner = remember("catalog_search") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: SearchViewModel = viewModel(key = "catalog_search") {
            SearchViewModel(
                listCatalog = { runtime.listCatalog() },
                getDetails = { id -> runtime.getDetails(id) },
            )
        }
        SearchScreen(
            viewModel = vm,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
    }
}

@Composable
private fun MovieDetailContent(
    runtime: WebRuntime,
    movieId: Media.MediaId.Movie,
    onNavigateBack: () -> Unit,
    onPlayClick: () -> Unit,
) {
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
            MovieDetailViewModel(
                mediaId = movieId,
                getDetails = { runtime.getDetails(it) },
                listLibrary = { runtime.listLibrary() },
                listDownloads = { runtime.listDownloads() },
                listProgress = { runtime.listAccountPlaybackProgress() },
            )
        }
        MovieDetailScreen(
            viewModel = vm,
            onNavigateBack = onNavigateBack,
            onPlayClick = onPlayClick,
        )
    }
}

@Composable
private fun ShowDetailContent(
    runtime: WebRuntime,
    showId: Media.MediaId.Show,
    onNavigateBack: () -> Unit,
    onPlayClick: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
) {
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
            ShowDetailViewModel(
                mediaId = showId,
                getDetails = { runtime.getDetails(it) },
                listLibrary = { runtime.listLibrary() },
                listDownloads = { runtime.listDownloads() },
                listProgress = { runtime.listAccountPlaybackProgress() },
                listWatchedIds = { runtime.listWatchedContentIds() },
            )
        }
        SeriesDetailScreen(
            viewModel = vm,
            onNavigateBack = onNavigateBack,
            onPlayClick = onPlayClick,
            onEpisodeClick = onEpisodeClick,
        )
    }
}

@Composable
private fun EpisodeDetailContent(
    runtime: WebRuntime,
    episodeId: Media.MediaId.Episode,
    onNavigateBack: () -> Unit,
    onPlayClick: () -> Unit,
) {
    val storeOwner = remember(episodeId) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: EpisodeDetailViewModel = viewModel(key = "episode_detail_${episodeId.value.value}") {
            EpisodeDetailViewModel(
                mediaId = episodeId,
                getDetails = { runtime.getDetails(it) },
                isWatched = { runtime.isWatched(it) },
            )
        }
        EpisodeDetailScreen(
            viewModel = vm,
            onNavigateBack = onNavigateBack,
            onPlayClick = { onPlayClick() },
        )
    }
}

@Composable
private fun AuthRepairContent(runtime: WebRuntime, onRepaired: () -> Unit) {
    val storeOwner = remember("auth_repair") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: LoginViewModel = viewModel(key = "auth_repair") {
            LoginViewModel(
                sessionPort = runtime.sessionPort,
                readApiBaseUrl = { runtime.apiBaseUrlFlow() },
                saveApiBaseUrl = { url -> runtime.saveApiBaseUrl(url) },
            )
        }
        // PlayerScreen navigated here after detecting an auth failure, so
        // force this instance straight into AuthRepair (same as the other
        // platforms).
        LaunchedEffect(vm) {
            vm.retryAuth()
        }
        AuthRepairScreen(viewModel = vm, onRepaired = onRepaired)
    }
}

@Composable
private fun LibraryContent(
    runtime: WebRuntime,
    onMovieClick: (Media.MediaId.Movie) -> Unit,
    onShowClick: (Media.MediaId.Show) -> Unit,
) {
    val storeOwner = remember("library") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: LibraryViewModel = viewModel(key = "library") {
            LibraryViewModel(
                libraryPort = { runtime.listLibrary() },
                downloadsPort = { runtime.listDownloads() },
                listMovies = { runtime.listMovies() },
                listShows = { runtime.listShows() },
                listProgress = { runtime.listAccountPlaybackProgress() },
                isLoggedIn = { runtime.sessionPort.current() is Session.Authenticated },
                removeDownload = { localId -> runtime.removeDownload(localId) },
            )
        }
        LibraryScreen(
            viewModel = vm,
            onMovieClick = onMovieClick,
            onShowClick = onShowClick,
        )
    }
}

@Composable
private fun DownloadsContent(runtime: WebRuntime) {
    val storeOwner = remember("downloads") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: DownloadsViewModel = viewModel(key = "downloads") {
            DownloadsViewModel(
                listDownloads = { runtime.listDownloads() },
                listSeasonQueues = { runtime.listSeasonQueues() },
                // listProgress stays on its safe default: the shared
                // offline_playback_progress table has no contentType column
                // (same reasoning as Android/desktop).
                pauseDownload = { localId -> runtime.pauseDownload(localId) },
                resumeDownload = { localId -> runtime.resumeDownload(localId) },
                cancelDownload = { localId -> runtime.cancelDownload(localId) },
                retryDownload = { localId -> runtime.retryDownload(localId) },
                removeDownload = { localId ->
                    runtime.removeDownload(localId).getOrDefault(DownloadCommandOutcome.NoOp)
                },
            )
        }
        DownloadsScreen(viewModel = vm)
    }
}

@Composable
private fun SettingsContent(runtime: WebRuntime, onNavigateToDiagnostics: () -> Unit) {
    val storeOwner = remember("settings") {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val vm: SettingsViewModel = viewModel(key = "settings") {
            SettingsViewModel(
                profileKey = { runtime.currentProfileKey() },
                readSubtitleEnabled = { key -> runtime.readSubtitleEnabled(key) },
                readSubtitleLanguage = { key -> runtime.readSubtitleLanguage(key) },
                readQuality = { key -> runtime.readQuality(key) },
                readPlaybackSpeed = { key -> runtime.readPlaybackSpeed(key) },
                readDownloadsWifiOnly = { key -> runtime.readDownloadsWifiOnly(key) },
                writeSubtitleEnabled = { runtime.writeSubtitleEnabled(it) },
                writeSubtitleLanguage = { runtime.writeSubtitleLanguage(it) },
                writeQuality = { runtime.writeQuality(it) },
                writePlaybackSpeed = { runtime.writePlaybackSpeed(it) },
                writeDownloadsWifiOnly = { runtime.writeDownloadsWifiOnly(it) },
                deleteAllDownloads = { runtime.deleteAllDownloads() },
                clearPreferences = { runtime.clearPreferences() },
                clearLibrary = { runtime.clearLibrary() },
                clearCredentials = { runtime.clearCredentials() },
            )
        }
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
    runtime: WebRuntime,
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
            LibraryViewModel(
                libraryPort = { runtime.listLibrary() },
                listMovies = { runtime.listMovies() },
                listShows = { runtime.listShows() },
                // No session on this screen (logged-out state) — same
                // reasoning as the other platforms.
                isLoggedIn = { false },
            )
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
    runtime: WebRuntime,
    contentId: String,
    contentType: String,
    onNavigateBack: () -> Unit,
    onNavigateToAuthRepair: () -> Unit,
    onNavigateToNextEpisode: (Media.MediaId) -> Unit,
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
            PlayerViewModel(
                mediaId = mediaId,
                fetchVideoSource = { runtime.fetchVideoSource(it) },
                refreshStreamUrl = { runtime.playbackPort.refreshStreamUrl(it) },
                fetchEpisodes = { showId -> runtime.fetchEpisodesForShow(showId.value) },
                saveProgress = { mediaId, position, duration, mode ->
                    runtime.savePlaybackProgress(mediaId, position, duration, mode)
                },
                loadProgress = { runtime.loadPlaybackProgress(it) },
                onNavigateToNextEpisode = onNavigateToNextEpisode,
                onAuthFailure = { runtime.invalidateSession() },
                savePlaybackSpeed = { runtime.savePlaybackSpeed(it) },
                loadPlaybackSpeed = { runtime.loadPlaybackSpeed() },
                loadPreferredLanguage = { runtime.loadPreferredLanguage() },
                resolveShowIdForEpisode = { runtime.resolveShowIdForEpisode(it) },
                fetchSubtitleText = { runtime.fetchSubtitleText(it) },
            )
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
