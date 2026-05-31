package net.subsloth.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.player.PlayerScreen
import net.subsloth.player.PlayerViewModel

/**
 * Desktop navigation host for SubSloth.
 *
 * Uses simple state-based navigation since Navigation3's KMP lifecycle
 * integration differs between targets. Feature screens are the same KMP
 * composables shared with Android and Web.
 *
 * Upgrading to Navigation3 is possible once the KMP Navigation3 runtime
 * stabilises and supports all desktop lifecycle scenarios.
 */
@Composable
fun DesktopNavHost(modifier: Modifier = Modifier) {
    var currentScreen by remember { mutableStateOf<DesktopScreen>(DesktopScreen.Placeholder) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val screen = currentScreen) {
            is DesktopScreen.Placeholder -> {
                DesktopPlaceholderScreen(
                    onNavigateToPlayer = { contentId, contentType ->
                        currentScreen = DesktopScreen.Player(contentId, contentType)
                    },
                )
            }

            is DesktopScreen.Player -> {
                val parsedId = screen.contentId.toIntOrNull()
                if (parsedId != null) {
                    PlayerContent(
                        contentId = screen.contentId,
                        parsedId = parsedId,
                        onNavigateBack = { currentScreen = DesktopScreen.Placeholder },
                    )
                }
            }
        }
    }
}

@Suppress("ViewModelInjection")
@Composable
private fun PlayerContent(contentId: String, parsedId: Int, onNavigateBack: () -> Unit) {
    // Scoped ViewModelStoreOwner so the ViewModel is cleared when navigating away.
    // Without this, viewModel() uses the global owner and the ViewModel stays cached,
    // leaking viewModelScope coroutines until the process dies.
    val storeOwner = remember(contentId) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides storeOwner,
    ) {
        val vm: PlayerViewModel = viewModel(key = contentId) {
            PlayerViewModel(
                mediaId = Media.MediaId.Movie(MovieId(parsedId)),
            )
        }
        PlayerScreen(
            viewModel = vm,
            modifier = Modifier.fillMaxSize(),
            onNavigateBack = onNavigateBack,
            onNavigateToAuthRepair = { /* Not yet wired */ },
        )
    }
}

/** Simple navigation state for the desktop app. */
private sealed interface DesktopScreen {
    data object Placeholder : DesktopScreen
    data class Player(val contentId: String, val contentType: String) : DesktopScreen
}
