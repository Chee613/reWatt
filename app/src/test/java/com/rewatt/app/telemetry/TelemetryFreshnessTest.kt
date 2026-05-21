package com.rewatt.app.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryFreshnessTest {
    private val freshness = TelemetryFreshness(staleAfterMillis = 30_000L)

    @Test
    fun evaluate_returnsWaitingBeforeAnyPacketArrives() {
        assertEquals(
            PacketFreshness.WaitingForFirstPacket,
            freshness.evaluate(lastValidPacketAtMillis = null, nowMillis = 100_000L),
        )
    }

    @Test
    fun evaluate_returnsFreshInsideWindow() {
        assertEquals(
            PacketFreshness.Fresh,
            freshness.evaluate(lastValidPacketAtMillis = 70_001L, nowMillis = 100_000L),
        )
    }

    @Test
    fun evaluate_returnsStaleAfterWindow() {
        assertEquals(
            PacketFreshness.Stale,
            freshness.evaluate(lastValidPacketAtMillis = 69_999L, nowMillis = 100_000L),
        )
    }
}

