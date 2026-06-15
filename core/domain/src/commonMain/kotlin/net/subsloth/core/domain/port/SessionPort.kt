package net.subsloth.core.domain.port

import kotlinx.coroutines.flow.StateFlow
import net.subsloth.core.model.error.Outcome

/**
 * The current session state.
 *
 * `Anonymous` is the initial state. `Authenticated` carries the
 * credentials and the user identifier; production wire injects the
 * `userId` from the API's `/users/me` response. The [openedAtEpochSeconds]
 * timestamp is useful for the UI ("session started 5 minutes ago")
 * and for session-age policies.
 */
sealed interface Session {
    data object Anonymous : Session

    data class Authenticated(val userId: String, val openedAtEpochSeconds: Long, val credentials: Credentials) :
        Session
}

/**
 * Port that holds the current session. The network shell writes to it
 * (on login, on 401). The UI reads from it (on navigation) via
 * [state].
 *
 * Implementations are provided by the platform shell; the
 * `:core:domain` module ships an in-memory default
 * ([InMemorySessionState]) for tests and the screenshot suite.
 */
interface SessionPort {
    /** Observable current session. */
    val state: StateFlow<Session>

    /** Snapshot accessor for non-Flow consumers. */
    fun current(): Session

    /**
     * Open a session with the given credentials. The [state] StateFlow
     * emits [Session.Authenticated] on success. Returns
     * [Outcome.Failure] if the credentials are rejected by the upstream
     * API.
     */
    fun open(credentials: Credentials): Outcome<Unit>

    /**
     * Close the current session (logout). The [state] StateFlow emits
     * [Session.Anonymous]. Idempotent: calling [close] on an anonymous
     * session is a no-op.
     */
    fun close(): Outcome<Unit>

    /**
     * Invalidate the current session (e.g. on a 401 from the upstream
     * API). The [state] StateFlow emits [Session.Anonymous]. Returns
     * the failure that triggered the invalidation (or `Outcome.Success(Unit)`
     * if there was no active session).
     */
    fun invalidate(): Outcome<Unit>
}
