package net.subsloth.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import subsloth.core.ui.generated.resources.Res
import subsloth.core.ui.generated.resources.downloads_unavailable_browser

/**
 * Whether download actions can make progress on this platform.
 *
 * Browsers have no byte-transfer worker, so web roots provide `false` and
 * download affordances render [DownloadUnavailableNotice] instead of
 * controls that would enqueue rows that can never complete.
 */
val LocalDownloadActionsEnabled = staticCompositionLocalOf { true }

/** Explains that downloads are unavailable on this platform. */
@Composable
fun DownloadUnavailableNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.downloads_unavailable_browser),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
