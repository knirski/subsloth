package net.subsloth

import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.map
import net.subsloth.core.ui.AppNavKey
import net.subsloth.core.ui.AuthRepairKey
import net.subsloth.core.ui.CatalogKey
import net.subsloth.core.ui.DiagnosticsKey
import net.subsloth.core.ui.DownloadsKey
import net.subsloth.core.ui.LibraryKey
import net.subsloth.core.ui.MovieDetailKey
import net.subsloth.core.ui.OfflineLibraryKey
import net.subsloth.core.ui.PlayerKey
import net.subsloth.core.ui.SettingsKey
import net.subsloth.core.ui.ShowDetailKey
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.auth.AuthRepairScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.catalog.HomeScreen
import net.subsloth.catalog.HomeViewModel
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
import net.subsloth.preferences.UserPreferences
import net.subsloth.settings.DiagnosticsScreen
import net.subsloth.settings.DiagnosticsViewModel
import net.subsloth.settings.SettingsScreen
import net.subsloth.settings.SettingsViewModel

/**
 * Top-level navigation host for the app.
 *
 * Uses Navigation3 [NavDisplay] with typed [AppNavKey] routes.
 * - State survives process death via [rememberSaveableStateHolderNavEntryDecorator]
 *   which retains composable state (scroll position, selected tabs, etc.).
 * - Predictive back is handled by [NavDisplay.onBack] popping the stack;
 *   per-destination interception is available via [NavDestinationBackHandler].
 * - Feature screen composables are wired as [entryProvider] entries.
 *   Each entry receives its typed [AppNavKey] which carries route
 *   arguments (e.g. [MovieDetailKey.movieId]).
 */
@Composable
fun SubSlothNavHost(
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(CatalogKey)

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
                val app = LocalContext.current.applicationContext
                val container = (app as? SubSlothApplication)?.container ?: return@entry
                val viewModel: HomeViewModel = viewModel(
                    key = "catalog_home",
                    factory = HomeViewModelFactory { container.catalogRepository },
                )
                HomeScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                    onMovieClick = { backStack += MovieDetailKey(it.value.toString()) },
                    onShowClick = { backStack += ShowDetailKey(it.value.toString()) },
                )
            }

            entry<MovieDetailKey> { key ->
                val app = LocalContext.current.applicationContext
                val container = (app as? SubSlothApplication)?.container ?: return@entry
                val movieId = parseMediaId(key.movieId, "movie") as? Media.MediaId.Movie ?: return@entry
                val viewModel: MovieDetailViewModel = viewModel(
                    key = "movie_detail_${key.movieId}",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(
                                modelClass.cast(
                                    MovieDetailViewModel(
                                        mediaId = movieId,
                                        // Read container.catalogRepository live on every call
                                        // (not captured once) since AppContainer rebuilds it
                                        // whenever the session's credentials change.
                                        getDetails = { id -> container.catalogRepository.getDetails(id) },
                                    ),
                                ),
                            )
                    },
                )
                MovieDetailScreen(viewModel = viewModel, modifier = Modifier)
            }

            entry<ShowDetailKey> { key ->
                val app = LocalContext.current.applicationContext
                val container = (app as? SubSlothApplication)?.container ?: return@entry
                val showId = parseMediaId(key.showId, "show") as? Media.MediaId.Show ?: return@entry
                val viewModel: ShowDetailViewModel = viewModel(
                    key = "show_detail_${key.showId}",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(
                                modelClass.cast(
                                    ShowDetailViewModel(
                                        mediaId = showId,
                                        // Read container.catalogRepository live on every call
                                        // (not captured once) since AppContainer rebuilds it
                                        // whenever the session's credentials change.
                                        getDetails = { id -> container.catalogRepository.getDetails(id) },
                                    ),
                                ),
                            )
                    },
                )
                SeriesDetailScreen(viewModel = viewModel, modifier = Modifier)
            }

            entry<PlayerKey> { key ->
                val context = LocalContext.current
                val activity = remember(context) {
                    var current = context
                    while (current is ContextWrapper) {
                        if (current is ComponentActivity) break
                        current = current.baseContext
                    }
                    current as? ComponentActivity
                } ?: return@entry
                DisposableEffect(Unit) {
                    val originalOrientation = activity.requestedOrientation
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    onDispose {
                        activity.requestedOrientation = originalOrientation
                    }
                }

                val viewModel: PlayerViewModel = viewModel(
                    key = "player_${key.contentId}",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(
                                modelClass.cast(
                                    PlayerViewModel(
                                        mediaId = parseMediaId(key.contentId, key.contentType)
                                            ?: error("Invalid player key: ${key.contentId}/${key.contentType}"),
                                    ),
                                ),
                            )
                    },
                )
                PlayerScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToAuthRepair = { backStack += AuthRepairKey },
                )
            }

            entry<LibraryKey> {
                val viewModel: LibraryViewModel = viewModel(
                    key = "library",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(modelClass.cast(LibraryViewModel()))
                    },
                )
                LibraryScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                    onMovieClick = { backStack += MovieDetailKey(it.value.toString()) },
                    onShowClick = { backStack += ShowDetailKey(it.value.toString()) },
                )
            }

            entry<DownloadsKey> {
                val viewModel: DownloadsViewModel = viewModel(
                    key = "downloads",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(modelClass.cast(DownloadsViewModel()))
                    },
                )
                DownloadsScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                )
            }

            entry<SettingsKey> {
                val viewModel: SettingsViewModel = viewModel(
                    key = "settings",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(modelClass.cast(SettingsViewModel()))
                    },
                )
                SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                    onNavigateToDiagnostics = { backStack += DiagnosticsKey },
                )
            }

            entry<DiagnosticsKey> {
                val viewModel: DiagnosticsViewModel = viewModel(
                    key = "diagnostics",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(modelClass.cast(DiagnosticsViewModel()))
                    },
                )
                DiagnosticsScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                )
            }

            entry<AuthRepairKey> {
                // Self-contained: this entry builds its own LoginViewModel
                // (same DI pattern as MainActivity's `login` slot, sharing
                // the same container.sessionPort) rather than depending on
                // one threaded down from MainActivity. That keeps
                // PlayerScreen's existing onNavigateToAuthRepair callback
                // working as a standalone in-app screen regardless of
                // SessionGate's current route — see MainActivity.kt's
                // `login` slot for the complementary path: once a session
                // is actually invalidated, SessionGate stops rendering this
                // nav host entirely and shows LoginScreen (which itself
                // now renders AuthRepairScreen while uiState is AuthRepair).
                val app = LocalContext.current.applicationContext
                val container = (app as? SubSlothApplication)?.container ?: return@entry
                val viewModel: LoginViewModel = viewModel(
                    key = "auth_repair",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(
                                modelClass.cast(
                                    LoginViewModel(
                                        sessionPort = container.sessionPort,
                                        readApiBaseUrl = {
                                            container.userPreferences.apiBaseUrl().map { url ->
                                                if (url == UserPreferences.DEFAULT_API_BASE_URL &&
                                                    BuildConfig.SUBSLOTH_API_BASE_URL.isNotEmpty()
                                                ) {
                                                    BuildConfig.SUBSLOTH_API_BASE_URL
                                                } else {
                                                    url
                                                }
                                            }
                                        },
                                        saveApiBaseUrl = { url -> container.userPreferences.setApiBaseUrl(url) },
                                    ),
                                ),
                            )
                    },
                )
                // PlayerScreen navigated here because it already detected an
                // auth failure, so force this fresh instance straight into
                // AuthRepair rather than whatever checkInitialState() computed.
                LaunchedEffect(viewModel) {
                    viewModel.retryAuth()
                }
                AuthRepairScreen(
                    viewModel = viewModel,
                    onRepaired = { backStack.removeLastOrNull() },
                )
            }

            entry<OfflineLibraryKey> {
                val viewModel: LibraryViewModel = viewModel(
                    key = "offline_library",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            requireNotNull(modelClass.cast(LibraryViewModel(isLoggedIn = { false })))
                    },
                )
                LibraryScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                    onMovieClick = { backStack += MovieDetailKey(it.value.toString()) },
                    onShowClick = { backStack += ShowDetailKey(it.value.toString()) },
                )
            }
        },
    )
}

/**
 * Helper for per-destination predictive back handling.
 *
 * Place this inside a scene composable to intercept system back
 * (and TV remote Back) with custom behavior before falling through
 * to the default back-stack pop.
 *
 * Usage:
 * ```kotlin
 * entry<SomeKey> {
 *     NavDestinationBackHandler(enabled = someCondition) {
 *         // custom back action (e.g. confirm discard changes)
 *     }
 *     // screen content
 * }
 * ```
 */
internal fun parseMediaId(contentId: String, contentType: String): Media.MediaId? = when (contentType) {
    "movie" -> contentId.toLongOrNull()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
        ?.let { Media.MediaId.Movie(MovieId(it.toInt())) }
    "episode" -> contentId.toLongOrNull()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
        ?.let { Media.MediaId.Episode(EpisodeId(it.toInt())) }
    "show" -> contentId.toLongOrNull()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
        ?.let { Media.MediaId.Show(ShowId(it.toInt())) }
    else -> null
}

@Composable
fun NavDestinationBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled) {
        onBack()
    }
}
