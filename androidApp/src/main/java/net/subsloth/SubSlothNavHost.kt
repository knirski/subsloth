package net.subsloth

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.subsloth.core.ui.AppNavKey
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
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.catalog.HomeScreen
import net.subsloth.catalog.HomeViewModel
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
    val backStack = rememberNavBackStack(LoginKey)

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
                // Login screen — wired in auth-persistence-shell
            }

            entry<CatalogKey> {
                val app = LocalContext.current.applicationContext
                val container = (app as? SubSlothApplication)?.container ?: return@entry
                val viewModel: HomeViewModel = viewModel(
                    key = "catalog_home",
                    factory = HomeViewModelFactory(container.catalogRepository),
                )
                HomeScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                    onMovieClick = { backStack += MovieDetailKey(it.value.toString()) },
                    onShowClick = { backStack += ShowDetailKey(it.value.toString()) },
                )
            }

            entry<MovieDetailKey> { key ->
                // Movie detail — wired in catalog-details
            }

            entry<ShowDetailKey> { key ->
                // Show/series detail — wired in catalog-details
            }

            entry<PlayerKey> { key ->
                val context = LocalContext.current
                val activity = context as? ComponentActivity ?: return@entry
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
                // Auth repair — wired in auth-persistence-shell
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
