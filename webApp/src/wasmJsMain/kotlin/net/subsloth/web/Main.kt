package net.subsloth.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.compose.viewModel
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.core.ui.LocalDownloadActionsEnabled
import net.subsloth.core.ui.SessionGate
import net.subsloth.core.ui.theme.SubSlothTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(content = {
        SubSlothTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                val app = remember { createWebApp() }
                DisposableEffect(app) {
                    onDispose { app.close() }
                }
                CompositionLocalProvider(LocalDownloadActionsEnabled provides false) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (app.showBanner) {
                            WebDemoBanner(text = app.bannerText)
                        }
                        when (app.mode) {
                            WebRuntimeMode.Demo -> WebNavHost(
                                runtime = app.runtime,
                                startDestination = app.startDestination,
                                modifier = Modifier.weight(1f),
                            )

                            // Production gates the nav host on the session exactly
                            // like desktop (`SessionGate`): login first, then the
                            // full navigation graph against the real adapters.
                            WebRuntimeMode.Production -> SessionGate(
                                sessionPort = app.runtime.sessionPort,
                                login = {
                                    val viewModel: LoginViewModel = viewModel {
                                        LoginViewModel(
                                            sessionPort = app.runtime.sessionPort,
                                            readApiBaseUrl = { app.runtime.apiBaseUrlFlow() },
                                            saveApiBaseUrl = { url -> app.runtime.saveApiBaseUrl(url) },
                                        )
                                    }
                                    LoginScreen(viewModel = viewModel, onNavigateToCatalog = {})
                                },
                                authenticated = {
                                    WebNavHost(
                                        runtime = app.runtime,
                                        startDestination = app.startDestination,
                                        modifier = Modifier.weight(1f),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    })
}
