package net.subsloth.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort

/**
 * The root navigation gate.
 *
 * Observes [SessionPort.state] and renders either the [login] composable
 * (when the user is anonymous) or the [authenticated] composable (when
 * authenticated). The gate is the only entry point into the app: any
 * composable placed in the [authenticated] slot is reachable only after
 * a successful login.
 *
 * Use from each app's root composable:
 * ```
 * SessionGate(
 *     sessionPort = appContainer.sessionPort,
 *     login = { LoginScreen() },
 *     authenticated = { AppRoot() },
 * )
 * ```
 *
 * @param sessionPort the application's session port.
 * @param login composable shown when the session is anonymous.
 * @param authenticated composable shown when the session is
 *   authenticated.
 */
@Composable
fun SessionGate(sessionPort: SessionPort, login: @Composable () -> Unit, authenticated: @Composable () -> Unit) {
    val state by remember(sessionPort) {
        sessionPort.state
    }.collectAsStateWithLifecycle()
    when (state) {
        is Session.Anonymous -> login()
        is Session.Authenticated -> authenticated()
    }
}

/**
 * Pure snapshot of the current session, for use from non-gate code
 * (e.g. conditional UI on a single screen). Re-renders only when the
 * session value changes.
 */
@Composable
fun rememberSession(sessionPort: SessionPort): State<Session> =
    remember(sessionPort) { sessionPort.state }.collectAsState()
