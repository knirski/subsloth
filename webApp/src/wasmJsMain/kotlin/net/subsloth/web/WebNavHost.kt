package net.subsloth.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
import net.subsloth.navigation.subslothNavConfig
import net.subsloth.player.PlayerScreen
import net.subsloth.player.PlayerViewModel

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
                // Catalog home — wired in catalog-details
            }

            entry<MovieDetailKey> { key ->
                // Movie detail — wired in catalog-details
            }

            entry<ShowDetailKey> { key ->
                // Show/series detail — wired in catalog-details
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

@Suppress("ViewModelInjection")
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
