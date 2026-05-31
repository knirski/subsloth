package net.subsloth

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.subsloth.navigation.AppNavKey
import net.subsloth.navigation.AuthRepairKey
import net.subsloth.navigation.CatalogKey
import net.subsloth.navigation.DiagnosticsKey
import net.subsloth.navigation.DownloadsKey
import net.subsloth.navigation.LibraryKey
import net.subsloth.navigation.LoginKey
import net.subsloth.navigation.MovieDetailKey
import net.subsloth.navigation.OfflineLibraryKey
import net.subsloth.navigation.PlayerKey
import net.subsloth.navigation.SettingsKey
import net.subsloth.navigation.ShowDetailKey
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.player.PlayerScreen
import net.subsloth.player.PlayerViewModel

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
                // Catalog home — wired in catalog-details
            }

            entry<MovieDetailKey> { key ->
                // Movie detail — wired in catalog-details
            }

            entry<ShowDetailKey> { key ->
                // Show/series detail — wired in catalog-details
            }

            entry<PlayerKey> { key ->
                @Suppress("ViewModelInjection")
                val viewModel: PlayerViewModel = viewModel(
                    key = "player_${key.contentId}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            PlayerViewModel(
                                mediaId = parseMediaId(key.contentId, key.contentType)
                                    ?: error("Invalid player key: ${key.contentId}/${key.contentType}"),
                            ) as T
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
                // Library screen — wired in library-settings-diagnostics
            }

            entry<DownloadsKey> {
                // Downloads screen — wired in library-settings-diagnostics
            }

            entry<SettingsKey> {
                // Settings screen — wired in library-settings-diagnostics
            }

            entry<DiagnosticsKey> {
                // Diagnostics screen — wired in library-settings-diagnostics
            }

            entry<AuthRepairKey> {
                // Auth repair — wired in auth-persistence-shell
            }

            entry<OfflineLibraryKey> {
                // Offline library — wired in library-settings-diagnostics
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
    "movie" -> contentId.toLongOrNull()?.let { Media.MediaId.Movie(MovieId(it.toInt())) }
    "episode" -> contentId.toLongOrNull()?.let { Media.MediaId.Episode(EpisodeId(it.toInt())) }
    "show" -> contentId.toLongOrNull()?.let { Media.MediaId.Show(ShowId(it.toInt())) }
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
