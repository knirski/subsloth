package net.subsloth

import co.touchlab.kermit.Logger
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.CredentialsPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.network.error.NetworkErrorClassifier
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.preferences.AccountProfileStore
import kotlin.time.Clock

/**
 * Production Android [SessionPort] implementation.
 *
 * Persists credentials via [credentialsPort] (Android Keystore-backed, see
 * `CredentialsStoreAdapter` / `CredentialStore` in `:core:preferences`) and
 * validates them using the Kodi plugin's normal authenticated startup
 * request rather than a dedicated auth-only probe: a fresh, short-lived,
 * Basic-Auth [io.ktor.client.HttpClient] built via [ClientFactory.create]
 * is used to call [Api.listMovies] for a single-item page. A 2xx response
 * means the credentials are accepted; any exception is classified via
 * [NetworkErrorClassifier] and, if it maps to an HTTP 401, further
 * translated into [AuthError.InvalidCredentials] (never accepted merely
 * because the fields were non-empty). Any other classified [NetworkError]
 * (timeout, no connectivity, 5xx, ...) is surfaced as-is since it does not
 * indicate the credentials themselves were rejected.
 *
 * Cold-start recovery is **not** performed implicitly on construction —
 * the constructor cannot suspend, and this class deliberately does not own
 * a background [kotlinx.coroutines.CoroutineScope]. Callers must invoke
 * [recover] exactly once during app startup, from a coroutine (e.g. the
 * hosting `Application`'s startup scope), before relying on [state]
 * reflecting a previously-persisted session. Until [recover] completes (or
 * if it is never called), [state] starts as [Session.Anonymous].
 *
 * @param credentialsPort persists and retrieves the user's login/password.
 * @param baseUrlProvider resolves the API base URL to validate against;
 *   callers should supply the same base URL the rest of the app uses
 *   (see how `MainActivity`'s `readApiBaseUrl` and `UserPreferences.apiBaseUrl()`
 *   resolve it, with a `BuildConfig.SUBSLOTH_API_BASE_URL` override) rather
 *   than relying on `ClientFactory`'s internal default.
 * @param accountProfileStore derives [Session.Authenticated.userId] via
 *   [AccountProfileStore.deriveProfileKey] — a non-reversible HMAC-SHA256 of
 *   the normalized login and an app-local, per-install salt, per the
 *   `auth-security` spec's "Account Profile Key Derivation" requirement.
 *   Unlike `InMemorySessionState`'s doc-labeled test/demo shortcut
 *   (`login.substringBefore('@')`), this never lets a directly recoverable
 *   fragment of the real login reach Room columns, DataStore keys, or any
 *   other persisted/logged surface.
 * @param clock source of the authenticated session's `openedAtEpochSeconds`
 *   timestamp; mirrors the pattern used by `InMemorySessionState`.
 * @param engineOverride test-support-only hook to inject a custom
 *   [HttpClientEngine] (e.g. Ktor's `MockEngine`) into the validation
 *   client instead of the real network engine. Defaults to `null`, in
 *   which case [ClientFactory.create] picks its normal engine; production
 *   callers must not pass this.
 */
class AndroidSessionState(
    private val credentialsPort: CredentialsPort,
    private val baseUrlProvider: suspend () -> String,
    private val accountProfileStore: AccountProfileStore,
    private val clock: Clock = Clock.System,
    private val engineOverride: HttpClientEngine? = null,
) : SessionPort {

    private val log = Logger.withTag("AndroidSessionState")

    private val _state: MutableStateFlow<Session> = MutableStateFlow(Session.Anonymous)
    override val state: StateFlow<Session> = _state.asStateFlow()

    override fun current(): Session = _state.value

    /**
     * Performs cold-start session recovery: reads any persisted credentials
     * and attempts Kodi-compatible validation before updating [state].
     *
     * - No stored credentials: [state] stays [Session.Anonymous]; no
     *   network call is made.
     * - Stored credentials validate successfully: [state] becomes
     *   [Session.Authenticated].
     * - Stored credentials are rejected ([AuthError.InvalidCredentials],
     *   i.e. an HTTP 401): they are cleared via [CredentialsPort.clear] and
     *   [state] stays [Session.Anonymous].
     * - Validation fails for any other reason (timeout, no connectivity,
     *   5xx, unexpected response, ...): credentials are left in place so a
     *   later retry can succeed once connectivity returns, and [state]
     *   stays [Session.Anonymous].
     *
     * Bounded by whatever timeout [ClientFactory]'s `HttpTimeout` plugin
     * already configures (currently a 30s request timeout) — no separate
     * timeout wrapper is layered on top.
     */
    suspend fun recover() {
        val stored = when (val outcome = credentialsPort.read()) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> {
                log.e { "Failed to read persisted credentials during recovery: ${outcome.error}" }
                null
            }
        } ?: return

        when (val result = validate(stored)) {
            is Outcome.Success -> {
                _state.value = Session.Authenticated(
                    userId = deriveUserId(stored.login),
                    openedAtEpochSeconds = clock.now().epochSeconds,
                    credentials = stored,
                )
            }

            is Outcome.Failure -> {
                if (result.error is AuthError.InvalidCredentials) {
                    log.i { "Persisted credentials rejected during recovery; clearing" }
                    credentialsPort.clear()
                } else {
                    log.w { "Recovery validation failed transiently, keeping credentials: ${result.error}" }
                }
                // Either way, the initial state stays Anonymous.
            }
        }
    }

    override suspend fun open(credentials: Credentials): Outcome<Unit> = when (val result = validate(credentials)) {
        is Outcome.Success -> {
            val saveOutcome = credentialsPort.save(credentials.login, credentials.password)
            if (saveOutcome is Outcome.Failure) {
                log.w { "Failed to persist credentials after successful validation: ${saveOutcome.error}" }
            }
            _state.value = Session.Authenticated(
                userId = deriveUserId(credentials.login),
                openedAtEpochSeconds = clock.now().epochSeconds,
                credentials = credentials,
            )
            Outcome.Success(Unit)
        }

        is Outcome.Failure -> result
    }

    override suspend fun close(): Outcome<Unit> {
        val clearOutcome = credentialsPort.clear()
        if (clearOutcome is Outcome.Failure) {
            log.w { "Failed to clear persisted credentials on close: ${clearOutcome.error}" }
        }
        _state.value = Session.Anonymous
        return Outcome.Success(Unit)
    }

    override suspend fun invalidate(): Outcome<Unit> = close()

    /**
     * Runs the Kodi-compatible startup validation call: builds a short-lived
     * authenticated client via [ClientFactory.create] and calls
     * [Api.listMovies] for a single-item page — the lightweight equivalent
     * of `CatalogRepository.sync()`'s normal authenticated request, not a
     * dedicated auth-only probe. The client is closed after the call since
     * it exists only to validate [credentials], not to serve app traffic.
     */
    private suspend fun validate(credentials: Credentials): Outcome<Unit> {
        val client = ClientFactory.create(
            login = credentials.login,
            password = credentials.password,
            baseUrl = baseUrlProvider(),
            engine = engineOverride,
        )
        return try {
            Api(client).listMovies(page = 1, perPage = 1)
            Outcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failure(e.toValidationError())
        } finally {
            client.close()
        }
    }

    private fun Exception.toValidationError(): DomainError {
        val networkError = NetworkErrorClassifier.classifyToNetwork(this)
        return if (networkError is NetworkError.HttpError && networkError.code == 401) {
            AuthError.InvalidCredentials
        } else {
            networkError
        }
    }

    private suspend fun deriveUserId(login: String): String = accountProfileStore.deriveProfileKey(login).value
}
