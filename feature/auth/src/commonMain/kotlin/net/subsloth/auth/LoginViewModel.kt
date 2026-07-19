package net.subsloth.auth

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.UiError
import net.subsloth.preferences.UserPreferences

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
    private val readApiBaseUrl: suspend () -> Flow<String> = { flowOf(UserPreferences.DEFAULT_API_BASE_URL) },
    private val saveApiBaseUrl: suspend (String) -> Unit = {},
) : ViewModel() {
    private val log = Logger.withTag("LoginViewModel")
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.LoginForm())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _apiBaseUrl = MutableStateFlow(UserPreferences.DEFAULT_API_BASE_URL)
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    init {
        checkInitialState()
        loadApiBaseUrl()
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
            delay(300)
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
        sessionPort.close()
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
