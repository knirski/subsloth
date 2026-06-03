package net.subsloth.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticsViewModel : ViewModel() {
    private val _state = MutableStateFlow(DiagnosticsState.REDACTED)
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()
}
