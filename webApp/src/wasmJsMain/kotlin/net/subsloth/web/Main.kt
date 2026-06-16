package net.subsloth.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.compose.viewModel
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.core.network.media.client.ClientConfig
import net.subsloth.core.ui.RootContainerViewModel
import net.subsloth.core.ui.SessionGate

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ClientConfig.useMock = true
    ComposeViewport(content = {
        val root: RootContainerViewModel = viewModel()
        val sessionPort = root.sessionPort
        SessionGate(
            sessionPort = sessionPort,
            login = {
                val viewModel: LoginViewModel = viewModel {
                    LoginViewModel(sessionPort = sessionPort)
                }
                LoginScreen(viewModel = viewModel, onNavigateToCatalog = {})
            },
            authenticated = { WebNavHost() },
        )
    })
}
