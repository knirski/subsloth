package net.subsloth.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import net.subsloth.core.domain.port.InMemorySessionState
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.ui.SessionGate

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = "SubSloth",
        state = windowState,
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                val sessionPort: SessionPort = remember { InMemorySessionState() }
                SessionGate(
                    sessionPort = sessionPort,
                    login = {
                        val viewModel: LoginViewModel = viewModel {
                            LoginViewModel(sessionPort = sessionPort)
                        }
                        LoginScreen(viewModel = viewModel, onNavigateToCatalog = {})
                    },
                    authenticated = { DesktopNavHost() },
                )
            }
        }
    }
}
