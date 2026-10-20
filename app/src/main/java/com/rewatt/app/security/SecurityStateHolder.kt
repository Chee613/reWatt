package com.rewatt.app.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide CCTV state: written by [IntrusionMonitor]/[IntrusionAlarm], read by the Security tab. */
class SecurityStateHolder(initialArmed: Boolean) {
    private val mutableState = MutableStateFlow(SecurityUiState(armed = initialArmed))
    val state: StateFlow<SecurityUiState> = mutableState.asStateFlow()

    fun update(transform: (SecurityUiState) -> SecurityUiState) {
        mutableState.update(transform)
    }
}
