package net.subsloth.core.domain.port

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.Outcome

/**
 * Default in-memory [SessionPort] implementation.
 *
 * Production wires a persistent-backed implementation; this is the
 * no-frills reference for tests, the screenshot suite, and the
 * dev/demo build flavour.
 */
class InMemorySessionState(private val clock: kotlin.time.Clock = kotlin.time.Clock.System) : SessionPort {
    private val _state: MutableStateFlow<Session> = MutableStateFlow(Session.Anonymous)
    override val state: StateFlow<Session> = _state.asStateFlow()

    override fun current(): Session = _state.value

    override fun open(credentials: Credentials): Outcome<Unit> {
        if (credentials.login.isBlank() || credentials.password.isBlank()) {
            return Outcome.Failure(AuthError.InvalidCredentials)
        }
        // The shell would normally verify credentials against the upstream
        // API here. For this in-memory implementation we accept any
        // non-blank pair and synthesise a userId from the login email.
        val userId = credentials.login.substringBefore('@').ifBlank { "user" }
        _state.value = Session.Authenticated(
            userId = userId,
            openedAtEpochSeconds = clock.now().epochSeconds,
            credentials = credentials,
        )
        return Outcome.Success(Unit)
    }

    override fun close(): Outcome<Unit> {
        _state.value = Session.Anonymous
        return Outcome.Success(Unit)
    }

    override fun invalidate(): Outcome<Unit> {
        _state.value = Session.Anonymous
        return Outcome.Success(Unit)
    }
}
