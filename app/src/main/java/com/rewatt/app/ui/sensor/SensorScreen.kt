package com.rewatt.app.ui.sensor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewatt.app.R
import com.rewatt.app.data.MqttStatus
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.perception.PerceptionUiState
import com.rewatt.app.safety.FireAlarmState
import com.rewatt.app.security.EventPhase
import com.rewatt.app.security.SecurityUiState
import com.rewatt.app.telemetry.FireThreshold
import com.rewatt.app.telemetry.GasLevel
import com.rewatt.app.telemetry.GasReading
import com.rewatt.app.telemetry.noxLevel
import com.rewatt.app.telemetry.vocLevel
import com.rewatt.app.usb.UsbConnectionStatus

private enum class StitchTab(val label: String) {
    ENERGY("ENERGY"),
    FIRE("FIRE"),
    SECURITY("SECURITY"),
    SETTINGS("SETTINGS"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(
    uiState: SensorNodeUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onDebugCapture: () -> Unit,
    onSendPhotoMqtt: () -> Unit,
    onUpdateMqttConfig: (String, Int, String, String, String) -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onOpenSetupGuide: () -> Unit,
    modifier: Modifier = Modifier,
    securityState: SecurityUiState = SecurityUiState(),
    securityContent: @Composable () -> Unit = {},
    fireAlarmState: FireAlarmState = FireAlarmState(),
    onToggleFireDemo: () -> Unit = {},
    settingsSecurityContent: @Composable () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(StitchTab.ENERGY) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF9FAFB),
        topBar = { StitchHeader(mqttStatus = uiState.mqttState.status) },
        bottomBar = {
            StitchBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                isDemoMode = uiState.isDemoMode,
                onToggleDemoMode = onToggleDemoMode,
                onToggleFireDemo = onToggleFireDemo,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (selectedTab) {
                StitchTab.ENERGY -> StitchMainTab(
                    uiState = uiState,
                    securityState = securityState,
                )
                StitchTab.FIRE -> StitchFireTab(
                    uiState = uiState,
                    fireAlarmState = fireAlarmState,
                )
                StitchTab.SECURITY -> securityContent()
                StitchTab.SETTINGS -> StitchSettingsTab(
                    uiState = uiState,
                    onUpdateMqttConfig = onUpdateMqttConfig,
                    onOpenSetupGuide = onOpenSetupGuide,
                    securitySettingsContent = settingsSecurityContent,
                    diagnosticsContent = {
                        DiagnosticsSection(
                            uiState = uiState,
                            onScanUsb = onScanUsb,
                            onStartPerception = onStartPerception,
                            onStopPerception = onStopPerception,
                            onDebugCapture = onDebugCapture,
                            onSendPhotoMqtt = onSendPhotoMqtt,
                        )
                    },
                )
            }
        }
    }
}

// ── TOP HEADER BAR ────────────────────────────────────────────────────────────

@Composable
private fun StitchHeader(mqttStatus: MqttStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF9FAFB))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App Logo + Title (Capitalized ReWatt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "ReWatt Logo",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "ReWatt",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF09090B),
            )
        }

        // Online Pill Status Badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (pillBg, pillText, label) = when (mqttStatus) {
                MqttStatus.Connected -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "ONLINE")
                MqttStatus.Connecting -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "CONNECTING")
                else -> Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), "OFFLINE")
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(pillBg)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(pillText),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillText,
                    )
                }
            }
        }
    }
}

// ── BOTTOM NAVIGATION BAR (Long-press SETTINGS triggers hidden Demo Mode) ───────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StitchBottomBar(
    selectedTab: StitchTab,
    onTabSelected: (StitchTab) -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    onToggleFireDemo: () -> Unit,
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.height(72.dp),
    ) {
        StitchTab.values().forEach { tab ->
            val selected = selectedTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    val icon = when (tab) {
                        StitchTab.ENERGY -> Icons.Outlined.Bolt
                        StitchTab.FIRE -> Icons.Outlined.LocalFireDepartment
                        StitchTab.SECURITY -> Icons.Outlined.Shield
                        StitchTab.SETTINGS -> Icons.Outlined.Settings
                    }
                    Box(
                        modifier = when (tab) {
                            // Hidden: long-press SETTINGS toggles Demo Mode (no pop-up message).
                            StitchTab.SETTINGS -> Modifier.combinedClickable(
                                onClick = { onTabSelected(tab) },
                                onLongClick = { onToggleDemoMode(!isDemoMode) },
                            )
                            // Hidden: long-press FIRE = fire demo (simulated rise → real fire alarm, no MQTT, no pop-up).
                            StitchTab.FIRE -> Modifier.combinedClickable(
                                onClick = { onTabSelected(tab) },
                                onLongClick = onToggleFireDemo,
                            )
                            else -> Modifier
                        },
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            tint = if (selected) Color(0xFF09090B) else Color(0xFF9CA3AF),
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color(0xFF09090B) else Color(0xFF9CA3AF),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFFF3F4F6),
                ),
            )
        }
    }
}

// ── TAB 1: MAIN ───────────────────────────────────────────────────────────────

/**
 * Tiny CCTV shield above the room name. Disarmed: outlined grey. Armed: solid red-gradient shield
 * plus "ARMED", and the live state ("· Motion" / "· Recording") while an event is running.
 */
@Composable
private fun SecurityShieldBadge(security: SecurityUiState) {
    val armedRed = Color(0xFFB91C1C)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (security.armed) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = "Armed",
                tint = Color.Black, // replaced by the gradient below
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithCache {
                        val gradient = Brush.verticalGradient(listOf(Color(0xFFF87171), armedRed))
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient, blendMode = BlendMode.SrcAtop)
                        }
                    },
            )
            Spacer(Modifier.width(4.dp))
            val live = when {
                security.recording -> " · Recording"
                security.phase != EventPhase.IDLE -> " · Motion"
                else -> ""
            }
            Text(
                text = "ARMED$live",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = armedRed,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = "Disarmed",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * VOC Index and NOx Index gauges (Sensirion Gas Index values — unitless, 0..500). Shows "--" when no
 * reading arrived recently, i.e. on modules without an SGP41. Cards turn red above the fire
 * threshold or while the fire alarm is active.
 */
@Composable
private fun GasGaugeRow(
    gas: GasReading?,
    nowMillis: Long,
    fireAlarmActive: Boolean,
) {
    val fresh = gas?.takeIf { nowMillis - it.capturedAtMillis <= FireThreshold.STALE_AFTER_MS }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GasGaugeCard(
            label = "VOC INDEX",
            value = fresh?.vocIndex,
            level = fresh?.vocIndex?.let(::vocLevel),
            colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
            fireAlarmActive = fireAlarmActive,
            modifier = Modifier.weight(1f),
        )
        GasGaugeCard(
            label = "NOx INDEX",
            value = fresh?.noxIndex,
            level = fresh?.noxIndex?.let(::noxLevel),
            colors = listOf(Color(0xFFF59E0B), Color(0xFFEA580C)),
            fireAlarmActive = fireAlarmActive,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GasGaugeCard(
    label: String,
    value: Int?,
    level: GasLevel?,
    colors: List<Color>,
    fireAlarmActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val shownLevel = if (fireAlarmActive) GasLevel.DANGER else level
    val high = shownLevel == GasLevel.DANGER
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (high) Color(0xFFFEE2E2) else Color(0xFFECEEEF)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (high) Color(0xFFB91C1C) else Color(0xFF374151),
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(16.dp))
            // The traffic-light pill sits in the ring's open bottom gap, so the card keeps exactly the
            // same height as the Temperature/Humidity cards.
            Box(contentAlignment = Alignment.BottomCenter) {
                StitchRingGauge(
                    value = (value ?: 0).toFloat(),
                    maxValue = GAS_INDEX_MAX,
                    centerText = value?.toString() ?: "--",
                    subText = if (value != null) "Index" else null,
                    gradientColors = if (high) listOf(Color(0xFFF87171), Color(0xFFB91C1C)) else colors,
                    isDataAvailable = value != null,
                )
                BreathingTrafficPill(level = shownLevel, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

/**
 * Small traffic-light pill: green = safe, amber = caution, red = danger. It softly "breathes"
 * (opacity only), a little faster as the level rises. Grey and still when there is no reading.
 */
@Composable
private fun BreathingTrafficPill(level: GasLevel?, modifier: Modifier = Modifier) {
    val color = when (level) {
        GasLevel.SAFE -> Color(0xFF22C55E)
        GasLevel.CAUTION -> Color(0xFFF59E0B)
        GasLevel.DANGER -> Color(0xFFEF4444)
        null -> Color(0xFFD1D5DB)
    }
    val alpha = if (level == null) {
        1f
    } else {
        val periodMs = when (level) {
            GasLevel.DANGER -> 700
            GasLevel.CAUTION -> 1_200
            GasLevel.SAFE -> 2_400
        }
        val breath by rememberInfiniteTransition(label = "gasBreath").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "gasBreathAlpha",
        )
        breath
    }
    Box(
        modifier = modifier
            .size(width = 22.dp, height = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = alpha)),
    )
}

private const val GAS_INDEX_MAX = 500f

// ── TAB 2: FIRE ───────────────────────────────────────────────────────────────

@Composable
private fun StitchFireTab(
    uiState: SensorNodeUiState,
    fireAlarmState: FireAlarmState,
) {
    val alarm = fireAlarmState.active
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Fire Safety", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))

        // Status card: NORMAL / FIRE ALERT
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (alarm) Color(0xFFFEE2E2) else Color(0xFFECEEEF)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (alarm) Color(0xFFB91C1C) else Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (alarm) Color.White else Color(0xFF15803D),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (alarm) "FIRE ALERT" else "NORMAL",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (alarm) Color(0xFFB91C1C) else Color(0xFF15803D),
                    )
                    Text(
                        text = fireAlarmState.atMillis?.let {
                            "Last alarm " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                .format(java.util.Date(it))
                        } ?: "No alarms",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
            }
        }

        // VOC / NOx gauges with traffic-light pills ("--" when the module has no SGP41)
        GasGaugeRow(
            gas = uiState.sensorState.latestGas,
            nowMillis = uiState.nowMillis,
            fireAlarmActive = alarm,
        )

        Text(
            text = "Alarm when VOC Index ≥ ${FireThreshold.VOC_INDEX} or NOx Index ≥ ${FireThreshold.NOX_INDEX} for 2 s",
            fontSize = 12.sp,
            color = Color(0xFF6B7280),
        )
    }
}

// ── TAB 1: ENERGY ─────────────────────────────────────────────────────────────

@Composable
private fun StitchMainTab(
    uiState: SensorNodeUiState,
    securityState: SecurityUiState,
) {
    val telemetry = uiState.sensorState.latestTelemetry
    val perception = uiState.perceptionState
    val isDemo = uiState.isDemoMode
    val usbConnected = uiState.sensorState.connectionStatus == UsbConnectionStatus.Connected || isDemo

    val rawTemp = telemetry?.temperatureC
    val rawHum = telemetry?.humidityPercent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Room ID & Connectivity Card (turns light red while CCTV is Armed)
        val armed = securityState.armed
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (armed) Color(0xFFFEE2E2) else Color(0xFFECEEEF)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    SecurityShieldBadge(securityState)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiState.roomId.ifBlank { "Room1" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF09090B),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "RN-${uiState.nodeId.takeLast(6).ifBlank { "082" }}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B7280),
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val mqttConnected = uiState.mqttState.status == MqttStatus.Connected || isDemo
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (mqttConnected) Color(0xFFC6F6D5) else Color(0xFFFEF3C7))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (mqttConnected) Color(0xFF22C55E) else Color(0xFFD97706)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (mqttConnected) "Connected" else "Offline",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (mqttConnected) Color(0xFF15803D) else Color(0xFFB45309),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BatteryFull, contentDescription = null, tint = Color(0xFF374151), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${uiState.batteryPercent}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151),
                        )
                    }
                }
            }
        }

        // Gauges Row: Temperature & Humidity (Real Dynamic Values)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Temperature Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "TEMPERATURE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151),
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    StitchRingGauge(
                        value = rawTemp ?: 0f,
                        maxValue = 50f,
                        centerText = if (rawTemp != null) "${rawTemp.toInt()}°" else "--",
                        gradientColors = listOf(Color(0xFF2563EB), Color(0xFF00E676)),
                        isDataAvailable = rawTemp != null && usbConnected,
                    )
                }
            }

            // Humidity Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "HUMIDITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151),
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    StitchRingGauge(
                        value = rawHum ?: 0f,
                        maxValue = 100f,
                        centerText = if (rawHum != null) "${rawHum.toInt()}" else "--",
                        subText = if (rawHum != null) "%" else null,
                        gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF14B8A6)),
                        isDataAvailable = rawHum != null && usbConnected,
                    )
                }
            }
        }

        // Occupancy Card (Beige Warm Gradient)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF4EB)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFED7AA)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Radar, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "OCCUPANCY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF374151),
                            letterSpacing = 0.5.sp,
                        )
                    }

                    val occupied = telemetry?.presence == true || isDemo
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (occupied) "OCCUPIED" else "EMPTY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (occupied) Color(0xFFD97706) else Color(0xFF6B7280),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${perception.personCount}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF09090B),
                )
                Text(
                    text = if (perception.isRunning || isDemo) "Detected in zone" else "Perception is stopped",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280),
                )
            }
        }

        // Illuminance / EXIF Light Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = Color(0xFF374151), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "ILLUMINANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF374151),
                            letterSpacing = 0.5.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        val evVal = perception.exposureEv?.let { "%.1f".format(it) } ?: if (isDemo) "8.4" else null
                        Text(
                            text = evVal ?: "--",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF09090B),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "EV100",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val lightsOn = perception.lightsOn == true || isDemo
                    Text(
                        text = if (perception.exposureEv == null && !isDemo) "Awaiting Frame" else if (lightsOn) "Lights ON" else "Lights OFF",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF09090B),
                    )
                    Spacer(Modifier.height(4.dp))
                    Switch(
                        checked = lightsOn,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34D399),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE5E7EB),
                        ),
                    )
                }
            }
        }
    }
}

/** Stitch Clean Gradient Ring Gauge with empty state support */
@Composable
private fun StitchRingGauge(
    value: Float,
    maxValue: Float,
    centerText: String,
    subText: String? = null,
    gradientColors: List<Color>,
    isDataAvailable: Boolean = true,
) {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val size2 = Size(diameter, diameter)

            // Background gray track arc
            drawArc(
                color = Color(0xFFE2E8F0),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = size2,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            if (isDataAvailable) {
                // Gradient sweep arc
                val fraction = (value / maxValue).coerceIn(0f, 1f)
                val sweep = fraction * 270f
                drawArc(
                    brush = Brush.sweepGradient(gradientColors),
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = size2,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF09090B),
            )
            if (subText != null) {
                Text(
                    text = subText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

// ── SETTINGS → DIAGNOSTICS (formerly the DEBUG tab) ───────────────────────────

@Composable
private fun DiagnosticsSection(
    uiState: SensorNodeUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onDebugCapture: () -> Unit,
    onSendPhotoMqtt: () -> Unit,
) {
    val telemetry = uiState.sensorState.latestTelemetry
    val usbStatus = uiState.sensorState.connectionStatus
    val perception = uiState.perceptionState
    val isDemo = uiState.isDemoMode

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // USB Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Usb, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("USB Status", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF09090B))
                    }

                    val connected = usbStatus == UsbConnectionStatus.Connected || isDemo
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (connected) Color(0xFFDCFCE7) else Color(0xFFFEF3C7))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (connected) "CONNECTED" else "DISCONNECTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (connected) Color(0xFF15803D) else Color(0xFFB45309),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("PORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
                        Text(
                            text = if (usbStatus == UsbConnectionStatus.Connected || isDemo) "/dev/ttyUSB0 (ESP32-C6)" else "No USB Device",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF09090B),
                        )
                    }

                    OutlinedButton(
                        onClick = onScanUsb,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF09090B)),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rescan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // mmWave Radar Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Radar, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("mmWave Radar", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF09090B))
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Distance", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${telemetry?.movingDistanceCm ?: 0}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
                                Spacer(Modifier.width(4.dp))
                                Text("cm", fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Energy", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${telemetry?.movingEnergy ?: 0}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
                                Spacer(Modifier.width(4.dp))
                                Text("E", fontSize = 12.sp, color = Color(0xFFC2410C), modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        // Perception Feed Card
        Column {
            Text("Perception Feed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
            Spacer(Modifier.height(10.dp))
            perception.latestFrame?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "YOLO Perception Feed",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // Controls Section
        Column {
            Text("Controls", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    if (perception.isRunning) onStopPerception()
                    else onStartPerception(CaptureStartSource.Manual)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B), contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (perception.isRunning) "Stop Perception" else "Start Perception", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDebugCapture,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF09090B)),
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test Capture", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSendPhotoMqtt,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF09090B)),
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("MQTT Publish", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── TAB 3: SETTINGS ("MQTT Configuration") ────────────────────────────────────

@Composable
private fun StitchSettingsTab(
    uiState: SensorNodeUiState,
    onUpdateMqttConfig: (String, Int, String, String, String) -> Unit,
    onOpenSetupGuide: () -> Unit,
    securitySettingsContent: @Composable () -> Unit,
    diagnosticsContent: @Composable () -> Unit,
) {
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var host by remember { mutableStateOf(uiState.mqttState.brokerHost) }
    var portText by remember { mutableStateOf("8883") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf(uiState.roomId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
        Column {
            Text("MQTT Configuration", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
            Text("Connect your ReWatt unit to your local broker.", fontSize = 13.sp, color = Color(0xFF6B7280))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("BROKER HOST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    placeholder = { Text("mqtt.local") },
                    leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        Text("PORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                            ),
                        )
                    }

                    Column(modifier = Modifier.weight(0.6f)) {
                        Text("ROOM ID", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = roomId,
                            onValueChange = { roomId = it },
                            placeholder = { Text("Living Room") },
                            leadingIcon = { Icon(Icons.Outlined.MeetingRoom, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                            ),
                        )
                    }
                }

                Text("USERNAME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    placeholder = { Text("rewatt_user") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )

                Text("PASSWORD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        val p = portText.toIntOrNull() ?: 8883
                        onUpdateMqttConfig(host, p, user, pass, roomId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A), contentColor = Color.White),
                ) {
                    Text("Save & Connect", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Clean Single "Quick Setup Guide" Outlined Button
        OutlinedButton(
            onClick = onOpenSetupGuide,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF09090B)),
        ) {
            Icon(Icons.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Quick Setup Guide", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        // Security camera settings + permissions (moved here from the SECURITY tab)
        securitySettingsContent()

        // Diagnostics (formerly the DEBUG tab) — collapsed by default to keep Settings tidy
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { diagnosticsExpanded = !diagnosticsExpanded },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Diagnostics", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF09090B), modifier = Modifier.weight(1f))
                Icon(
                    if (diagnosticsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                )
            }
        }
        if (diagnosticsExpanded) diagnosticsContent()
        Spacer(Modifier.height(8.dp))
    }
}
