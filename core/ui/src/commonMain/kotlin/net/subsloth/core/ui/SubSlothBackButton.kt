package net.subsloth.core.ui

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import subsloth.core.ui.generated.resources.Res
import subsloth.core.ui.generated.resources.action_back

/**
 * Shared back affordance for every screen except playback.
 *
 * A 48dp [IconButton] with an arrow glyph: a comfortable touch target
 * without the visual weight or vertical cost of a titled top app bar.
 * The playback screen keeps its own "Close" control instead.
 */
@Composable
fun SubSlothBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val backDescription = stringResource(Res.string.action_back)
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = backDescription },
    ) {
        Text(text = "←", style = MaterialTheme.typography.titleLarge)
    }
}
