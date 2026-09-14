package net.subsloth.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.subsloth.core.domain.port.Session

/**
 * Operational state for the diagnostics screen.
 *
 * [apiBaseUrl] is the effective API base URL the app is using and
 * [session] feeds the auth-state category; both are required so the
 * screen cannot silently render placeholder text.
 */
class DiagnosticsViewModel(apiBaseUrl: Flow<String>, session: Flow<Session>) : ViewModel() {
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(apiBaseUrl, session) { url, currentSession ->
                DiagnosticsState(
                    apiBaseUrl = url,
                    authStateCategory = currentSession.categoryLabel,
                )
            }.collect { _state.value = it }
        }
    }
}

private val Session.categoryLabel: String
    get() = when (this) {
        Session.Anonymous -> "anonymous"
        is Session.Authenticated -> "authenticated"
    }
