package net.subsloth.auth

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Handles credential validation, offline library availability,
 * and auth repair routing.
 */
class LoginViewModel(
    private val hasStoredCredentials: () -> Boolean = { false },
    private val hasPlayableDownloads: () -> Boolean = { false },
    private val onLoginSuccess: () -> Unit = {},
    private val validateCredentials: suspend (String, String) -> Result<Unit> = { _, _ ->
        Result.success(Unit)
    },
    private val onLogout: () -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.LoginForm())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        checkInitialState()
    }

    private fun checkInitialState() {
        viewModelScope.launch {
            val hasCredentials = hasStoredCredentials()
            val hasOffline = hasPlayableDownloads()

            if (hasCredentials) {
                _uiState.value = LoginUiState.LoggedIn
                onLoginSuccess()
            } else {
                _uiState.value = LoginUiState.LoginForm(hasOfflineLibrary = hasOffline)
            }
        }
    }

    fun login(login: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            val result = validateCredentials(login.trim(), password)

            result.fold(
                onSuccess = {
                    _uiState.value = LoginUiState.LoggedIn
                    onLoginSuccess()
                },
                onFailure = { error ->
                    _uiState.value = LoginUiState.LoginForm(
                        hasOfflineLibrary = hasPlayableDownloads(),
                        error = mapToUiError(error),
                    )
                },
            )
        }
    }

    fun logout() {
        onLogout()
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

    companion object {
        fun mapToUiError(error: Throwable): UiError {
            val message = error.message
            return when {
                message?.contains("401", ignoreCase = true) == true ||
                    message?.contains("auth", ignoreCase = true) == true ->
                    UiError.AuthRequired(message)
                else -> UiError.Unknown(message)
            }
        }
    }
}
