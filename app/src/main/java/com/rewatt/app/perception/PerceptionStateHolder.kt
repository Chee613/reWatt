package com.rewatt.app.perception

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/**
 * Process-wide holder for the live [PerceptionUiState]. Capture now runs inside
 * [CameraCaptureService] (a foreground service) so it keeps firing while the screen is off, but
 * the dashboard still needs to observe the latest frame, detections and exposure. The service
 * writes here and [PerceptionViewModel] exposes [state] to the UI, so both share one source of
 * truth without the service holding a reference to the Activity/ViewModel.
 */
class PerceptionStateHolder {
    private val _state = MutableStateFlow(PerceptionUiState())
    val state: StateFlow<PerceptionUiState> = _state.asStateFlow()

    fun update(transform: (PerceptionUiState) -> PerceptionUiState) = _state.update(transform)

    fun updateAndGet(transform: (PerceptionUiState) -> PerceptionUiState): PerceptionUiState =
        _state.updateAndGet(transform)
}
