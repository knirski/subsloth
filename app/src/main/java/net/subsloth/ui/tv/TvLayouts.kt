package net.subsloth.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Overscan-safe horizontal padding for TV screens.
 *
 * Android TV overscan can clip content near the screen edges.
 * Apply this as horizontal padding to room-level rows and content
 * to ensure all elements remain visible.
 */
val tvOverscanHorizontal = 48.dp

/**
 * Overscan-safe vertical padding for TV screens.
 */
val tvOverscanVertical = 32.dp

/**
 * Standard spacing between items in a TV row.
 */
val tvItemSpacing = 16.dp

/**
 * Standard spacing between rows in a TV layout.
 */
val tvRowSpacing = 32.dp

/**
 * Height for a TV action rail row.
 */
private val tvActionRailHeight = 56.dp

/**
 * A TV content row with a title and horizontally scrolling items.
 *
 * Each item is rendered by [itemContent], which receives the data [T].
 * The row is overscan-safe with [tvOverscanHorizontal] side padding.
 *
 * @param title The row title displayed above the items.
 * @param items The list of data items to display.
 * @param modifier Modifier for the row container.
 * @param itemContent Composable for rendering each item.
 */
@Composable
fun <T> TvRow(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    TvRowContent(
        title = title,
        modifier = modifier,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(tvItemSpacing),
            contentPadding =
                PaddingValues(
                    start = tvOverscanHorizontal,
                    end = tvOverscanHorizontal,
                ),
        ) {
            itemsIndexed(items) { index, item ->
                itemContent(item)
            }
        }
    }
}

/**
 * A large TV card for featured content (e.g. hero or spotlight items).
 *
 * Uses [Card] from TV Material 3 with default focusable appearance.
 *
 * @param onClick Called when the card is selected/clicked via D-pad.
 * @param modifier Modifier for the card.
 * @param cardWidth Width of the card (defaults to 320.dp for large cards).
 * @param cardHeight Height of the card (defaults to 180.dp for large cards).
 * @param content The visual content (e.g. an image composable) inside the card.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLargeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 320.dp,
    cardHeight: Dp = 180.dp,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(cardWidth).height(cardHeight),
        scale = CardDefaults.scale(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * A horizontal action rail for TV (e.g. tab bar or filter chips).
 *
 * Renders a horizontally scrollable list of actions that users can
 * navigate with D-pad left/right.
 *
 * @param actions The list of action labels to display.
 * @param selectedIndex The currently selected action index.
 * @param onActionSelected Called when an action is selected via D-pad.
 * @param modifier Modifier for the rail container.
 */
@Composable
fun TvActionRail(
    actions: List<String>,
    selectedIndex: Int,
    onActionSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tvItemSpacing),
        contentPadding =
            PaddingValues(
                start = tvOverscanHorizontal,
                end = tvOverscanHorizontal,
            ),
    ) {
        items(actions.size) { index ->
            TvActionChip(
                label = actions[index],
                isSelected = index == selectedIndex,
                onClick = { onActionSelect(index) }
            )
        }
    }
}

/**
 * A single action chip in a TV action rail.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvActionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(tvActionRailHeight),
        scale = CardDefaults.scale(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = if (isSelected) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Internal layout wrapper for a single TV row with title.
 */
@Composable
private fun TvRowContent(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(
            top = tvRowSpacing / 2,
            bottom = tvRowSpacing / 2,
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = tvOverscanHorizontal,
                end = tvOverscanHorizontal,
                bottom = 8.dp,
            ),
        )
        content()
    }
}
