package com.rewatt.app.ui.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureAutoTriggerTest {
    @Test
    fun shouldAutoTriggerCapture_requiresFreshPresence() {
        assertTrue(
            shouldAutoTriggerCapture(
                hasFreshPresence = true,
                isCaptureRunning = false,
                autoSuppressedUntilClear = false,
                latestTelemetryAtMillis = 1_000L,
                lastAutoTriggerAtMillis = null,
            ),
        )

        assertFalse(
            shouldAutoTriggerCapture(
                hasFreshPresence = false,
                isCaptureRunning = false,
                autoSuppressedUntilClear = false,
                latestTelemetryAtMillis = 1_000L,
                lastAutoTriggerAtMillis = null,
            ),
        )
    }

    @Test
    fun shouldAutoTriggerCapture_doesNotRestartWhileRunningOrSuppressed() {
        assertFalse(
            shouldAutoTriggerCapture(
                hasFreshPresence = true,
                isCaptureRunning = true,
                autoSuppressedUntilClear = false,
                latestTelemetryAtMillis = 1_000L,
                lastAutoTriggerAtMillis = null,
            ),
        )

        assertFalse(
            shouldAutoTriggerCapture(
                hasFreshPresence = true,
                isCaptureRunning = false,
                autoSuppressedUntilClear = true,
                latestTelemetryAtMillis = 1_000L,
                lastAutoTriggerAtMillis = null,
            ),
        )
    }

    @Test
    fun shouldAutoTriggerCapture_doesNotRepeatSameTelemetryPacket() {
        assertFalse(
            shouldAutoTriggerCapture(
                hasFreshPresence = true,
                isCaptureRunning = false,
                autoSuppressedUntilClear = false,
                latestTelemetryAtMillis = 1_000L,
                lastAutoTriggerAtMillis = 1_000L,
            ),
        )
    }
}
