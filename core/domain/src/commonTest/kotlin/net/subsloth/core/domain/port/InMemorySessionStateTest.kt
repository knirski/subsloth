package net.subsloth.core.domain.port

import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InMemorySessionStateTest {
    @Test
    fun initial_state_is_anonymous() {
        val session = InMemorySessionState()
        assertIs<Session.Anonymous>(session.current())
        assertIs<Session.Anonymous>(session.state.value)
    }

    @Test
    fun open_transitions_to_authenticated() = runTest {
        val session = InMemorySessionState()
        val result = session.open(Credentials(login = "user@example.com", password = "secret"))
        assertEquals(Outcome.Success(Unit), result)
        val state = session.current()
        assertIs<Session.Authenticated>(state)
        assertEquals("user", state.userId)
        assertEquals("user@example.com", state.credentials.login)
        assertEquals("secret", state.credentials.password)
    }

    @Test
    fun open_synthesises_userId_from_email() = runTest {
        val session = InMemorySessionState()
        session.open(Credentials(login = "alice@subsloth.app", password = "pw"))
        val state = session.current()
        assertIs<Session.Authenticated>(state)
        assertEquals("alice", state.userId)
    }

    @Test
    fun open_with_email_no_local_part_uses_userId_user() = runTest {
        val session = InMemorySessionState()
        session.open(Credentials(login = "@nope.com", password = "pw"))
        val state = session.current()
        assertIs<Session.Authenticated>(state)
        assertEquals("user", state.userId)
    }

    @Test
    fun open_with_blank_password_returns_InvalidCredentials() = runTest {
        val session = InMemorySessionState()
        val result = session.open(Credentials(login = "alice@x.com", password = ""))
        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), result)
        assertIs<Session.Anonymous>(session.current())
    }

    @Test
    fun open_with_blank_login_returns_InvalidCredentials() = runTest {
        val session = InMemorySessionState()
        val result = session.open(Credentials(login = "", password = "pw"))
        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), result)
        assertIs<Session.Anonymous>(session.current())
    }

    @Test
    fun close_returns_to_anonymous() = runTest {
        val session = InMemorySessionState()
        session.open(Credentials(login = "user@example.com", password = "pw"))
        session.close()
        assertIs<Session.Anonymous>(session.current())
    }

    @Test
    fun close_on_anonymous_is_idempotent() = runTest {
        val session = InMemorySessionState()
        val result = session.close()
        assertEquals(Outcome.Success(Unit), result)
        assertIs<Session.Anonymous>(session.current())
    }

    @Test
    fun invalidate_returns_to_anonymous() = runTest {
        val session = InMemorySessionState()
        session.open(Credentials(login = "user@example.com", password = "pw"))
        session.invalidate()
        assertIs<Session.Anonymous>(session.current())
    }

    @Test
    fun consecutive_open_calls_update_credentials() = runTest {
        val session = InMemorySessionState()
        session.open(Credentials(login = "alice@x.com", password = "pw1"))
        session.open(Credentials(login = "bob@x.com", password = "pw2"))
        val state = session.current()
        assertIs<Session.Authenticated>(state)
        assertEquals("bob", state.userId)
        assertEquals("bob@x.com", state.credentials.login)
    }

    @Test
    fun state_is_observable_via_StateFlow() = runTest {
        val session = InMemorySessionState()
        // Initial value is Anonymous; subsequent transitions are
        // observed via the StateFlow value after each mutation.
        assertIs<Session.Anonymous>(session.state.value)
        session.open(Credentials(login = "u@x.com", password = "pw"))
        assertIs<Session.Authenticated>(session.state.value)
        session.invalidate()
        assertIs<Session.Anonymous>(session.state.value)
    }
}
