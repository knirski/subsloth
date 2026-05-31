package net.subsloth.catalog

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf

// Previews
@Preview
@Composable
private fun CatalogContentPreview() {
    CatalogContent(
        state = HomeUiState.Content(
            rows = persistentListOf(),
            selectedTab = HomeTab.MOVIES,
        ),
    )
}
