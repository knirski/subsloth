package net.subsloth.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.CatalogContent
import net.subsloth.catalog.HomeTab
import net.subsloth.catalog.HomeUiState

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun HomeScreenScreenshot() {
    MaterialTheme {
        CatalogContent(
            state =
                HomeUiState.Content(
                    rows = persistentListOf(),
                    selectedTab = HomeTab.MOVIES,
                ),
        )
    }
}
