package net.subsloth.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.CredentialsPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.preferences.AccountProfileStore
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Unit tests for [ValidatingSessionState]'s cold-start recovery contract,
 * exercised through a Ktor [MockEngine] and an in-memory
 * [CredentialsPort].
 *
 * The key guarantee under test: a relaunch with previously-stored
 * credentials publishes [Session.Authenticated] before the validation
 * round-trip completes, so the UI never falls back to the login screen for
 * a user who is already signed in.
 */
class ValidatingSessionStateTest {
    @Test
    fun `initial state is Restoring before recover runs`() = runTest {
        val (session, _) = createSession(okEngine())

        assertThat(session.state.value).isEqualTo(Session.Restoring)
    }

    @Test
    fun `recover without stored credentials resolves Anonymous without network`() = runTest {
        val calls = AtomicInteger()
        val (session, _) = createSession(okEngine(calls))

        session.recover()

        assertThat(session.state.value).isEqualTo(Session.Anonymous)
        assertThat(calls.get()).isEqualTo(0)
    }

    @Test
    fun `recover publishes stored session before validation completes`() = runTest {
        var stateAtValidationRequest: Session? = null
        lateinit var session: ValidatingSessionState
        val engine = MockEngine {
            // Capture what the session gate would render at the exact moment
            // the validation request goes out: it must already be
            // Authenticated, never Anonymous (the login screen).
            stateAtValidationRequest = session.state.value
            respondJson()
        }
        val (created, _) = createSession(engine, stored = Credentials("user@test.com", "password"))
        session = created

        session.recover()

        assertIs<Session.Authenticated>(stateAtValidationRequest)
        assertIs<Session.Authenticated>(session.state.value)
    }

    @Test
    fun `recover clears rejected credentials and returns to Anonymous`() = runTest {
        val (session, port) = createSession(unauthorizedEngine(), stored = Credentials("user@test.com", "wrong"))

        session.recover()

        assertThat(session.state.value).isEqualTo(Session.Anonymous)
        assertThat(port.cleared).isTrue()
        assertThat(port.stored).isNull()
    }

    @Test
    fun `recover keeps credentials and optimistic session on transient failure`() = runTest {
        val credentials = Credentials("offline@test.com", "password")
        val (session, port) = createSession(transientFailureEngine(), stored = credentials)

        session.recover()

        assertIs<Session.Authenticated>(session.state.value)
        assertThat(port.stored).isEqualTo(credentials)
        assertThat(port.cleared).isFalse()
    }

    @Test
    fun `recover resolves Anonymous when the credential read fails`() = runTest {
        val (session, _) = createSession(okEngine(), readFailure = NetworkError.NoConnectivity)

        session.recover()

        assertThat(session.state.value).isEqualTo(Session.Anonymous)
    }

    @Test
    fun `failed open resolves a pending restore to Anonymous`() = runTest {
        val (session, _) = createSession(unauthorizedEngine())

        val outcome = session.open(Credentials("user@test.com", "wrong"))

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat(session.state.value).isEqualTo(Session.Anonymous)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun createSession(
        engine: MockEngine,
        stored: Credentials? = null,
        readFailure: DomainError? = null,
    ): Pair<ValidatingSessionState, FakeCredentialsPort> {
        val port = FakeCredentialsPort(stored = stored, readFailure = readFailure)
        val session = ValidatingSessionState(
            credentialsPort = port,
            baseUrlProvider = { "http://localhost/" },
            accountProfileStore = AccountProfileStore(tempDataStore()),
            clock = FixedClock,
            engineOverride = engine,
        )
        return session to port
    }

    private fun okEngine(calls: AtomicInteger? = null): MockEngine = MockEngine {
        calls?.incrementAndGet()
        respondJson()
    }

    private fun unauthorizedEngine(): MockEngine = MockEngine {
        respond(
            content = "Unauthorized",
            status = HttpStatusCode.Unauthorized,
            headers = headersOf(HttpHeaders.ContentType, "text/plain"),
        )
    }

    private fun transientFailureEngine(): MockEngine = MockEngine {
        throw IOException("Simulated no connectivity")
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson() = respond(
        content = """{"movies":[]}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun tempDataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val file = File.createTempFile("validating_session_${UUID.randomUUID()}", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private class FakeCredentialsPort(var stored: Credentials? = null, var readFailure: DomainError? = null) :
        CredentialsPort {
        var cleared = false

        override suspend fun save(login: String, password: String): Outcome<Unit> {
            stored = Credentials(login, password)
            return Outcome.Success(Unit)
        }

        override suspend fun read(): Outcome<Credentials?> = readFailure
            ?.let { Outcome.Failure(it) }
            ?: Outcome.Success(stored)

        override suspend fun clear(): Outcome<Unit> {
            stored = null
            cleared = true
            return Outcome.Success(Unit)
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    }
}
