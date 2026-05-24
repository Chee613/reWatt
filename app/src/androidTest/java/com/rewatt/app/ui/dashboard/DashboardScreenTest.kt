package com.rewatt.app.ui.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.perception.DetectionBox
import com.rewatt.app.perception.ObjectDetection
import com.rewatt.app.perception.PerceptionUiState
import com.rewatt.app.perception.SoundDetection
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.RadarTelemetry
import com.rewatt.app.ui.theme.ReWattTheme
import com.rewatt.app.usb.UsbConnectionStatus
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsDisconnectedState() {
        setDashboardContent()

        composeRule.onNodeWithText("Disconnected").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for first packet.").assertIsDisplayed()
        composeRule.onNodeWithText("LIVE").assertIsDisplayed()
        composeRule.onNodeWithText("Analytic").assertIsDisplayed()
        composeRule.onNodeWithText("Capture").assertIsDisplayed()
    }

    @Test
    fun showsLiveTelemetryState() {
        setDashboardContent(
            uiState = DashboardUiState(
                sensorState = SensorDashboardState(
                    connectionStatus = UsbConnectionStatus.Connected,
                    connectionMessage = "Connected at 115200 baud.",
                    latestTelemetry = RadarTelemetry(
                        presence = true,
                        movingEnergy = 42,
                        stationaryEnergy = 85,
                        distanceCm = 120,
                        temperatureC = 26.5f,
                        humidityPercent = 58.2f,
                        capturedAtMillis = 1_000L,
                        rawPacket = "LD2412:PRES=1,MOV=42,STA=85,DIST=120|SHT30:T=26.5,H=58.2",
                    ),
                    lastValidPacketAtMillis = 1_000L,
                    validPacketCount = 1,
                ),
                packetFreshness = PacketFreshness.Fresh,
                nowMillis = 1_500L,
            ),
        )

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("120 cm").assertIsDisplayed()
        composeRule.onNodeWithText("26.5 C").assertIsDisplayed()
        composeRule.onNodeWithText("58.2 %").assertIsDisplayed()
        composeRule.onNodeWithText("Detected").assertIsDisplayed()
        composeRule.onNodeWithText("Live telemetry is fresh.").assertIsDisplayed()
        composeRule.onNodeWithText("Firestore sync").assertIsDisplayed()
        composeRule.onNodeWithText("Start Capture").assertIsDisplayed()
    }

    @Test
    fun showsMalformedPacketWarning() {
        setDashboardContent(
            uiState = DashboardUiState(
                sensorState = SensorDashboardState(
                    malformedPacketCount = 1,
                    lastMalformedPacket = "not-sensor-data",
                ),
            ),
        )

        composeRule.onNodeWithText("Malformed").assertIsDisplayed()
        composeRule.onNodeWithText("Packet health").assertIsDisplayed()
    }

    @Test
    fun pressingStartCaptureOpensCaptureTab() {
        setDashboardContent()

        composeRule.onNodeWithText("Start Capture").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Capture interface").assertIsDisplayed()
        composeRule.onNodeWithText("Top 3 sounds heard").assertIsDisplayed()
        composeRule.onNodeWithText("Human activity sounds").assertIsDisplayed()
    }

    @Test
    fun analyticTabIsSecondAndShowsTimeFmPlaceholder() {
        setDashboardContent()

        composeRule.onNodeWithText("Analytic").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("TimeFM Analysis").assertIsDisplayed()
        composeRule.onNodeWithText("Analytic tab ready for future TimeFM code.").assertIsDisplayed()
    }

    @Test
    fun showsFilteredLectureHallPerceptionState() {
        setDashboardContent(
            perceptionState = PerceptionUiState(
                yoloDetections = listOf(
                    ObjectDetection(
                        label = "person",
                        confidence = 0.91f,
                        classId = 0,
                        box = DetectionBox(0f, 0f, 1f, 1f),
                    ),
                    ObjectDetection(
                        label = "laptop",
                        confidence = 0.72f,
                        classId = 63,
                        box = DetectionBox(0f, 0f, 1f, 1f),
                    ),
                ),
                soundDetections = listOf(
                    SoundDetection(label = "Speech", confidence = 0.84f),
                    SoundDetection(label = "Clicking", confidence = 0.76f),
                ),
                captureStartSource = CaptureStartSource.Manual,
                isRunning = true,
            ),
        )

        composeRule.onNodeWithText("Capture").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Top 3 sounds heard").assertIsDisplayed()
        composeRule.onNodeWithText("Human activity sounds").assertIsDisplayed()
        composeRule.onAllNodesWithText("Clicking (support)").assertCountEquals(2)
    }

    @Test
    fun captureTabShowsBinaryLightState() {
        setDashboardContent(
            perceptionState = PerceptionUiState(
                lightsOn = true,
                exposureEv = 2.1,
            ),
        )

        composeRule.onNodeWithText("Capture").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lights On").assertIsDisplayed()
        composeRule.onNodeWithText("EV 2.1").assertIsDisplayed()
    }

    private fun setDashboardContent(
        uiState: DashboardUiState = DashboardUiState(),
        perceptionState: PerceptionUiState = PerceptionUiState(),
    ) {
        composeRule.setContent {
            ReWattTheme {
                DashboardScreen(
                    uiState = uiState,
                    perceptionState = perceptionState,
                    onScanUsb = {},
                    onStartPerception = { _, _ -> },
                    onStopPerception = {},
                    userEmail = "test@example.com",
                    userDisplayName = "Test User",
                    onSignOut = {},
                )
            }
        }
    }
}
