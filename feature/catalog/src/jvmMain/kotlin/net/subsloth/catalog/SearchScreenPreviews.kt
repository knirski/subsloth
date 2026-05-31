package net.subsloth.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Previews
@Preview
@Composable
private fun SearchIdlePreview() {
    SearchScreen(
        viewModel = SearchViewModel(),
    )
}
