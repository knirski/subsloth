package net.subsloth.desktop

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
import net.subsloth.auth.LoginViewModel
import net.subsloth.catalog.HomeScreen
import net.subsloth.catalog.HomeViewModel
import net.subsloth.catalog.SearchScreen
import net.subsloth.catalog.SearchViewModel
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.domain.port.Session
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
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
 * Desktop navigation host for SubSloth.
 *
 * Uses Navigation3 via [androidx.navigation3.runtime] with a
 * [SavedStateConfiguration] that registers all [AppNavKey] subtypes
 * for polymorphic serialization. Mirrors the Android nav host: every
 * entry constructs its ViewModel from [DesktopContainer]-provided
 * ports (read live at call time, so session rebuilds never leave a
 * stale adapter captured). Login is handled outside this host by the
 * [net.subsloth.core.ui.SessionGate] in [Main.kt].
 */
@Composable
fun DesktopNavHost(container: DesktopContainer, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(subslothNavConfig, CatalogKey)

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
                ScopedViewModel(key = "catalog_home") {
                    val vm: HomeViewModel = viewModel(key = "catalog_home") {
                        HomeViewModel(
                            catalogItems = { contentType ->
                                container.catalogRepository.catalogItems(contentType)
                            },
                            syncCatalog = { container.catalogRepository.sync() },
                            isCatalogStale = { container.catalogRepository.isStale() },
                        )
                    }
                    HomeScreen(
                        viewModel = vm,
                        onSearchClick = { backStack += SearchKey },
                        onMovieClick = { backStack += MovieDetailKey(it.value.value.toString()) },
                        onShowClick = { backStack += ShowDetailKey(it.value.value.toString()) },
                    )
                }
            }

            entry<SearchKey> {
                ScopedViewModel(key = "catalog_search") {
                    val vm: SearchViewModel = viewModel(key = "catalog_search") {
                        SearchViewModel(
                            listCatalog = { container.listAllMedia() },
                            // Read catalogRepository live on every call so
                            // session rebuilds never leave a stale adapter
                            // captured — same discipline as every entry.
                            getDetails = { id -> container.catalogRepository.getDetails(id) },
                        )
                    }
                    SearchScreen(
                        viewModel = vm,
                        onMovieClick = { backStack += MovieDetailKey(it.value.value.toString()) },
                        onShowClick = { backStack += ShowDetailKey(it.value.value.toString()) },
                    )
                }
            }

            entry<MovieDetailKey> { key ->
                val movieId = key.movieId.toIntOrNull()?.let { Media.MediaId.Movie(MovieId(it)) }
                if (movieId != null) {
                    ScopedViewModel(key = "movie_detail_${key.movieId}") {
                        val vm: MovieDetailViewModel = viewModel(key = "movie_detail_${key.movieId}") {
                            MovieDetailViewModel(
                                mediaId = movieId,
                                getDetails = { id -> container.catalogRepository.getDetails(id) },
                            )
                        }
                        MovieDetailScreen(
                            viewModel = vm,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onPlayClick = {
                                backStack += PlayerKey(
                                    contentId = movieId.value.value.toString(),
                                    contentType = "movie",
                                )
                            },
                        )
                    }
                }
            }

            entry<ShowDetailKey> { key ->
                val showId = key.showId.toIntOrNull()?.let { Media.MediaId.Show(ShowId(it)) }
                if (showId != null) {
                    ScopedViewModel(key = "show_detail_${key.showId}") {
                        val vm: ShowDetailViewModel = viewModel(key = "show_detail_${key.showId}") {
                            ShowDetailViewModel(
                                mediaId = showId,
                                getDetails = { id -> container.catalogRepository.getDetails(id) },
                            )
                        }
                        SeriesDetailScreen(
                            viewModel = vm,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onPlayClick = {
                                backStack += PlayerKey(
                                    contentId = showId.value.value.toString(),
                                    contentType = "show",
                                )
                            },
                            onEpisodeClick = { episode ->
                                backStack += EpisodeDetailKey(episode.id.value.toString())
                            },
                        )
                    }
                }
            }

            entry<EpisodeDetailKey> { key ->
                val episodeId = key.episodeId.toIntOrNull()?.let { Media.MediaId.Episode(EpisodeId(it)) }
                if (episodeId != null) {
                    ScopedViewModel(key = "episode_detail_${key.episodeId}") {
                        val vm: EpisodeDetailViewModel = viewModel(key = "episode_detail_${key.episodeId}") {
                            EpisodeDetailViewModel(
                                mediaId = episodeId,
                                getDetails = { id -> container.catalogRepository.getDetails(id) },
                            )
                        }
                        EpisodeDetailScreen(
                            viewModel = vm,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onPlayClick = { details ->
                                backStack += PlayerKey(
                                    contentId = details.id.value.value.toString(),
                                    contentType = "episode",
                                )
                            },
                        )
                    }
                }
            }

            entry<PlayerKey> { key ->
                PlayerContent(
                    container = container,
                    contentId = key.contentId,
                    contentType = key.contentType,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToAuthRepair = { backStack += AuthRepairKey },
                    onNavigateToNextEpisode = { nextId ->
                        val rawId = (nextId as? Media.MediaId.Episode)?.value?.value?.toString()
                        if (rawId != null) {
                            backStack += PlayerKey(contentId = rawId, contentType = "episode")
                        }
                    },
                )
            }

            entry<LibraryKey> {
                ScopedViewModel(key = "library") {
                    val vm: LibraryViewModel = viewModel(key = "library") {
                        LibraryViewModel(
                            libraryPort = container.libraryPortAdapter::listLibrary,
                            downloadsPort = container.downloadController::listDownloads,
                            listMovies = container::listMovies,
                            listShows = container::listShows,
                            listProgress = container::listAccountPlaybackProgress,
                            isLoggedIn = { container.sessionPort.current() is Session.Authenticated },
                            removeDownload = { localId ->
                                container.downloadController.remove(LocalMediaIdentifier(localId))
                            },
                        )
                    }
                    LibraryScreen(
                        viewModel = vm,
                        onMovieClick = { backStack += MovieDetailKey(it.value.value.toString()) },
                        onShowClick = { backStack += ShowDetailKey(it.value.value.toString()) },
                    )
                }
            }

            entry<DownloadsKey> {
                ScopedViewModel(key = "downloads") {
                    val vm: DownloadsViewModel = viewModel(key = "downloads") {
                        DownloadsViewModel(
                            listDownloads = container.downloadController::listDownloads,
                            listSeasonQueues = container::listSeasonQueues,
                            // listProgress intentionally left on its safe default here:
                            // the shared offline_playback_progress table has no
                            // contentType column, so a contentId alone can't
                            // disambiguate movie vs. episode ids — same reasoning as
                            // Android's DownloadsViewModel wiring.
                            pauseDownload = { localId ->
                                container.downloadController.pause(LocalMediaIdentifier(localId))
                                    .getOrDefault(DownloadCommandOutcome.NoOp)
                            },
                            resumeDownload = { localId ->
                                container.downloadController.resume(LocalMediaIdentifier(localId))
                                    .getOrDefault(DownloadCommandOutcome.NoOp)
                            },
                            cancelDownload = { localId ->
                                container.downloadController.cancel(LocalMediaIdentifier(localId))
                                    .getOrDefault(DownloadCommandOutcome.NoOp)
                            },
                            retryDownload = container::retryDownload,
                            removeDownload = { localId ->
                                container.downloadController.remove(LocalMediaIdentifier(localId))
                                    .getOrDefault(DownloadCommandOutcome.NoOp)
                            },
                        )
                    }
                    DownloadsScreen(viewModel = vm)
                }
            }

            entry<SettingsKey> {
                ScopedViewModel(key = "settings") {
                    val vm: SettingsViewModel = viewModel(key = "settings") {
                        SettingsViewModel(
                            profileKey = container::currentProfileKey,
                            readSubtitleEnabled = { key -> container.userPreferences.subtitleEnabled(key) },
                            readSubtitleLanguage = { key -> container.userPreferences.subtitleLanguage(key) },
                            readQuality = { key -> container.userPreferences.quality(key) },
                            readPlaybackSpeed = { key -> container.userPreferences.playbackSpeed(key) },
                            readDownloadsWifiOnly = { key -> container.userPreferences.downloadsWifiOnly(key) },
                            writeSubtitleEnabled = container::writeSubtitleEnabled,
                            writeSubtitleLanguage = container::writeSubtitleLanguage,
                            writeQuality = container::writeQuality,
                            writePlaybackSpeed = container::writePlaybackSpeed,
                            writeDownloadsWifiOnly = container::writeDownloadsWifiOnly,
                            clearPreferences = container::clearPreferences,
                            clearLibrary = container::clearLibrary,
                            clearCredentials = container::clearCredentials,
                            deleteAllDownloads = container::deleteAllDownloads,
                        )
                    }
                    SettingsScreen(
                        viewModel = vm,
                        onNavigateToDiagnostics = { backStack += DiagnosticsKey },
                    )
                }
            }

            entry<DiagnosticsKey> {
                ScopedViewModel(key = "diagnostics") {
                    val vm: DiagnosticsViewModel = viewModel(key = "diagnostics") { DiagnosticsViewModel() }
                    DiagnosticsScreen(viewModel = vm)
                }
            }

            entry<AuthRepairKey> {
                // Self-contained LoginViewModel (same DI pattern as Android's
                // entry): PlayerScreen navigated here after detecting an auth
                // failure, so force this instance straight into AuthRepair.
                ScopedViewModel(key = "auth_repair") {
                    val vm: LoginViewModel = viewModel(key = "auth_repair") {
                        LoginViewModel(
                            sessionPort = container.sessionPort,
                            readApiBaseUrl = { container.userPreferences.apiBaseUrl() },
                            saveApiBaseUrl = { url -> container.userPreferences.setApiBaseUrl(url) },
                        )
                    }
                    LaunchedEffect(vm) {
                        vm.retryAuth()
                    }
                    AuthRepairScreen(viewModel = vm, onRepaired = { backStack.removeLastOrNull() })
                }
            }

            entry<OfflineLibraryKey> {
                ScopedViewModel(key = "offline_library") {
                    val vm: LibraryViewModel = viewModel(key = "offline_library") {
                        LibraryViewModel(
                            libraryPort = container.libraryPortAdapter::listLibrary,
                            downloadsPort = container.downloadController::listDownloads,
                            listMovies = container::listMovies,
                            listShows = container::listShows,
                            // No session at all on this screen (reached from the
                            // logged-out state), so the account-scoped progress
                            // DAO isn't applicable — same reasoning as Android.
                            isLoggedIn = { false },
                            removeDownload = { localId ->
                                container.downloadController.remove(LocalMediaIdentifier(localId))
                            },
                        )
                    }
                    LibraryScreen(
                        viewModel = vm,
                        onMovieClick = { backStack += MovieDetailKey(it.value.value.toString()) },
                        onShowClick = { backStack += ShowDetailKey(it.value.value.toString()) },
                    )
                }
            }
        },
    )
}

/**
 * Scopes a nav entry's ViewModels to a private [ViewModelStoreOwner] that
 * is cleared when the entry leaves composition — the same lifetime pattern
 * the web nav host and Android's player entry use.
 */
@Composable
private fun ScopedViewModel(key: String, content: @Composable () -> Unit) {
    val storeOwner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner, content = content)
}

@Composable
private fun PlayerContent(
    container: DesktopContainer,
    contentId: String,
    contentType: String,
    onNavigateBack: () -> Unit,
    onNavigateToAuthRepair: () -> Unit,
    onNavigateToNextEpisode: (Media.MediaId) -> Unit,
) {
    val mediaId = parseMediaId(contentId, contentType)
    if (mediaId == null) {
        // Invalid navigation — do nothing
        return
    }
    ScopedViewModel(key = "player_$contentId") {
        val vm: PlayerViewModel = viewModel(key = contentId) {
            PlayerViewModel(
                mediaId = mediaId,
                fetchVideoSource = { id -> container.playbackPort.prepareSource(id) },
                refreshStreamUrl = { id -> container.playbackPort.refreshStreamUrl(id) },
                fetchEpisodes = { showId -> container.fetchEpisodesForShow(showId) },
                saveProgress = { id, positionSeconds, durationSeconds, playbackMode ->
                    container.savePlaybackProgress(id, positionSeconds, durationSeconds, playbackMode)
                },
                onNavigateToNextEpisode = onNavigateToNextEpisode,
                onAuthFailure = container::invalidateSession,
                savePlaybackSpeed = container::savePlaybackSpeed,
                loadPlaybackSpeed = container::loadPlaybackSpeed,
                loadPreferredLanguage = container::loadPreferredLanguage,
                resolveShowIdForEpisode = container::resolveShowIdForEpisode,
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
