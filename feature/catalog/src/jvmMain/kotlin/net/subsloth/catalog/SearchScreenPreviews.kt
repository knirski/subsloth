package net.subsloth.catalog

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable

// Previews
@Preview
@Composable
private fun SearchIdlePreview() {
    SearchScreen(
        viewModel = SearchViewModel(),
    )
}
