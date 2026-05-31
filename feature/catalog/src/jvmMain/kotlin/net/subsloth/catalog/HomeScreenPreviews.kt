package net.subsloth.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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
