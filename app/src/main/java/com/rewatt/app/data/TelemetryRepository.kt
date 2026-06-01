package com.rewatt.app.data

import com.rewatt.app.telemetry.RadarTelemetry
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

        telemetryDao.insert(telemetry.toEntity())
        mutableState.update { current ->
            current.copy(
                latestTelemetry = telemetry,
                lastValidPacketAtMillis = telemetry.capturedAtMillis,
                validPacketCount = current.validPacketCount + 1,
                lastMalformedPacket = null,
            )
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

    fun hallScheduleFlow(hallLabel: String): Flow<Array<IntArray>> =
        firebaseSyncRepository.hallScheduleFlow(hallLabel)

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
        val capturedAtMillis = summary.capturedAtMillis ?: return
        if (summary.activityLabels.isEmpty()) {
            mutableState.update { current ->
                current.copy(
                    activityAnalysisState = ActivityAnalysisState(
                        message = "Gemini activity analysis is waiting for top-3 YAMNet labels.",
                    ),
                )
            }
            return
        }

        val analysisKey = listOf(
            capturedAtMillis,
            summary.personCount,
            summary.activityLabels.take(3).joinToString("|"),
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
                    message = "Gemini is analyzing the top-3 YAMNet labels.",
                ),
            )
        }

        analysisJob = analysisScope.launch {
            runCatching { geminiActivityAnalyzer.analyze(summary) }
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
