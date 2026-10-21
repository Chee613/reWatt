package com.rewatt.app.ui.sensor

import android.content.Context
import android.os.BatteryManager
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rewatt.app.data.MqttState
import com.rewatt.app.data.MqttStatus
import com.rewatt.app.data.MqttSyncRepository
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.perception.DetectionBox
import com.rewatt.app.perception.ObjectDetection
import com.rewatt.app.perception.PerceptionStateHolder
import com.rewatt.app.perception.PerceptionUiState
import com.rewatt.app.telemetry.FireThreshold
import com.rewatt.app.telemetry.GasReading
import com.rewatt.app.telemetry.RadarTelemetry
import com.rewatt.app.usb.UsbConnectionStatus
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class SensorNodeUiState(
    val roomId: String = "Room1",
    val nodeId: String = "",
    val mqttState: MqttState = MqttState(),
    val sensorState: SensorDashboardState = SensorDashboardState(),
    val perceptionState: PerceptionUiState = PerceptionUiState(),
    val nowMillis: Long = System.currentTimeMillis(),
    val batteryPercent: Int = 100,
    val isDemoMode: Boolean = false,
)

class SensorViewModel(
    private val appContext: Context,
    private val telemetryRepository: TelemetryRepository,
    val mqttRepository: MqttSyncRepository,
    val perceptionStateHolder: PerceptionStateHolder,
) : ViewModel() {

    private val nowMillisFlow = MutableStateFlow(System.currentTimeMillis())
    private val _isDemoMode = MutableStateFlow(false)
    private var tickCount = 0L

    val uiState: StateFlow<SensorNodeUiState> = combine(
        telemetryRepository.telemetryState,
        mqttRepository.mqttState,
        perceptionStateHolder.state,
        _isDemoMode,
    ) { sensorState: SensorDashboardState, mqttState: MqttState, perceptionState: PerceptionUiState, demo: Boolean ->
        if (demo) {
            val simTemp = (24.0f + sin(tickCount / 4.0).toFloat() * 1.5f)
            val simHum = (55.0f + cos(tickCount / 3.0).toFloat() * 3.0f)
            val simDist = 120 + ((tickCount % 10) * 3).toInt()
            val simHeadcount = (12 + ((tickCount % 4) - 2)).toInt()
            val simulatedDetections = List(simHeadcount) {
                ObjectDetection("person", 0.94f, 0, DetectionBox(0.1f, 0.1f, 0.5f, 0.8f))
            }

            val demoTelemetry = RadarTelemetry(
                presence = true,
                movingEnergy = 45,
                stationaryEnergy = 20,
                distanceCm = simDist,
                temperatureC = simTemp,
                humidityPercent = simHum,
                capturedAtMillis = System.currentTimeMillis(),
                rawPacket = "DEMO_PACKET",
                radarState = 1,
                movingDistanceCm = simDist,
            )

            SensorNodeUiState(
                roomId = if (mqttRepository.roomId.isBlank()) "Room1" else mqttRepository.roomId,
                nodeId = if (mqttRepository.nodeId.isBlank()) "RN-082" else mqttRepository.nodeId,
                mqttState = MqttState(status = MqttStatus.Connected, brokerHost = "demo.hivemq.cloud", activeTopic = "rewatt/sensors/Room1/telemetry"),
                sensorState = SensorDashboardState(latestTelemetry = demoTelemetry, connectionStatus = UsbConnectionStatus.Connected),
                perceptionState = PerceptionUiState(isRunning = true, yoloDetections = simulatedDetections, lightsOn = true, exposureEv = 8.4),
                nowMillis = nowMillisFlow.value,
                batteryPercent = 85,
                isDemoMode = true,
            )
        } else {
            SensorNodeUiState(
                roomId = mqttRepository.roomId,
                nodeId = mqttRepository.nodeId,
                mqttState = mqttState,
                sensorState = sensorState,
                perceptionState = perceptionState,
                nowMillis = nowMillisFlow.value,
                batteryPercent = getRealBatteryLevel(),
                isDemoMode = false,
            )
        }
    }.combine(nowMillisFlow) { state: SensorNodeUiState, now: Long ->
        if (state.isDemoMode) {
            // Simulated SGP41 (refreshed every tick so it never goes stale): normal air ≈ VOC 100, NOx 1.
            // While the long-press fire demo runs, show its readings instead.
            val demoGas = if (telemetryRepository.gasDemoActive) {
                telemetryRepository.telemetryState.value.latestGas
            } else {
                GasReading(vocIndex = 100 + ((tickCount % 11) - 5).toInt(), noxIndex = 1, capturedAtMillis = now)
            }
            state.copy(nowMillis = now, sensorState = state.sensorState.copy(latestGas = demoGas))
        } else {
            state.copy(nowMillis = now)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SensorNodeUiState(),
    )

    init {
        // Ticker loop: update time every second and emit MQTT 1 Hz payload
        viewModelScope.launch {
            while (true) {
                val current = System.currentTimeMillis()
                tickCount++
                nowMillisFlow.value = current
                streamMqttTelemetry(current)
                delay(1000L)
            }
        }
    }

    fun toggleDemoMode(enabled: Boolean) {
        _isDemoMode.value = enabled
        mqttRepository.publishingSuppressed = enabled
    }

    fun updateMqttConfig(host: String, port: Int, user: String, pass: String, roomId: String) {
        mqttRepository.configure(host, port, user, pass, roomId)
    }

    /** Publish latest camera frame photo (compressed JPEG Base64) to MQTT on demand. */
    fun publishCurrentPhoto() {
        if (uiState.value.isDemoMode) return
        val bitmap = perceptionStateHolder.state.value.latestFrame ?: return
        viewModelScope.launch {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            val jpegBytes = outputStream.toByteArray()
            val base64Jpeg = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            val currentRoomId = mqttRepository.roomId
            val json = JSONObject().apply {
                put("nodeId", mqttRepository.nodeId)
                put("roomId", currentRoomId)
                put("timestamp", System.currentTimeMillis())
                put("imageFormat", "jpeg")
                put("imageBase64", base64Jpeg)
            }

            mqttRepository.publishTelemetryJson(json.toString(), currentRoomId)
        }
    }

    private fun getRealBatteryLevel(): Int {
        return runCatching {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (capacity in 0..100) capacity else 100
        }.getOrDefault(100)
    }

    private fun streamMqttTelemetry(now: Long) {
        val state = uiState.value
        // In Demo Mode, DO NOT publish to real MQTT broker/network
        if (state.isDemoMode) {
            return
        }

        val latest = state.sensorState.latestTelemetry
        val perception = state.perceptionState
        val batteryPct = state.batteryPercent

        val radarObj = JSONObject().apply {
            put("state", latest?.radarState ?: 0)
            put("movingDistanceCm", latest?.movingDistanceCm ?: 0)
            put("movingEnergy", latest?.movingEnergy ?: 0)
            put("stationaryDistanceCm", latest?.stationaryDistanceCm ?: 0)
            put("stationaryEnergy", latest?.stationaryEnergy ?: 0)
        }

        val json = JSONObject().apply {
            put("nodeId", state.nodeId)
            put("roomId", state.roomId)
            put("timestamp", now)
            put("presence", latest?.presence ?: false)
            put("personCount", perception.personCount)
            put("lightsOn", perception.lightsOn)
            put("temperatureC", latest?.temperatureC ?: 0.0)
            put("humidityPercent", latest?.humidityPercent ?: 0.0)
            put("exposureEv", perception.exposureEv)
            put("radar", radarObj)
            put("batteryPercent", batteryPct)
            // Optional SGP41 — only included when a recent reading exists, so existing consumers see no change.
            state.sensorState.latestGas
                ?.takeIf { now - it.capturedAtMillis <= FireThreshold.STALE_AFTER_MS }
                ?.let { gas ->
                    gas.vocIndex?.let { put("vocIndex", it) }
                    gas.noxIndex?.let { put("noxIndex", it) }
                }
        }

        mqttRepository.publishTelemetryJson(json.toString(), state.roomId)
    }
}

class SensorViewModelFactory(
    private val appContext: Context,
    private val telemetryRepository: TelemetryRepository,
    private val mqttRepository: MqttSyncRepository,
    private val perceptionStateHolder: PerceptionStateHolder,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SensorViewModel(appContext, telemetryRepository, mqttRepository, perceptionStateHolder) as T
    }
}
