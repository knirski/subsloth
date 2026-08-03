package net.subsloth.web

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WebDemoBanner(text: String = DEMO_BANNER_TEXT, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
    )
}
