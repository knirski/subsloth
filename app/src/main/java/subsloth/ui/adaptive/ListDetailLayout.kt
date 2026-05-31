package subsloth.ui.adaptive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Adaptive list-detail layout for tablet and expanded-width screens.
 *
 * Wraps the Material 3 [ListDetailPaneScaffold] with app conventions.
 * - On expanded windows: both panes share the screen.
 * - On compact (phone) windows: behaves as a single-pane stack with
 *   animated transitions between list and detail.
 *
 * @param listContent Composable rendered in the list pane.
 * @param detailContent Composable rendered in the detail pane.
 * @param modifier Modifier for the scaffold root.
 * @param onBackFromDetail Called when the user navigates back from detail
 *   to list pane in single-pane (phone) mode.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SubSlothListDetailLayout(
    listContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBackFromDetail: () -> Unit = {},
) {
    val navigator = rememberListDetailPaneScaffoldNavigator()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch {
            navigator.navigateBack()
            onBackFromDetail()
        }
    }

    ListDetailPaneScaffold(
        modifier = modifier.fillMaxSize(),
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                listContent()
            }
        },
        detailPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                detailContent()
            }
        },
    )
}
