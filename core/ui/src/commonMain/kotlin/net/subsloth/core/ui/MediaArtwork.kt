package net.subsloth.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Remote poster artwork.
 *
 * Poster URLs are signed and ephemeral, so they are only ever used as an
 * in-memory image request key; nothing here persists the URL. Renders nothing
 * when [url] is null/blank, letting callers keep their local placeholder
 * visible.
 *
 * The network fetcher comes from the host's Coil `ImageLoader` singleton
 * (configured in each app's entry point from `coil-network-ktor3`), which is
 * why this layer does not depend on a specific HTTP client.
 */
@Composable
fun MediaArtwork(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) return
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
