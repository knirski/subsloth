package net.subsloth.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centres and caps content on non-compact windows so forms and lists stay
 * readable when a desktop window, tablet, or TV is wider than the layout
 * needs. Compact windows render the content unchanged.
 *
 * The cap is a maximum, not a fixed width: narrower non-compact windows still
 * fill their available width.
 */
@Composable
fun AdaptiveContentFrame(modifier: Modifier = Modifier, maxWidth: Dp = 840.dp, content: @Composable () -> Unit) {
    if (currentWindowWidthClass() == WindowWidthClass.COMPACT) {
        Box(modifier = modifier.fillMaxSize()) { content() }
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
                content()
            }
        }
    }
}
