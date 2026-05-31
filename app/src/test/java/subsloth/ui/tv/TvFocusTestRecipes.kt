package subsloth.ui.tv

/**
 * Test recipes for TV D-pad focus, media-key handling, and focus restoration.
 *
 * These tests require a TV emulator or Robolectric with Compose UI test support.
 * Run them via `./gradlew :app:testDebugUnitTest` with Robolectric configured, or
 * on a TV emulator as instrumented tests.
 *
 * Usage example for downstream features:
 *
 * ```kotlin
 * class MyFeatureTvFocusTest {
 *
 *     @get:Rule
 *     val tvFocusRule = TvFocusTestRule()
 *
 *     @Test
 *     fun dPadTraversalThroughRows() {
 *         tvFocusRule.setContent {
 *             Column {
 *                 Button(
 *                     onClick = {},
 *                     modifier = Modifier.size(100.dp).focusable().testTag("item1"),
 *                 ) {}
 *                 Button(
 *                     onClick = {},
 *                     modifier = Modifier.size(100.dp).focusable().testTag("item2"),
 *                 ) {}
 *             }
 *         }
 *         tvFocusRule.assertFocused("item1")
 *         tvFocusRule.pressDpadDown()
 *         tvFocusRule.assertFocused("item2")
 *     }
 * }
 * ```
 */
class TvFocusTestRecipes
