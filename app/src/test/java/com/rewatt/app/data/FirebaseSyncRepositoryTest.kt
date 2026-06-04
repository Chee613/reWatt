package com.rewatt.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseSyncRepositoryTest {
    @Test
    fun buildPerceptionPayload_includesPeopleCountAndActivityOnly() {
        val payload = buildPerceptionPayload(
            perceptionSummary = PerceptionSyncSummary(
                personCount = 1,
                temperatureC = 26.5f,
                humidityPercent = 58.2f,
                lightsOn = true,
                exposureEv = 4.5,
                exposureIso = 200,
                exposureTimeSeconds = 0.05,
                exposureAperture = 1.8,
                capturedAtMillis = 900L,
            ),
            roomId = "default-room",
            nodeId = "node-1",
            updatedAtMillis = 1_000L,
        )

        assertEquals("default-room", payload["roomId"])
        assertEquals("node-1", payload["nodeId"])
        assertEquals(1_000L, payload["lastUpdatedAt"])
        assertEquals(1, payload["personCount"])
        assertEquals(900L, payload["perceptionCapturedAt"])
        assertEquals(26.5f, payload["temperatureC"])
        assertEquals(58.2f, payload["humidityPercent"])
        assertEquals(true, payload["lightsOn"])
        assertEquals(4.5, payload["exposureEv"])
        assertEquals(200, payload["exposureIso"])
        assertEquals(0.05, payload["exposureTimeSeconds"])
        assertEquals(1.8, payload["exposureAperture"])
        assertFalse(payload.containsKey("primaryActivity"))
        assertFalse(payload.containsKey("activityLabels"))
        assertFalse(payload.containsKey("presence"))
        assertFalse(payload.containsKey("movingEnergy"))
        assertFalse(payload.containsKey("stationaryEnergy"))
        assertFalse(payload.containsKey("distanceCm"))
        assertFalse(payload.containsKey("rawPacket"))
    }

    @Test
    fun buildActivityAnalysisPayload_usesGeminiModelId() {
        val payload = buildActivityAnalysisPayload(
            analysis = ActivityAnalysis(
                activity = "Lecture session",
                confidence = 0.82f,
                reason = "Speech, clapping, and keyboard sounds suggest a lecture.",
                occupancyHint = "occupied",
            ),
            status = "complete",
            analyzedAtMillis = 1_234L,
        )

        assertEquals("Lecture session", payload["activity"])
        assertEquals(0.82f, payload["confidence"])
        assertEquals("occupied", payload["occupancyHint"])
        assertEquals("complete", payload["status"])
        assertEquals(GeminiActivityAnalyzer.MODEL_ID, payload["model"])
        assertEquals(1_234L, payload["analyzedAt"])
    }

    @Test
    fun buildFirestoreWriteFailureMessage_includesEachFailedPath() {
        val message = buildFirestoreWriteFailureMessage(
            linkedMapOf(
                "rooms/default-room/nodes/node-1" to "PERMISSION_DENIED - Missing or insufficient permissions.",
                "rooms/default-room/telemetry/900-node-1" to "UNAVAILABLE - Network unavailable.",
            ),
        )

        assertTrue(message.contains("rooms/default-room/nodes/node-1"))
        assertTrue(message.contains("PERMISSION_DENIED"))
        assertTrue(message.contains("rooms/default-room/telemetry/900-node-1"))
        assertTrue(message.contains("UNAVAILABLE"))
    }
}
