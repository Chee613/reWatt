package com.rewatt.app.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.rewatt.app.data.SensorDashboardState
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
        composeRule.setContent {
            ReWattTheme {
                DashboardScreen(
                    uiState = DashboardUiState(),
                    perceptionState = PerceptionUiState(),
                    onScanUsb = {},
                    onStartPerception = {},
                    onStopPerception = {},
                )
            }
        }

        composeRule.onNodeWithText("Disconnected").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for the first valid telemetry packet.").assertIsDisplayed()
    }

    @Test
    fun showsLiveTelemetryState() {
        composeRule.setContent {
            ReWattTheme {
                DashboardScreen(
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
                    perceptionState = PerceptionUiState(),
                    onScanUsb = {},
                    onStartPerception = {},
                    onStopPerception = {},
                )
            }
        }

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("120 cm").assertIsDisplayed()
        composeRule.onNodeWithText("26.5 C").assertIsDisplayed()
        composeRule.onNodeWithText("58.2 %").assertIsDisplayed()
        composeRule.onNodeWithText("Detected").assertIsDisplayed()
        composeRule.onNodeWithText("Live telemetry is fresh.").assertIsDisplayed()
        composeRule.onNodeWithText("Firestore sync").assertIsDisplayed()
    }

    @Test
    fun showsMalformedPacketWarning() {
        composeRule.setContent {
            ReWattTheme {
                DashboardScreen(
                    uiState = DashboardUiState(
                        sensorState = SensorDashboardState(
                            malformedPacketCount = 1,
                            lastMalformedPacket = "not-sensor-data",
                        ),
                    ),
                    perceptionState = PerceptionUiState(),
                    onScanUsb = {},
                    onStartPerception = {},
                    onStopPerception = {},
                )
            }
        }

        composeRule.onNodeWithText("Malformed").assertIsDisplayed()
        composeRule.onNodeWithText("Last rejected: not-sensor-data").assertIsDisplayed()
    }

    @Test
    fun showsFilteredLectureHallPerceptionState() {
        composeRule.setContent {
            ReWattTheme {
                DashboardScreen(
                    uiState = DashboardUiState(),
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
                    ),
                    onScanUsb = {},
                    onStartPerception = {},
                    onStopPerception = {},
                )
            }
        }

        composeRule.onNodeWithText("People detected").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Context objects").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Human activity sounds").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Clicking (support)").performScrollTo().assertIsDisplayed()
    }
}
