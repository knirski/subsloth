package net.subsloth.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.player.PlayerScreen
import net.subsloth.player.PlayerViewModel

/**
 * Lightweight navigation host for the SubSloth web app.
 *
 * Uses simple state-based navigation (no Navigation3) since the
 * Navigation3 KMP runtime is not yet available for wasmJs.
 *
 * Feature screens are the same KMP composables used by Android and Desktop.
 * Navigation will be upgraded to Navigation3 when the KMP runtime is published.
 */
@Composable
fun WebNavHost(
    modifier: Modifier = Modifier,
) {
    var currentScreen by remember { mutableStateOf<WebScreen>(WebScreen.Placeholder) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val screen = currentScreen) {
            is WebScreen.Placeholder -> {
                WebPlaceholderScreen(
                    onNavigateToPlayer = { contentId, contentType ->
                        currentScreen = WebScreen.Player(contentId, contentType)
                    },
                )
            }

            is WebScreen.Player -> {
                val parsedId = screen.contentId.toIntOrNull()
                if (parsedId != null) {
                    PlayerContent(
                        contentId = screen.contentId,
                        parsedId = parsedId,
                        onNavigateBack = { currentScreen = WebScreen.Placeholder },
                    )
                }
            }
        }
    }
}

@Suppress("ViewModelInjection")
@Composable
private fun PlayerContent(
    contentId: String,
    parsedId: Int,
    onNavigateBack: () -> Unit,
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

/** Simple navigation state for the web app. */
private sealed interface WebScreen {
    data object Placeholder : WebScreen
    data class Player(val contentId: String, val contentType: String) : WebScreen
}
