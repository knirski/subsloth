package subsloth.ui.adaptive

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.edgeToEdgePadding(): Modifier =
    this.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
    )
