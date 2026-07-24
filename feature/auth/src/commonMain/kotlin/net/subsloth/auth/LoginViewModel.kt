package net.subsloth.auth

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.subsloth.core.domain.LoginDefaults
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.UiError

/**
 * UI state for the login screen.
 *
 * Modelled as a sealed interface so invalid flag combinations
 * (e.g. isLoading && isLoggedIn) are impossible at the type level.
 */
@Stable
sealed interface LoginUiState {
    /** Credentials are being validated. */
    data object Loading : LoginUiState

    /** Login form is displayed. */
    @Immutable
    data class LoginForm(val hasOfflineLibrary: Boolean = false, val error: UiError? = null) : LoginUiState

    /** User is authenticated; navigation to catalog will follow. */
    data object LoggedIn : LoginUiState

    /** Expired or unexpected session state — repair UI is shown. */
    @Immutable
    data class AuthRepair(val hasOfflineLibrary: Boolean = false) : LoginUiState
}

/**
 * ViewModel for the login screen.
 *
 * The session itself is owned by [SessionPort]; the VM only mirrors the
 * session's auth state into the UI and exposes the `login` and
 * `logout` actions that delegate to the port.
 */
class LoginViewModel(
    private val sessionPort: SessionPort,
    private val hasPlayableDownloads: () -> Boolean = { false },
    private val onLogout: () -> Unit = {},
    private val readApiBaseUrl: suspend () -> Flow<String> = { flowOf(LoginDefaults.DEFAULT_API_BASE_URL) },
    private val saveApiBaseUrl: suspend (String) -> Unit = {},
) : ViewModel() {
    private val log = Logger.withTag("LoginViewModel")
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.LoginForm())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _apiBaseUrl = MutableStateFlow(LoginDefaults.DEFAULT_API_BASE_URL)
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    init {
        checkInitialState()
        loadApiBaseUrl()
        observeSessionState()
    }

    private fun loadApiBaseUrl() {
        viewModelScope.launch {
            readApiBaseUrl().collect { url ->
                _apiBaseUrl.value = url
            }
        }
    }

    fun onApiBaseUrlChanged(url: String) {
        _apiBaseUrl.value = url
        viewModelScope.launch {
            saveApiBaseUrl(url)
        }
    }

    private fun checkInitialState() {
        log.d { "Checking initial auth state" }
        val hasOffline = hasPlayableDownloads()
        when (sessionPort.current()) {
            is Session.Authenticated -> {
                _uiState.value = LoginUiState.LoggedIn
            }

            is Session.Anonymous -> {
                _uiState.value = LoginUiState.LoginForm(hasOfflineLibrary = hasOffline)
            }
        }
    }

    /**
     * Keeps [uiState] in sync with [SessionPort.state] for the lifetime of this
     * ViewModel instance, not just at construction time.
     *
     * This matters because Compose can cache and reuse the same [LoginViewModel]
     * instance across a `SessionGate`'s `login`/`authenticated` slots (they share a
     * `ViewModelStoreOwner` with no nested back-stack scoping). Without this
     * subscription, only [checkInitialState] (a one-shot read of
     * [SessionPort.current]) and this VM's own [logout] would ever move [uiState]
     * off [LoginUiState.LoggedIn], so any *external* trigger that flips the
     * session to [Session.Anonymous] (e.g. a logout initiated elsewhere, or a
     * future 401-triggered `invalidate()`) would leave a cached, already-`LoggedIn`
     * instance stuck showing stale state.
     *
     * The guards below exist so this collector composes cleanly with the rest of
     * the class rather than fighting it:
     *  - It only reacts to `Anonymous` by moving *out of* [LoginUiState.LoggedIn].
     *    It deliberately leaves [LoginUiState.AuthRepair] alone: that state already
     *    implies an anonymous session and is a deliberate, more specific state
     *    reached only via [retryAuth] — collapsing it back to [LoginUiState.LoginForm]
     *    on the same `Anonymous` emission that led to it would undo the user's
     *    explicit repair flow. It also leaves [LoginUiState.Loading] alone, since a
     *    session flip during [login] is [login]'s own transition to react to.
     *  - It only reacts to `Authenticated` by moving *out of* non-[LoginUiState.LoggedIn]
     *    states, so it can't stomp anything (and setting [LoginUiState.LoggedIn] when
     *    already there is a same-value no-op).
     *  - On subscription, [kotlinx.coroutines.flow.StateFlow.collect] immediately
     *    replays the current session value. That first emission always agrees with
     *    whatever [checkInitialState] just computed synchronously, so both guards
     *    above make this replay a no-op rather than a redundant/conflicting write.
     */
    private fun observeSessionState() {
        viewModelScope.launch {
            sessionPort.state.collect { session ->
                when (session) {
                    is Session.Anonymous -> {
                        if (_uiState.value is LoginUiState.LoggedIn) {
                            _uiState.value = LoginUiState.LoginForm(hasOfflineLibrary = hasPlayableDownloads())
                        }
                    }

                    is Session.Authenticated -> {
                        if (_uiState.value !is LoginUiState.LoggedIn) {
                            _uiState.value = LoginUiState.LoggedIn
                        }
                    }
                }
            }
        }
    }

    fun login(login: String, password: String) {
        log.d { "Login attempt" }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            when (val result = sessionPort.open(Credentials(login = login.trim(), password = password))) {
                is Outcome.Success -> {
                    log.d { "Login successful" }
                    _uiState.value = LoginUiState.LoggedIn
                }

                is Outcome.Failure -> {
                    val error = result.error
                    log.e { "Login failed: $error" }
                    _uiState.value = LoginUiState.LoginForm(
                        hasOfflineLibrary = hasPlayableDownloads(),
                        error = error.toUiError(),
                    )
                }
            }
        }
    }

    fun logout() {
        onLogout()
        viewModelScope.launch {
            sessionPort.close()
        }
        _uiState.value = LoginUiState.LoginForm(
            hasOfflineLibrary = hasPlayableDownloads(),
        )
    }

    fun retryAuth() {
        val currentForm = _uiState.value as? LoginUiState.LoginForm
        _uiState.value = LoginUiState.AuthRepair(
            hasOfflineLibrary = currentForm?.hasOfflineLibrary ?: hasPlayableDownloads(),
        )
    }

    fun dismissNeedsAuthRepair() {
        val authRepair = _uiState.value as? LoginUiState.AuthRepair
        _uiState.value = LoginUiState.LoginForm(
            hasOfflineLibrary = authRepair?.hasOfflineLibrary ?: hasPlayableDownloads(),
        )
    }
}

private fun DomainError.toUiError(): UiError = when (this) {
    is AuthError.InvalidCredentials -> UiError.AuthRequired()

    is AuthError.SessionExpired -> UiError.AuthRequired()

    is NetworkError.NoConnectivity -> UiError.Offline()

    is NetworkError.Timeout -> UiError.Offline()

    is NetworkError.HttpError -> when (code) {
        401 -> UiError.AuthRequired()
        404 -> UiError.NotFound()
        in 500..599 -> UiError.ServiceError()
        else -> UiError.Unknown()
    }

    is NetworkError.RateLimited -> UiError.ServiceError()

    is NetworkError.UnexpectedResponse -> UiError.Unknown()

    is DomainError -> UiError.Unknown()
}
