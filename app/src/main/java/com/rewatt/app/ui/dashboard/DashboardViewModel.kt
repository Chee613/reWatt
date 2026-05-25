package com.rewatt.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rewatt.app.data.EnergyAdvice
import com.rewatt.app.data.EnergyAdviceInput
import com.rewatt.app.data.EnergyAdviceState
import com.rewatt.app.data.EnergyAdviceStatus
import com.rewatt.app.data.GeminiEnergyAdvisor
import com.rewatt.app.data.HallSnapshot
import com.rewatt.app.data.OccupancyForecastProvider
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.data.WeatherRepository
import com.rewatt.app.data.WeatherState
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.TelemetryFreshness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: TelemetryRepository,
    private val weatherRepository: WeatherRepository,
    private val occupancyForecastProvider: OccupancyForecastProvider,
    private val energyAdvisor: GeminiEnergyAdvisor,
) : ViewModel() {
    private val freshness = TelemetryFreshness()
    private val ticker = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val energyAdvice = MutableStateFlow(EnergyAdviceState())
    private var energyAdviceJob: Job? = null

    val uiState = combine(
        repository.dashboardState,
        weatherRepository.weatherState,
        energyAdvice,
        ticker,
    ) { sensorState, weather, advice, now ->
        DashboardUiState(
            sensorState = sensorState,
            weather = weather,
            energyAdvice = advice,
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

    /** Resolve the device location and fetch outdoor weather. Safe to call repeatedly. */
    fun refreshWeather() {
        viewModelScope.launch { weatherRepository.refresh() }
    }

    /** Ask Gemini for an AC energy recommendation from current weather + indoor sensor + occupancy. */
    fun requestEnergyAdvice() {
        if (!energyAdvisor.isConfigured()) {
            energyAdvice.value = EnergyAdviceState(
                status = EnergyAdviceStatus.ConfigMissing,
                message = "Gemini API key missing. Add GEMINI_API_KEY to local.properties and rebuild.",
            )
            return
        }

        val weatherInfo = weatherRepository.weatherState.value.info
        val telemetry = repository.dashboardState.value.latestTelemetry
        val snapshot = hallSnapshot.value
        val hallLabel = _selectedHallLabel.value

        val indoorTempC = telemetry?.temperatureC?.toDouble() ?: snapshot?.temperatureC
        val indoorHumidity = telemetry?.humidityPercent?.toDouble() ?: snapshot?.humidityPercent
        val outdoorTempC = weatherInfo?.temperatureC
        val deltaT = if (outdoorTempC != null && indoorTempC != null) outdoorTempC - indoorTempC else null

        energyAdvice.update {
            it.copy(status = EnergyAdviceStatus.Pending, message = "Gemini is preparing an AC recommendation.")
        }

        energyAdviceJob?.cancel()
        energyAdviceJob = viewModelScope.launch {
            runCatching {
                val occupancy = occupancyForecastProvider.forecast(hallLabel, System.currentTimeMillis())
                val input = EnergyAdviceInput(
                    deltaTC = deltaT,
                    indoorTempC = indoorTempC,
                    outdoorTempC = outdoorTempC,
                    indoorHumidity = indoorHumidity,
                    outdoorHumidity = weatherInfo?.humidityPercent,
                    occupancy = occupancy,
                    conditionText = weatherInfo?.conditionText,
                )
                energyAdvisor.advise(input)
            }.onSuccess { advice: EnergyAdvice ->
                energyAdvice.value = EnergyAdviceState(
                    status = EnergyAdviceStatus.Complete,
                    message = advice.acAction,
                    advice = advice,
                    lastAdvisedAtMillis = System.currentTimeMillis(),
                )
            }.onFailure { error ->
                energyAdvice.value = EnergyAdviceState(
                    status = EnergyAdviceStatus.Failed,
                    message = "Energy advice failed: ${error.message ?: error::class.java.simpleName}",
                )
            }
        }
    }
}

data class DashboardUiState(
    val sensorState: SensorDashboardState = SensorDashboardState(),
    val weather: WeatherState = WeatherState(),
    val energyAdvice: EnergyAdviceState = EnergyAdviceState(),
    val packetFreshness: PacketFreshness = PacketFreshness.WaitingForFirstPacket,
    val nowMillis: Long = System.currentTimeMillis(),
)

class DashboardViewModelFactory(
    private val repository: TelemetryRepository,
    private val weatherRepository: WeatherRepository,
    private val occupancyForecastProvider: OccupancyForecastProvider,
    private val energyAdvisor: GeminiEnergyAdvisor,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(
                repository = repository,
                weatherRepository = weatherRepository,
                occupancyForecastProvider = occupancyForecastProvider,
                energyAdvisor = energyAdvisor,
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
