package net.subsloth.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Top-level phone single-pane scaffold.
 *
 * Provides a full-screen surface with the current [MaterialTheme]
 * color scheme for phone-sized (compact) layouts.
 *
 * @param modifier Modifier for the root layout.
 * @param contentPadding Optional extra padding around content.
 * @param content The scrollable or static content for this pane.
 */
@Composable
fun PhoneScaffold(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (padding: PaddingValues) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        content(contentPadding)
    }
}

/**
 * Determines whether to show a bottom navigation bar based on window width.
 *
 * Phone-sized (compact) layouts typically use a bottom bar, while
 * wider layouts use a navigation rail or drawer.
 */
@Composable
fun shouldShowBottomNavigation(): Boolean =
    currentDeviceFormFactor() == DeviceFormFactor.Phone
