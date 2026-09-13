package net.subsloth.ui.tv

/**
 * Test recipe pointer for TV D-pad focus traversal.
 *
 * The harness sends Android key events, so it runs as an **instrumented** test,
 * not a JVM unit test: `androidTestImplementation(project(":testing:tv-focus-harness"))`.
 * On a regular (phone) emulator, force the TV behaviour with
 * `CompositionLocalProvider(LocalIsTelevision provides true)`.
 *
 * The working example is
 * `net.subsloth.ui.TvFocusTraversalTest` in `androidApp/src/androidTest`, which
 * covers initial focus (back button, Home search action) and D-pad traversal
 * into media cards.
 *
 * Usage sketch for new screens:
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
 *             CompositionLocalProvider(LocalIsTelevision provides true) {
 *                 MyScreen()
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
