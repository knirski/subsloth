package net.subsloth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.Session
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.Outcome
import net.subsloth.preferences.AccountProfileStore
import net.subsloth.preferences.CredentialStore
import net.subsloth.preferences.CredentialsStoreAdapter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Instrumented tests exercising the *real* [AndroidSessionState] wired to a
 * real, Keystore-backed [CredentialsStoreAdapter]/[CredentialStore] (the
 * same construction `AppContainer` uses in production), with network calls
 * intercepted via Ktor's [MockEngine] (injected through [AndroidSessionState]'s
 * test-only `engineOverride` constructor parameter) instead of hitting a
 * real backend.
 *
 * Unlike `ui/LoginFlowTest.kt` (which exercises [net.subsloth.auth.LoginViewModel]
 * against `InMemorySessionState`/a hand-rolled fake), these tests prove the
 * production adapter chain itself: encrypted persistence round-trips, and
 * cold-start [AndroidSessionState.recover] genuinely reads from — and, on
 * rejection, clears — that same encrypted storage.
 *
 * [EncryptedSharedPreferences][androidx.security.crypto.EncryptedSharedPreferences]
 * persists across test methods within this instrumented test process, so
 * [tearDown] clears the shared storage after every test to keep tests
 * independent.
 *
 * [accountProfileStore] wraps the process-wide [AppContainer.dataStore]
 * instance (via [SubSlothApplication]'s already-constructed container)
 * rather than opening a second `DataStore` against the same
 * `subsloth.preferences_pb` file, which throws at runtime — see
 * `LogoutCleanupInstrumentedTest`'s doc for the same constraint.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSessionStateInstrumentedTest {
    private lateinit var adapter: CredentialsStoreAdapter
    private lateinit var accountProfileStore: AccountProfileStore

    @Before
    fun setUp() {
        // Ensures a real Context is available and the app's Application
        // (which initialises the preferences module's AndroidContext
        // singleton that CredentialStore relies on) has run.
        val context = ApplicationProvider.getApplicationContext<SubSlothApplication>()
        adapter = CredentialsStoreAdapter(CredentialStore())
        accountProfileStore = AccountProfileStore(context.container.dataStore)
    }

    @After
    fun tearDown() =
        runTest {
            // Fresh CredentialStore instance, same underlying encrypted
            // SharedPreferences file — clears any state this test left behind
            // so it can't bleed into the next test method.
            CredentialsStoreAdapter(CredentialStore()).clear()
        }

    /** A 200 response shaped like [net.subsloth.core.network.media.api.model.MovieListResponse]. */
    private fun validLoginEngine(): MockEngine =
        MockEngine { _ ->
            respond(
                content = """{"movies":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

    /**
     * A 401 response with a plain-text (non-JSON) body, matching how an
     * auth-rejecting server typically responds to a bad Basic-Auth
     * credential. `ResponseValidationPlugin` throws a `ResponseValidationException`
     * carrying `NetworkError.HttpError(401, ...)` for *any* 401 response
     * regardless of content type (see its "3b." check), which
     * `AndroidSessionState` maps to [AuthError.InvalidCredentials] — so the
     * body shape here is deliberately non-JSON to also prove that mapping
     * doesn't depend on the error body happening to parse as
     * [net.subsloth.core.network.media.api.model.MovieListResponse].
     */
    private fun invalidLoginEngine(): MockEngine =
        MockEngine { _ ->
            respond(
                content = "Unauthorized",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

    /**
     * Simulates a transient, non-auth validation failure (e.g. no
     * connectivity) by throwing an [IOException] from the engine itself,
     * rather than returning any HTTP response. [NetworkErrorClassifier]'s
     * catch-all branch classifies this as [net.subsloth.core.model.error.NetworkError.NoConnectivity] —
     * deliberately *not* an HTTP 401 — so it must not be mistaken for
     * [AuthError.InvalidCredentials] by [AndroidSessionState.recover].
     */
    private fun transientFailureEngine(): MockEngine =
        MockEngine {
            throw IOException("Simulated no connectivity")
        }

    @Test
    fun validLogin_authenticatesAndPersistsCredentialsToRealStorage() =
        runTest {
            val credentials = Credentials("user@test.com", "password")
            val sessionState =
                AndroidSessionState(
                    credentialsPort = adapter,
                    baseUrlProvider = { "http://localhost/" },
                    accountProfileStore = accountProfileStore,
                    engineOverride = validLoginEngine(),
                )

            val outcome = sessionState.open(credentials)

            assertIs<Outcome.Success<Unit>>(outcome)
            assertIs<Session.Authenticated>(sessionState.state.value)

            // Fresh adapter instance -> proves a real Keystore round-trip, not
            // merely in-memory state on the original adapter.
            val persisted = CredentialsStoreAdapter(CredentialStore()).read()
            assertIs<Outcome.Success<Credentials?>>(persisted)
            assertEquals(credentials, persisted.value)
        }

    @Test
    fun invalidLogin_rejectsAndPersistsNothing() =
        runTest {
            val sessionState =
                AndroidSessionState(
                    credentialsPort = adapter,
                    baseUrlProvider = { "http://localhost/" },
                    accountProfileStore = accountProfileStore,
                    engineOverride = invalidLoginEngine(),
                )

            val outcome = sessionState.open(Credentials("user@test.com", "wrong-password"))

            assertIs<Outcome.Failure>(outcome)
            assertEquals(AuthError.InvalidCredentials, outcome.error)
            assertEquals(Session.Anonymous, sessionState.state.value)

            val persisted = CredentialsStoreAdapter(CredentialStore()).read()
            assertIs<Outcome.Success<Credentials?>>(persisted)
            assertNull(persisted.value)
        }

    @Test
    fun coldStart_recoversPersistedValidSessionFromRealStorage() =
        runTest {
            val credentials = Credentials("cold@test.com", "coldpass")
            adapter.save(credentials.login, credentials.password)

            // A brand new AndroidSessionState instance, over the same
            // underlying encrypted storage, proves recover() itself reads
            // persisted credentials rather than relying on open() having
            // been called on this object.
            val freshSessionState =
                AndroidSessionState(
                    credentialsPort = CredentialsStoreAdapter(CredentialStore()),
                    baseUrlProvider = { "http://localhost/" },
                    accountProfileStore = accountProfileStore,
                    engineOverride = validLoginEngine(),
                )

            freshSessionState.recover()

            assertIs<Session.Authenticated>(freshSessionState.state.value)
        }

    @Test
    fun coldStart_clearsRejectedPersistedCredentialsFromRealStorage() =
        runTest {
            val credentials = Credentials("expired@test.com", "expiredpass")
            adapter.save(credentials.login, credentials.password)

            val freshSessionState =
                AndroidSessionState(
                    credentialsPort = CredentialsStoreAdapter(CredentialStore()),
                    baseUrlProvider = { "http://localhost/" },
                    accountProfileStore = accountProfileStore,
                    engineOverride = invalidLoginEngine(),
                )

            freshSessionState.recover()

            assertEquals(Session.Anonymous, freshSessionState.state.value)

            val persisted = CredentialsStoreAdapter(CredentialStore()).read()
            assertIs<Outcome.Success<Credentials?>>(persisted)
            assertNull(persisted.value)
        }

    @Test
    fun coldStart_recoversAsAuthenticatedOnTransientValidationFailure() =
        runTest {
            val credentials = Credentials("offline@test.com", "offlinepass")
            adapter.save(credentials.login, credentials.password)

            val freshSessionState =
                AndroidSessionState(
                    credentialsPort = CredentialsStoreAdapter(CredentialStore()),
                    baseUrlProvider = { "http://localhost/" },
                    accountProfileStore = accountProfileStore,
                    engineOverride = transientFailureEngine(),
                )

            freshSessionState.recover()

            // Transient failure (no connectivity here): the previously-saved
            // credentials are trusted rather than treated as rejected.
            assertIs<Session.Authenticated>(freshSessionState.state.value)

            // Credentials must still be persisted -- a transient failure must
            // not clear them, unlike a genuine AuthError.InvalidCredentials
            // rejection (see coldStart_clearsRejectedPersistedCredentialsFromRealStorage).
            val persisted = CredentialsStoreAdapter(CredentialStore()).read()
            assertIs<Outcome.Success<Credentials?>>(persisted)
            assertEquals(credentials, persisted.value)
        }
}
