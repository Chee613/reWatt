package com.rewatt.app.data

import com.rewatt.app.telemetry.RadarTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseSyncRepositoryTest {
    @Test
    fun buildTelemetryPayload_includesRadarAndPerceptionState() {
        val payload = buildTelemetryPayload(
            telemetry = RadarTelemetry(
                presence = true,
                movingEnergy = 42,
                stationaryEnergy = 85,
                distanceCm = 120,
                capturedAtMillis = 1_000L,
                rawPacket = "RADAR:PRES=1,MOV=42,STA=85,DIST=120",
            ),
            perceptionSummary = PerceptionSyncSummary(
                personCount = 1,
                topAudioLabels = listOf("Speech", "Typing"),
                audioPresenceDetected = true,
                audioPresenceScore = 0.82f,
                capturedAtMillis = 900L,
            ),
            roomId = "default-room",
            nodeId = "node-1",
        )

        assertEquals("default-room", payload["roomId"])
        assertEquals("node-1", payload["nodeId"])
        assertEquals(true, payload["presence"])
        assertEquals(42, payload["movingEnergy"])
        assertEquals(85, payload["stationaryEnergy"])
        assertEquals(120, payload["distanceCm"])
        assertEquals(1, payload["personCount"])
        assertEquals(900L, payload["perceptionCapturedAt"])
        assertEquals(true, payload["audioPresenceDetected"])
        assertEquals(0.82f, payload["audioPresenceScore"])
        assertTrue((payload["topAudioLabels"] as List<*>).contains("Typing"))
    }
}
