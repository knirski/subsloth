package net.subsloth.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.core.ui.RootContainerViewModel
import net.subsloth.core.ui.SessionGate
import net.subsloth.core.ui.theme.SubSlothTheme

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = "SubSloth",
        state = windowState,
    ) {
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
            val viewModel: LoginViewModel = viewModel {
                LoginViewModel(
                    sessionPort = sessionPort,
                    readApiBaseUrl = { container.apiBaseUrlFlow() },
                    saveApiBaseUrl = { url -> container.userPreferences.setApiBaseUrl(url) },
                )
            }
            LoginScreen(viewModel = viewModel, onNavigateToCatalog = {})
        },
        authenticated = { DesktopNavHost(container) },
    )
}
