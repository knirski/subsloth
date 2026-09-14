package net.subsloth.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.core.ui.OfflineLibraryKey
import net.subsloth.core.ui.RootContainerViewModel
import net.subsloth.core.ui.SessionGate
import net.subsloth.core.ui.theme.SubSlothTheme
import java.awt.Dimension

private const val MIN_WINDOW_WIDTH_PX = 480
private const val MIN_WINDOW_HEIGHT_PX = 640

fun main() {
    // Poster artwork is fetched through Coil; add the Ktor network fetcher
    // explicitly. Artwork URLs are signed, so no auth is required.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    runDesktopApp()
}

private fun runDesktopApp() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = "SubSloth",
        state = windowState,
    ) {
        // Keep the window wide enough for the two-column detail layouts and
        // the tab row; the layout adapts upward from here.
        LaunchedEffect(window) {
            window.minimumSize = Dimension(MIN_WINDOW_WIDTH_PX, MIN_WINDOW_HEIGHT_PX)
        }
        SubSlothTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopRoot()
            }
        }
    }
}

/**
 * Desktop root: owns the process-scoped [DesktopContainer] composition root
 * (constructed once per window via [remember]) and gates the nav host on the
 * real, persisted [net.subsloth.core.domain.port.SessionPort] state — the
 * same structure as androidApp's `MainActivity` + `SubSlothApplication`.
 *
 * The [container] parameter is the test seam for live E2E tests, which pass a
 * container rooted at a temporary data directory.
 */
@Composable
internal fun DesktopRoot(container: DesktopContainer = remember { DesktopContainer() }) {
    val root: RootContainerViewModel = viewModel {
        RootContainerViewModel(container.sessionPort)
    }
    val sessionPort = root.sessionPort
    SessionGate(
        sessionPort = sessionPort,
        login = {
            var showOfflineLibrary by remember { mutableStateOf(false) }
            if (showOfflineLibrary) {
                DesktopNavHost(
                    container = container,
                    startDestination = OfflineLibraryKey,
                    onExitOfflineLibrary = { showOfflineLibrary = false },
                )
            } else {
                val viewModel: LoginViewModel = viewModel {
                    LoginViewModel(
                        sessionPort = sessionPort,
                        readApiBaseUrl = { container.apiBaseUrlFlow() },
                        saveApiBaseUrl = { url -> container.userPreferences.setApiBaseUrl(url) },
                    )
                }
                LoginScreen(
                    viewModel = viewModel,
                    onNavigateToOfflineLibrary = { showOfflineLibrary = true },
                    onNavigateToCatalog = {},
                )
            }
        },
        authenticated = { DesktopNavHost(container) },
    )
}
