package net.subsloth.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import net.subsloth.core.ui.theme.SubSlothTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(content = {
        SubSlothTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                val app = remember { createWebDemoApp() }
                DisposableEffect(app) {
                    onDispose { app.close() }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    WebDemoBanner(text = app.bannerText)
                    WebNavHost(
                        runtime = app.runtime,
                        startDestination = app.startDestination,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    })
}
