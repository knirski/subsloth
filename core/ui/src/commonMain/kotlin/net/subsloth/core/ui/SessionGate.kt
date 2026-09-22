package net.subsloth.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort

/**
 * Pure routing decision: maps a [Session] value to a [Route].
 * Exposed for testing — the composable's `when` is trivially derived
 * from this exhaustive mapping.
 */
internal fun routeFor(state: Session): Route = when (state) {
    is Session.Restoring -> Route.Restoring
    is Session.Anonymous -> Route.Login
    is Session.Authenticated -> Route.Authenticated
}

internal enum class Route { Restoring, Login, Authenticated }

/**
 * The root navigation gate.
 *
 * Observes [SessionPort.state] and renders either the [login] composable
 * (when the user is anonymous) or the [authenticated] composable (when
 * authenticated). While cold-start session recovery is still deciding
 * ([Session.Restoring]) the [splash] slot is rendered instead, so a user
 * with valid stored credentials never sees the login screen flash before
 * the session is restored. The gate is the only entry point into the app:
 * any composable placed in the [authenticated] slot is reachable only after
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
 * @param splash composable shown while the session is still being restored;
 *   defaults to a centered progress indicator.
 */
@Composable
fun SessionGate(
    sessionPort: SessionPort,
    login: @Composable () -> Unit,
    authenticated: @Composable () -> Unit,
    splash: @Composable () -> Unit = { SessionRestoringIndicator() },
) {
    val state by remember(sessionPort) {
        sessionPort.state
    }.collectAsStateWithLifecycle()
    when (routeFor(state)) {
        Route.Restoring -> splash()
        Route.Login -> login()
        Route.Authenticated -> authenticated()
    }
}

/** Neutral full-screen placeholder rendered while the session is restoring. */
@Composable
private fun SessionRestoringIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Pure snapshot of the current session, for use from non-gate code
 * (e.g. conditional UI on a single screen). Re-renders only when the
 * session value changes.
 */
@Composable
fun rememberSession(sessionPort: SessionPort): State<Session> =
    remember(sessionPort) { sessionPort.state }.collectAsStateWithLifecycle()
