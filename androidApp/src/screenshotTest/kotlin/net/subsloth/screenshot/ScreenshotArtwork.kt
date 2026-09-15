package net.subsloth.screenshot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine

/**
 * Screenshot previews must never hit the network. Install a Coil loader whose
 * fake engine resolves every poster URL to a deterministic solid colour so the
 * artwork slots render like the real app instead of falling back to glyphs.
 */
fun installFakeArtworkLoader() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader
            .Builder(context)
            .components {
                add(
                    FakeImageLoaderEngine
                        .Builder()
                        .default(ColorImage(color = POSTER_COLOR.toArgb(), width = 120, height = 180))
                        .build(),
                )
            }.build()
    }
}

private val POSTER_COLOR = Color(0xFF7E57C2)
