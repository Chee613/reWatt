package com.rewatt.app.data

import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.RadarTelemetry
import com.rewatt.app.telemetry.TelemetryFreshness
import com.rewatt.app.telemetry.UsbDataParser
import com.rewatt.app.usb.UsbConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TelemetryRepository(
    private val telemetryDao: TelemetryDao,
    private val firebaseSyncRepository: FirebaseSyncRepository,
    private val geminiActivityAnalyzer: GeminiActivityAnalyzer,
) {
    private val mutableState = MutableStateFlow(SensorDashboardState())
    val dashboardState: StateFlow<SensorDashboardState> = mutableState.asStateFlow()
    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var analysisJob: Job? = null
    private var lastAnalysisKey: String? = null
    private val freshness = TelemetryFreshness()

    /** True only when the USB mmWave sensor sent a packet within the freshness window (~30s). */
    fun isTelemetryFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        freshness.evaluate(mutableState.value.lastValidPacketAtMillis, nowMillis) == PacketFreshness.Fresh

    /** Sensor side: publish the current energy-waste state to Firestore so Admin phones can alert. */
    fun publishEnergyAlertState(wasteful: Boolean, personCount: Int, lightsOn: Boolean?) {
        firebaseSyncRepository.publishEnergyAlert(wasteful, personCount, lightsOn)
    }

    /** Admin side: live cross-device energy-waste signal for [roomId]. */
    fun energyAlertFlow(roomId: String): Flow<EnergyAlertSignal?> =
        firebaseSyncRepository.energyAlertFlow(roomId)

    /** Admin side: log that a NOW-action recommendation was carried out (Done pressed). */
    fun recordNowActionDone(signature: String) {
        firebaseSyncRepository.recordNowActionDone(signature)
    }

    // Last time a packet was persisted to Room. Used to throttle high-rate serial writes so the DB
    // can never back up and stall the live UI path. Volatile because packets are processed on
    // concurrent coroutines (one per serial line); an occasional extra insert from a race is harmless.
    @Volatile
    private var lastPersistedAtMillis = 0L

    suspend fun acceptRawPacket(rawPacket: String, capturedAtMillis: Long = System.currentTimeMillis()) {
        val telemetry = UsbDataParser.parseTelemetry(rawPacket, capturedAtMillis)
        if (telemetry == null) {
            mutableState.update { current ->
                current.copy(
                    malformedPacketCount = current.malformedPacketCount + 1,
                    lastMalformedPacket = rawPacket.trim().take(MAX_MALFORMED_PACKET_PREVIEW),
                )
            }
            return
        }

        // Update the live UI state IMMEDIATELY, independently of persistence. Previously the Room
        // insert ran first, so as the (never-pruned) telemetry_readings table grew, the serialized
        // write backed up and the displayed reading fell progressively behind real time — the
        // "live data freezes after a few minutes" bug. Newest packet always wins, so an out-of-order
        // older packet (concurrent coroutines) can never overwrite a fresher reading.
        mutableState.update { current ->
            val lastAt = current.lastValidPacketAtMillis
            if (lastAt != null && telemetry.capturedAtMillis < lastAt) {
                current.copy(validPacketCount = current.validPacketCount + 1)
            } else {
                current.copy(
                    latestTelemetry = telemetry,
                    lastValidPacketAtMillis = telemetry.capturedAtMillis,
                    validPacketCount = current.validPacketCount + 1,
                    lastMalformedPacket = null,
                )
            }
        }

        // Persist at most once per PERSIST_INTERVAL_MILLIS. The serial stream can emit many packets a
        // second; the dashboard/forecast only need a sampled history, so throttling keeps the table
        // small and the write path off the live UI's back.
        if (capturedAtMillis - lastPersistedAtMillis >= PERSIST_INTERVAL_MILLIS) {
            lastPersistedAtMillis = capturedAtMillis
            runCatching { telemetryDao.insert(telemetry.toEntity()) }
        }
    }

    fun setConnectionStatus(
        status: UsbConnectionStatus,
        message: String,
    ) {
        mutableState.update { current ->
            current.copy(
                connectionStatus = status,
                connectionMessage = message,
            )
        }
    }

    val recentCapturesFlow: StateFlow<List<RecentCapture>> =
        firebaseSyncRepository.recentCapturesFlow

    val nodeId: String get() = firebaseSyncRepository.nodeId

    fun listenToHall(roomId: String): Flow<HallSnapshot?> =
        firebaseSyncRepository.listenToHall(roomId)

    fun listenToHallNodes(roomId: String): Flow<List<SensorNodeSnapshot>> =
        firebaseSyncRepository.listenToHallNodes(roomId)

    suspend fun fetchHallNodes(roomId: String): List<SensorNodeSnapshot> =
        firebaseSyncRepository.fetchHallNodes(roomId)

    fun hallScheduleFlow(hallLabel: String): Flow<Array<IntArray>> =
        firebaseSyncRepository.hallScheduleFlow(hallLabel)

    fun hallMeanScheduleFlow(hallLabel: String): Flow<Array<IntArray>> =
        firebaseSyncRepository.hallMeanScheduleFlow(hallLabel)

    fun hallMinuteScheduleFlow(hallLabel: String): Flow<Array<IntArray>> =
        firebaseSyncRepository.hallMinuteScheduleFlow(hallLabel)

    fun learnedScheduleFlow(hallLabel: String): Flow<List<List<LearnedSegment>>> =
        firebaseSyncRepository.learnedScheduleFlow(hallLabel)

    fun wasteSummaryFlow(): Flow<EnergyWasteSummary?> =
        firebaseSyncRepository.wasteSummaryFlow()

    suspend fun loadHallSchedule(hallLabel: String): Array<IntArray> =
        firebaseSyncRepository.loadHallSchedule(hallLabel)

    fun updatePerceptionSummary(perceptionSummary: PerceptionSyncSummary) {
        val latestTelemetry = mutableState.value.latestTelemetry
        val enrichedSummary = perceptionSummary.copy(
            temperatureC = latestTelemetry?.temperatureC,
            humidityPercent = latestTelemetry?.humidityPercent,
        )
        firebaseSyncRepository.syncPerceptionSummary(
            perceptionSummary = enrichedSummary,
            onState = ::setFirebaseSyncState,
        )
        analyzeActivity(enrichedSummary)
    }

    private fun setFirebaseSyncState(syncState: FirebaseSyncState) {
        mutableState.update { current ->
            current.copy(firebaseSyncState = syncState)
        }
    }

    private fun analyzeActivity(summary: PerceptionSyncSummary) {
        // Camera-only trigger: run once per distinct capture (no dependency on audio labels).
        val capturedAtMillis = summary.capturedAtMillis ?: return

        val analysisKey = listOf(
            capturedAtMillis,
            summary.personCount,
            summary.temperatureC,
            summary.humidityPercent,
        ).joinToString(separator = "::")
        if (analysisKey == lastAnalysisKey) return
        lastAnalysisKey = analysisKey

        if (!geminiActivityAnalyzer.isConfigured()) {
            mutableState.update { current ->
                current.copy(
                    activityAnalysisState = ActivityAnalysisState(
                        status = ActivityAnalysisStatus.ConfigMissing,
                        message = "Gemini API key missing. Add GEMINI_API_KEY to local.properties and rebuild.",
                    ),
                )
            }
            return
        }

        analysisJob?.cancel()
        mutableState.update { current ->
            current.copy(
                activityAnalysisState = ActivityAnalysisState(
                    status = ActivityAnalysisStatus.Pending,
                    message = "Gemini is analyzing current activity.",
                ),
            )
        }

        analysisJob = analysisScope.launch {
            runCatching { geminiActivityAnalyzer.analyze(summary, com.rewatt.app.core.LocaleManager.promptLanguageName()) }
                .onSuccess { analysis ->
                    mutableState.update { current ->
                        current.copy(
                            activityAnalysisState = ActivityAnalysisState(
                                status = ActivityAnalysisStatus.Complete,
                                message = "Gemini classified this as ${analysis.activity}.",
                                analysis = analysis,
                                lastAnalyzedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                    firebaseSyncRepository.syncActivityAnalysis(summary, analysis)
                }
                .onFailure { error ->
                    mutableState.update { current ->
                        current.copy(
                            activityAnalysisState = ActivityAnalysisState(
                                status = ActivityAnalysisStatus.Failed,
                                message = "Gemini analysis failed: ${error.message ?: error::class.java.simpleName}",
                            ),
                        )
                    }
                }
        }
    }

    companion object {
        private const val MAX_MALFORMED_PACKET_PREVIEW = 160

        // Persist at most one telemetry row every 2s regardless of serial packet rate. The live UI
        // updates on every packet (in memory); only the sampled history goes to Room.
        private const val PERSIST_INTERVAL_MILLIS = 2_000L
    }
}

data class SensorDashboardState(
    val connectionStatus: UsbConnectionStatus = UsbConnectionStatus.Disconnected,
    val connectionMessage: String = "USB service has not scanned yet.",
    val latestTelemetry: RadarTelemetry? = null,
    val lastValidPacketAtMillis: Long? = null,
    val validPacketCount: Int = 0,
    val malformedPacketCount: Int = 0,
    val lastMalformedPacket: String? = null,
    val firebaseSyncState: FirebaseSyncState = FirebaseSyncState(),
    val activityAnalysisState: ActivityAnalysisState = ActivityAnalysisState(),
)
