@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.subsloth.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.subsloth.core.ui.MediaArtwork

@Composable
internal fun isLandscapeWideScreen(): Boolean {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { containerSize.width.toDp().value }
    val heightDp = with(density) { containerSize.height.toDp().value }
    return widthDp > heightDp && widthDp >= 800f
}

/** Maximum height of the compact-layout backdrop. */
internal val DETAIL_HERO_MAX_HEIGHT = 300.dp

/**
 * Backdrop height for a [width]-wide hero: 16:9 until [DETAIL_HERO_MAX_HEIGHT]
 * caps it.
 *
 * Computed explicitly because `heightIn(...).aspectRatio(...)` lays the hero
 * out taller than its layout slot on wide screens (the aspect-ratio node wins
 * over the height cap), which makes the backdrop overlap the content below.
 */
internal fun detailHeroHeight(width: Dp): Dp = minOf(width * 9f / 16f, DETAIL_HERO_MAX_HEIGHT)

/**
 * The compact-layout backdrop: the title initial over a gradient, with the
 * backdrop artwork on top. Clipped to its computed height so the cropped
 * artwork cannot draw over the content below.
 */
@Composable
internal fun DetailHero(
    title: String,
    artworkUrl: String?,
    posterContentDescription: String,
    containerColor: Color,
    onContainerColor: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(detailHeroHeight(maxWidth))
                .clipToBounds()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(containerColor, MaterialTheme.colorScheme.surface),
                    ),
                )
                .semantics { contentDescription = posterContentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1),
                style = MaterialTheme.typography.displayLarge,
                color = onContainerColor.copy(alpha = 0.3f),
            )
            MediaArtwork(
                url = artworkUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
