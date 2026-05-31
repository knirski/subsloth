package net.subsloth.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder landing screen for the desktop app.
 *
 * Shows the app name and provides navigation entry points.
 * Will be replaced with the full Catalog screen when wired up.
 */
@Composable
fun DesktopPlaceholderScreen(
    onNavigateToPlayer: (contentId: String, contentType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SubSloth",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Desktop Edition",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onNavigateToPlayer("1", "movie") }) {
                Text("Player Demo (Movie #1)")
            }
        }
    }
}
