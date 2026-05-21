package com.rewatt.app.data

import com.rewatt.app.telemetry.SensorTelemetry
import com.rewatt.app.telemetry.UsbDataParser
import com.rewatt.app.usb.UsbConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TelemetryRepository(
    private val telemetryDao: TelemetryDao,
) {
    private val mutableState = MutableStateFlow(SensorDashboardState())
    val dashboardState: StateFlow<SensorDashboardState> = mutableState.asStateFlow()

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

    companion object {
        private const val MAX_MALFORMED_PACKET_PREVIEW = 160
    }
}

data class SensorDashboardState(
    val connectionStatus: UsbConnectionStatus = UsbConnectionStatus.Disconnected,
    val connectionMessage: String = "USB service has not scanned yet.",
    val latestTelemetry: SensorTelemetry? = null,
    val lastValidPacketAtMillis: Long? = null,
    val validPacketCount: Int = 0,
    val malformedPacketCount: Int = 0,
    val lastMalformedPacket: String? = null,
)

