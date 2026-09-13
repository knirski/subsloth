package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import net.subsloth.catalog.HomeScreen
import net.subsloth.catalog.HomeViewModel
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.ui.LocalIsTelevision
import net.subsloth.core.ui.SubSlothBackButton
import org.junit.Rule
import org.junit.Test

/**
 * TV initial-focus behaviour (`LocalIsTelevision` + `tvInitialFocus`) on the
 * desktop UI-test host, where focus semantics are observable.
 *
 * D-pad traversal itself is provided by Compose's focus search, not app code;
 * the Android `TvFocusTraversalTest` (harness) covers remote-control key events
 * on devices that grant window focus.
 */
class TvFocusDesktopTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val movie =
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "Focus Movie",
            plot = null,
            availability = Availability.Available,
            rating = null,
            year = null,
            genres = persistentListOf(),
            durationMinutes = null,
            slug = null,
            imdbId = null,
            backdropUrl = null,
        )

    @Test
    fun tvBackButton_receivesInitialFocus() {
        composeRule.setContent {
            CompositionLocalProvider(LocalIsTelevision provides true) {
                MaterialTheme {
                    SubSlothBackButton(onClick = {})
                }
            }
        }

        composeRule.onNodeWithContentDescription("Back").assertIsFocused()
    }

    @Test
    fun tvHomeSearchAction_receivesInitialFocus() {
        val viewModel =
            HomeViewModel(
                catalogItems = { contentType ->
                    flowOf(if (contentType == "movie") listOf(movie) else emptyList())
                },
                savedState = mapOf("selectedTab" to "MOVIES"),
            )

        composeRule.setContent {
            CompositionLocalProvider(LocalIsTelevision provides true) {
                MaterialTheme {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }

        composeRule.onNodeWithContentDescription("Search").assertIsFocused()
    }
}
