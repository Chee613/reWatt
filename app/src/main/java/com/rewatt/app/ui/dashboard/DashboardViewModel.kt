package com.rewatt.app.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rewatt.app.data.EnergyAdvice
import com.rewatt.app.notify.EnergyAlertNotifier
import com.rewatt.app.notify.NowActionNotifier
import com.rewatt.app.notify.ScheduledActionScheduler
import com.rewatt.app.data.EnergyAdviceInput
import com.rewatt.app.data.EnergyAdviceState
import com.rewatt.app.data.EnergyAdviceStatus
import com.rewatt.app.data.EnergyWasteSummary
import com.rewatt.app.data.EnergyWasteWorker
import com.rewatt.app.data.GeminiEnergyAdvisor
import com.rewatt.app.data.LearnedSegment
import com.rewatt.app.data.RecentCapture
import com.rewatt.app.data.HallSnapshot
import com.rewatt.app.data.SensorNodeSnapshot
import com.rewatt.app.data.OccupancyForecastProvider
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.data.WeatherRepository
import com.rewatt.app.data.WeatherState
import com.rewatt.app.perception.millisUntilNextBoundary
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.TelemetryFreshness
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val appContext: Context,
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

    private val _hallSchedules = MutableStateFlow<Map<String, Array<IntArray>>>(emptyMap())
    val hallSchedules: StateFlow<Map<String, Array<IntArray>>> = _hallSchedules

    private val _hallMeanSchedules = MutableStateFlow<Map<String, Array<IntArray>>>(emptyMap())
    val hallMeanSchedules: StateFlow<Map<String, Array<IntArray>>> = _hallMeanSchedules

    private val _hallMinuteSchedules = MutableStateFlow<Map<String, Array<IntArray>>>(emptyMap())
    val hallMinuteSchedules: StateFlow<Map<String, Array<IntArray>>> = _hallMinuteSchedules

    private val _learnedSchedules = MutableStateFlow<Map<String, List<List<LearnedSegment>>>>(emptyMap())
    val learnedSchedules: StateFlow<Map<String, List<List<LearnedSegment>>>> = _learnedSchedules

    // Venue-wide sensor fleet (Admin Manage tab): merged live nodes across all halls.
    private val _sensorFleet = MutableStateFlow<Map<String, List<SensorNodeSnapshot>>>(emptyMap())
    val sensorFleet: StateFlow<List<SensorNodeSnapshot>> = _sensorFleet
        .map { byHall -> byHall.values.flatten() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val recentCaptures: StateFlow<List<RecentCapture>> =
        repository.recentCapturesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val wasteSummary: StateFlow<EnergyWasteSummary?> =
        repository.wasteSummaryFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    // True while a manual wasted-energy recompute is in flight (drives the card's spinning ↻).
    private val _wasteRefreshing = MutableStateFlow(false)
    val wasteRefreshing: StateFlow<Boolean> = _wasteRefreshing

    /**
     * Manually recompute yesterday's wasted energy: enqueue a one-time [EnergyWasteWorker] (which
     * re-counts empty+lit samples and re-asks Gemini for kWh/RM, then writes Firestore). The card
     * updates via [wasteSummary]; the spinner clears when a newer summary lands or after a timeout.
     */
    fun refreshWasteSummary() {
        if (_wasteRefreshing.value) return
        _wasteRefreshing.value = true
        val before = wasteSummary.value?.generatedAtMillis ?: 0L
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "energy_waste_refresh",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<EnergyWasteWorker>().build(),
        )
        viewModelScope.launch {
            withTimeoutOrNull(25_000L) {
                repository.wasteSummaryFlow().first { (it?.generatedAtMillis ?: 0L) > before }
            }
            _wasteRefreshing.value = false
        }
    }

    // Demo override (fsktm screen-recording only): fake vitals for the LIVE blocks + NOW notification.
    private val _demoVitals = MutableStateFlow<DemoVitals?>(null)
    // Gate so the too-cold notification fires only once the animation finishes (not mid-ramp).
    private val _demoNotifyArmed = MutableStateFlow(false)
    private var demoJob: Job? = null

    /**
     * Screen-recording demo: ramp the [hallLabel] room's vitals comfortable→too-cold over ~10s (occupied),
     * hold ~8s so the THI "Set AC" notification is visible, then ramp back and clear. Drives both the LIVE
     * Temp/Humidity/Occupancy blocks (via [uiState].demoVitals) and the NOW notification (via
     * [notifyNowActions], which reads [_demoVitals]).
     */
    fun startTooColdDemo(hallLabel: String) {
        demoJob?.cancel()
        _demoNotifyArmed.value = false   // clears any prior notification while the new ramp runs
        demoJob = viewModelScope.launch {
            val people = 28
            val steps = 60
            val stepMs = 100L                // 60 * 100ms ≈ 6s ramp
            suspend fun ramp(fromT: Double, toT: Double, fromH: Double, toH: Double) {
                for (i in 0..steps) {
                    val f = i.toDouble() / steps
                    _demoVitals.value = DemoVitals(
                        hallLabel = hallLabel,
                        personCount = people,
                        temperatureC = fromT + (toT - fromT) * f,
                        humidityPercent = fromH + (toH - fromH) * f,
                    )
                    delay(stepMs)
                }
            }
            ramp(24.0, 21.5, 58.0, 65.0)   // comfortable (THI ≈ 22) → too cold (THI ≈ 20) over ~6s
            // Fire the persistent, unswipeable too-cold notification (with Done) right as the
            // animation finishes. The vitals hold at too-cold and the notification stays until Done.
            _demoNotifyArmed.value = true
        }
    }

    // Escalation bookkeeping (admin): the current actionable signature, when it first appeared, and
    // the last signature we posted a notification for. Drive both the persistent notification and the
    // "left un-Done too long" full-screen escalation.
    private var lastNotifiedActionSig: String? = null
    private var escActionableSig: String? = null
    private var escPendingSince: Long = 0L

    init {
        listenToAllHallSchedules()
        listenToAllHallMeanSchedules()
        listenToAllHallMinuteSchedules()
        listenToAllLearnedSchedules()
        listenToAllHallNodes()
        notifyNowActions()
        scheduleTimelineNotifications()
        startAutoEnergyAdvice()
    }

    /**
     * Admin: cache the per-hall schedule and (re)arm exact-time alarms so every Today's-Schedule row
     * fires as a notification at its purple-box time — even when the app is later closed. Runs whenever
     * the schedule changes (and on first emission, which for the fsktm demo uses the hardcoded fallback).
     */
    private fun scheduleTimelineNotifications() {
        viewModelScope.launch {
            _hallMinuteSchedules.collect { grids ->
                ScheduledActionScheduler.updateSchedules(appContext, grids)
            }
        }
    }

    /**
     * Admin: derive each hall's NOW action from the SAME shared schedule the Action tab uses plus the
     * sensor's relayed live state (latest node snapshot), then:
     *  1. post/refresh the persistent NOW-action notification (with Done) when the actionable set changes;
     *  2. ESCALATE to the full-screen "lights still on, no people" alert when a *Turn off (empty room)*
     *     recommendation has been left un-Done for [ESCALATION_MILLIS] — the alert shows the exact action
     *     and space. Done (notification or full-screen) acknowledges and clears both.
     * Sensor phones stay silent (gated inside [NowActionNotifier] / [EnergyAlertNotifier]).
     */
    private fun notifyNowActions() {
        viewModelScope.launch {
            combine(_hallMinuteSchedules, _sensorFleet, ticker) { schedules, fleet, now ->
                Triple(schedules, fleet, now)
            }.collect { (schedules, fleet, now) ->
                val demo = _demoVitals.value
                val demoArmed = _demoNotifyArmed.value
                val actions = Hall.values().map { hall ->
                    val node = fleet[hall.label]?.maxByOrNull { it.lastUpdatedAt ?: 0L }
                    // Only drive the notification from the demo once armed (animation finished).
                    val useDemo = demo != null && demoArmed && demo.hallLabel == hall.label
                    hall to deriveNowActionFromSessions(
                        nowMillis = now,
                        personCount = if (useDemo) demo!!.personCount else node?.personCount ?: 0,
                        temperatureC = if (useDemo) demo!!.temperatureC.toFloat() else node?.temperatureC?.toFloat(),
                        hall = hall,
                        humidityPercent = if (useDemo) demo!!.humidityPercent else node?.humidityPercent,
                        minuteSchedule = schedules[hall.label],
                    )
                }
                fun lineFor(hall: Hall, action: NowAction): String =
                    "${hall.label} — " + appContext.getString(action.labelRes, *action.labelArgs.toTypedArray())

                // 1) Persistent notification — every actionable recommendation; refresh only on change.
                val actionableLines = actions
                    .filter {
                        it.second.acState == AcState.OFF ||
                            it.second.acState == AcState.COOL_PLUS ||
                            it.second.acState == AcState.COOL_MINUS
                    }
                    .map { (hall, action) -> lineFor(hall, action) }
                val sig = actionableLines.joinToString("|")
                if (sig != lastNotifiedActionSig) {
                    lastNotifiedActionSig = sig
                    NowActionNotifier.update(appContext, actionableLines)
                }

                // 2) Escalation — only "Turn off (empty room)" actions left un-Done past the timeout.
                val offLines = actions
                    .filter { it.second.acState == AcState.OFF }
                    .map { (hall, action) -> lineFor(hall, action) }
                if (sig != escActionableSig) {
                    escActionableSig = sig
                    escPendingSince = now
                }
                val escalate = offLines.isNotEmpty() &&
                    !NowActionNotifier.isAcknowledged(appContext, sig) &&
                    (now - escPendingSince) >= ESCALATION_MILLIS
                EnergyAlertNotifier.onNowEscalation(
                    context = appContext,
                    active = escalate,
                    actions = offLines,
                    signature = sig,
                )
            }
        }
    }

    private fun listenToAllHallNodes() {
        listOf("DK1", "DK2", "MM8").forEach { hall ->
            viewModelScope.launch {
                repository.listenToHallNodes(hall).collect { nodes ->
                    _sensorFleet.update { current -> current + (hall to nodes) }
                }
            }
        }
    }

    /**
     * Force a one-time pull of the latest node docs across all halls. Called when the
     * Admin opens the Manage tab so the freshest stored data shows immediately, rather
     * than waiting for the next 10-minute capture sync.
     */
    fun refreshSensorFleet() {
        listOf("DK1", "DK2", "MM8").forEach { hall ->
            viewModelScope.launch {
                val nodes = repository.fetchHallNodes(hall)
                _sensorFleet.update { current -> current + (hall to nodes) }
            }
        }
    }

    /**
     * Auto-refresh the AC energy recommendation aligned to the wall clock every
     * [ENERGY_ADVICE_INTERVAL_MINUTES] minutes (e.g. :00, :30). The manual "Suggest action"
     * button still triggers the same flow on demand for the demo.
     */
    private fun startAutoEnergyAdvice() {
        viewModelScope.launch {
            while (isActive) {
                delay(millisUntilNextBoundary(ENERGY_ADVICE_INTERVAL_MINUTES))
                requestEnergyAdvice()
            }
        }
    }

    private fun listenToAllHallMeanSchedules() {
        listOf("DK1", "DK2", "MM8").forEach { hall ->
            viewModelScope.launch {
                repository.hallMeanScheduleFlow(hall).collect { schedule ->
                    _hallMeanSchedules.update { current -> current + (hall to schedule) }
                }
            }
        }
    }

    private fun listenToAllHallMinuteSchedules() {
        listOf("DK1", "DK2", "MM8").forEach { hall ->
            viewModelScope.launch {
                repository.hallMinuteScheduleFlow(hall).collect { schedule ->
                    _hallMinuteSchedules.update { current -> current + (hall to schedule) }
                }
            }
        }
    }

    private fun listenToAllHallSchedules() {
        listOf("DK1", "DK2", "MM8").forEach { hall ->
            viewModelScope.launch {
                repository.hallScheduleFlow(hall).collect { schedule ->
                    _hallSchedules.update { current -> current + (hall to schedule) }
                }
            }
        }
    }

    private fun listenToAllLearnedSchedules() {
        listOf("DK1", "DK2", "MM8").forEach { hall ->
            viewModelScope.launch {
                repository.learnedScheduleFlow(hall).collect { days ->
                    _learnedSchedules.update { current -> current + (hall to days) }
                }
            }
        }
    }

    val uiState = combine(
        repository.dashboardState,
        weatherRepository.weatherState,
        energyAdvice,
        ticker,
        _demoVitals,
    ) { sensorState, weather, advice, now, demo ->
        DashboardUiState(
            sensorState = sensorState,
            weather = weather,
            energyAdvice = advice,
            packetFreshness = freshness.evaluate(sensorState.lastValidPacketAtMillis, now),
            nowMillis = now,
            demoVitals = demo,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState(),
    )

    private val _selectedHallLabel = MutableStateFlow("DK1")

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
            val occupancy = runCatching {
                occupancyForecastProvider.forecast(hallLabel, System.currentTimeMillis())
            }.getOrNull()
            if (occupancy == null) {
                energyAdvice.value = EnergyAdviceState(
                    status = EnergyAdviceStatus.Failed,
                    message = "Occupancy lookup failed.",
                )
                return@launch
            }
            val input = EnergyAdviceInput(
                deltaTC = deltaT,
                indoorTempC = indoorTempC,
                outdoorTempC = outdoorTempC,
                indoorHumidity = indoorHumidity,
                outdoorHumidity = weatherInfo?.humidityPercent,
                occupancy = occupancy,
                conditionText = weatherInfo?.conditionText,
            )
            runCatching { energyAdvisor.advise(input, com.rewatt.app.core.LocaleManager.promptLanguageName()) }
                .onSuccess { advice: EnergyAdvice ->
                    energyAdvice.value = EnergyAdviceState(
                        status = EnergyAdviceStatus.Complete,
                        message = advice.acAction,
                        advice = advice,
                        occupancy = occupancy,
                        lastAdvisedAtMillis = System.currentTimeMillis(),
                    )
                }
                .onFailure { error ->
                    energyAdvice.value = EnergyAdviceState(
                        status = EnergyAdviceStatus.Failed,
                        message = "Energy advice failed: ${error.message ?: error::class.java.simpleName}",
                    )
                }
        }
    }

    companion object {
        private const val ENERGY_ADVICE_INTERVAL_MINUTES = 30

        // How long a "Turn off (empty room)" recommendation may stay un-Done before the full-screen
        // alert escalates. Measured from when the recommendation first appeared (while the app is open).
        private const val ESCALATION_MILLIS = 30L * 60 * 1000
    }
}

/** Faked vitals for the fsktm screen-recording demo (see DashboardViewModel.startTooColdDemo). */
data class DemoVitals(
    val hallLabel: String,
    val personCount: Int,
    val temperatureC: Double,
    val humidityPercent: Double,
)

data class DashboardUiState(
    val sensorState: SensorDashboardState = SensorDashboardState(),
    val weather: WeatherState = WeatherState(),
    val energyAdvice: EnergyAdviceState = EnergyAdviceState(),
    val packetFreshness: PacketFreshness = PacketFreshness.WaitingForFirstPacket,
    val nowMillis: Long = System.currentTimeMillis(),
    val demoVitals: DemoVitals? = null,
)

class DashboardViewModelFactory(
    private val appContext: Context,
    private val repository: TelemetryRepository,
    private val weatherRepository: WeatherRepository,
    private val occupancyForecastProvider: OccupancyForecastProvider,
    private val energyAdvisor: GeminiEnergyAdvisor,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(
                appContext = appContext.applicationContext,
                repository = repository,
                weatherRepository = weatherRepository,
                occupancyForecastProvider = occupancyForecastProvider,
                energyAdvisor = energyAdvisor,
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
