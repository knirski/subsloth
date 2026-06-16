package net.subsloth.core.ui

import androidx.lifecycle.ViewModel
import net.subsloth.core.domain.port.InMemorySessionState
import net.subsloth.core.domain.port.SessionPort

/**
 * Process-wide holder for a [SessionPort] that survives configuration
 * changes. The session is the single source of truth for "is the
 * user logged in?" — it must outlive Activity recreation so that
 * retained ViewModels and the gate observe the same instance.
 *
 * Use from each app's root composable:
 * ```
 * val root: RootContainerViewModel = viewModel()
 * SessionGate(sessionPort = root.sessionPort, login = ..., authenticated = ...)
 * ```
 *
 * Production wires the real, persistent-backed session; the default
 * is an in-memory implementation suitable for the screenshot suite
 * and the dev/demo build flavours.
 */
open class RootContainerViewModel(sessionPort: SessionPort? = null) : ViewModel() {
    val sessionPort: SessionPort = sessionPort ?: InMemorySessionState()
}
