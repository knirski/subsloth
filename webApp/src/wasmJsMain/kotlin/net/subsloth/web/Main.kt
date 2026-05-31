package net.subsloth.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import net.subsloth.core.network.media.client.ClientConfig

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ClientConfig.useMock = true
    ComposeViewport(content = {
        WebNavHost()
    })
}
