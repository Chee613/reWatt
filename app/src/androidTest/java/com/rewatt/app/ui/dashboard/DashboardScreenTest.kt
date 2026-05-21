package com.rewatt.app.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.SensorTelemetry
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
                    onScanUsb = {},
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
                            latestTelemetry = SensorTelemetry(
                                tempC = 26.5f,
                                humidityPercent = 58.2f,
                                aqi = 1,
                                tvocPpb = 45,
                                eco2Ppm = 415,
                                movingEnergy = 42,
                                stationaryEnergy = 85,
                                presence = true,
                                capturedAtMillis = 1_000L,
                                rawPacket = "ENV:T=26.5,H=58.2,AQI=1,TVOC=45,CO2=415|RADAR:MOV=42,STA=85,PRES=1",
                            ),
                            lastValidPacketAtMillis = 1_000L,
                            validPacketCount = 1,
                        ),
                        packetFreshness = PacketFreshness.Fresh,
                        nowMillis = 1_500L,
                    ),
                    onScanUsb = {},
                )
            }
        }

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("26.5 C").assertIsDisplayed()
        composeRule.onNodeWithText("Detected").assertIsDisplayed()
        composeRule.onNodeWithText("Live telemetry is fresh.").assertIsDisplayed()
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
                    onScanUsb = {},
                )
            }
        }

        composeRule.onNodeWithText("Malformed").assertIsDisplayed()
        composeRule.onNodeWithText("Last rejected: not-sensor-data").assertIsDisplayed()
    }
}
