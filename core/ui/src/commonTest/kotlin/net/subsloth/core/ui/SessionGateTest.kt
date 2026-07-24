package net.subsloth.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionGateTest {
    @Test
    fun routeFor_Anonymous_routes_to_Login() {
        val state: Session = Session.Anonymous
        assertEquals("Login", routeFor(state).name)
    }

    @Test
    fun routeFor_Authenticated_routes_to_Authenticated() {
        val state: Session = Session.Authenticated(
            userId = "alice",
            openedAtEpochSeconds = 1_700_000_000L,
            credentials = Credentials("alice@x.com", "pw"),
        )
        assertEquals("Authenticated", routeFor(state).name)
    }

    @Test
    fun sessionPort_state_reflects_open_and_invalidate() = runTest {
        val fake = FakeSessionPort()
        val state: StateFlow<Session> = fake.state
        val first = sessionName(state.value)
        // Initial state is Anonymous.
        assertEquals("Anonymous", first)
        fake.open(Credentials("alice@x.com", "pw"))
        assertEquals("Authenticated", sessionName(state.value))
        fake.invalidate()
        assertEquals("Anonymous", sessionName(state.value))
    }

    @Test
    fun fakeSessionPort_open_emits_Authenticated() = runTest {
        val fake = FakeSessionPort()
        val result = fake.open(Credentials("alice@x.com", "pw"))
        assertEquals(Outcome.Success(Unit), result)
        val state = fake.current()
        assertEquals("Authenticated", sessionName(state))
        assertEquals("alice", (state as Session.Authenticated).userId)
    }

    @Test
    fun fakeSessionPort_invalidate_returns_to_Anonymous() = runTest {
        val fake = FakeSessionPort()
        fake.open(Credentials("alice@x.com", "pw"))
        fake.invalidate()
        assertEquals("Anonymous", sessionName(fake.current()))
    }
}

private fun sessionName(session: Session): String = when (session) {
    Session.Anonymous -> "Anonymous"
    is Session.Authenticated -> "Authenticated"
}

private class FakeSessionPort : SessionPort {
    private val _state = MutableStateFlow<Session>(Session.Anonymous)
    override val state: StateFlow<Session> = _state
    override fun current(): Session = _state.value
    override suspend fun open(credentials: Credentials): Outcome<Unit> {
        if (credentials.login.isBlank() || credentials.password.isBlank()) {
            return Outcome.Failure(net.subsloth.core.model.error.AuthError.InvalidCredentials)
        }
        _state.value = Session.Authenticated(
            userId = credentials.login.substringBefore('@').ifBlank { "user" },
            openedAtEpochSeconds = 1_700_000_000L,
            credentials = credentials,
        )
        return Outcome.Success(Unit)
    }
    override suspend fun close(): Outcome<Unit> {
        _state.value = Session.Anonymous
        return Outcome.Success(Unit)
    }
    override suspend fun invalidate(): Outcome<Unit> {
        _state.value = Session.Anonymous
        return Outcome.Success(Unit)
    }
}
