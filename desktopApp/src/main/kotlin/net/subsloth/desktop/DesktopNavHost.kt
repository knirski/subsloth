package net.subsloth.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                val viewModel = remember(screen.contentId) {
                    PlayerViewModel(
                        mediaId = Media.MediaId.Movie(
                            MovieId(screen.contentId.toIntOrNull() ?: 0),
                        ),
                    )
                }
                PlayerScreen(
                    viewModel = viewModel,
                    modifier = Modifier,
                    onNavigateBack = { currentScreen = DesktopScreen.Placeholder },
                    onNavigateToAuthRepair = { /* Not yet wired */ },
                )
            }
        }
    }
}

/** Simple navigation state for the desktop app. */
private sealed interface DesktopScreen {
    data object Placeholder : DesktopScreen
    data class Player(val contentId: String, val contentType: String) : DesktopScreen
}
