package net.subsloth.core.ui

import androidx.navigation3.runtime.NavKey

/**
 * Pops [backStack] one level, or invokes [onExitOfflineLibrary] when the
 * offline library is the root destination.
 *
 * Every platform host wires this to both the visible back button and the
 * system back handler. The offline library is the only destination that can
 * be a root *outside* the authenticated flow (it is rendered from the login
 * gate when the user has offline downloads), so backing out of it must
 * return to login instead of being a no-op.
 */
fun <T : NavKey> navigateBack(backStack: MutableList<T>, onExitOfflineLibrary: () -> Unit = {}) {
    if (backStack.size > 1) {
        backStack.removeLastOrNull()
    } else if (backStack.lastOrNull() == OfflineLibraryKey) {
        onExitOfflineLibrary()
    }
}
