package subsloth.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag

/**
 * Custom saver for [FocusRequester] so it survives recomposition and
 * process death when used with [rememberSaveable].
 */
val focusRequesterSaver: Saver<FocusRequester, Any> = Saver(
    save = { Unit },
    restore = { FocusRequester() },
)

/**
 * Remembers a [FocusRequester] that survives process death via
 * [rememberSaveable]. Useful for TV D-pad focus restoration when
 * the user navigates back to a screen.
 */
@Composable
fun rememberTvFocusRequester(): FocusRequester {
    return rememberSaveable(saver = focusRequesterSaver) {
        FocusRequester()
    }
}

/**
 * Requests focus on this composable once it enters composition.
 *
 * Typical TV usage:
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .tvFocusRequester(focusRequester)
 *         .focusable()
 * ) { ... }
 * ```
 *
 * @param focusRequester The [FocusRequester] to use for the initial focus request.
 * @param modifier The modifier to apply.
 */
@Composable
fun Modifier.tvFocusRequester(focusRequester: FocusRequester): Modifier {
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    return this.then(Modifier.focusRequester(focusRequester))
}

/**
 * Sets up a focus restoration tag for this composable.
 *
 * When the user navigates back to this screen after going to detail,
 * the focus system can restore focus to the last focused element
 * identified by [tag].
 *
 * @param tag The test tag or identifier for focus restoration.
 */
@Composable
fun Modifier.focusRestorationTag(tag: String): Modifier =
    this.then(Modifier.testTag("focus_$tag"))

/**
 * Wraps content that should auto-request initial focus when first composed.
 *
 * This is useful for TV screens where the first focusable element
 * should receive focus automatically when the screen opens.
 *
 * @param focusRequester The focus requester for the initial element.
 * @param content The content that contains the focusable element.
 */
@Composable
fun AutoFocusInitial(
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.tvFocusRequester(focusRequester)) {
        content()
    }
}

/**
 * Requests focus on the given [FocusRequester] whenever any focus
 * direction event triggers the handler.
 *
 * This is useful for restoring focus to a specific element after
 * a dialog is dismissed or after navigating back to a screen.
 */
@Composable
fun FocusRestorationHandler(
    targetFocusRequester: FocusRequester,
) {
    LaunchedEffect(targetFocusRequester) {
        // Placeholder for future D-pad focus restoration logic.
        // Focus restoration will be wired when the TV focus harness
        // is added in a follow-up PR.
    }
}
