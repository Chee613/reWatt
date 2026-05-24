package com.rewatt.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rewatt.app.data.HallSnapshot
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.TelemetryFreshness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

class DashboardViewModel(
    private val repository: TelemetryRepository,
) : ViewModel() {
    private val freshness = TelemetryFreshness()
    private val ticker = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState = combine(repository.dashboardState, ticker) { sensorState, now ->
        DashboardUiState(
            sensorState = sensorState,
            packetFreshness = freshness.evaluate(sensorState.lastValidPacketAtMillis, now),
            nowMillis = now,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState(),
    )

    private val _selectedHallLabel = MutableStateFlow("Hall 1")

    @OptIn(ExperimentalCoroutinesApi::class)
    val hallSnapshot: StateFlow<HallSnapshot?> = _selectedHallLabel
        .flatMapLatest { hallLabel -> repository.listenToHall(hallLabel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    fun selectHall(hallLabel: String) {
        _selectedHallLabel.value = hallLabel
    }
}

data class DashboardUiState(
    val sensorState: SensorDashboardState = SensorDashboardState(),
    val packetFreshness: PacketFreshness = PacketFreshness.WaitingForFirstPacket,
    val nowMillis: Long = System.currentTimeMillis(),
)

class DashboardViewModelFactory(
    private val repository: TelemetryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(repository) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}

