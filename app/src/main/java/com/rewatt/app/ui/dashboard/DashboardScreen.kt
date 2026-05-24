package com.rewatt.app.ui.dashboard

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rewatt.app.R
import com.rewatt.app.data.ActivityAnalysisStatus
import com.rewatt.app.data.FirebaseSyncStatus
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.perception.ObjectDetection
import com.rewatt.app.perception.PerceptionUiState
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.RadarTelemetry
import com.rewatt.app.usb.UsbConnectionStatus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.input.pointer.pointerInput
import com.rewatt.app.data.UserRole
import com.rewatt.app.telemetry.OccupancyThreshold
import com.rewatt.app.telemetry.toOccupancyDecision
import com.rewatt.app.ui.ModeSelectionDialog
import com.rewatt.app.ui.SetupGuideDialog
import java.util.Calendar
import kotlin.math.min

private enum class ReWattTab(val label: String, val icon: ImageVector) {
    Live("LIVE",      Icons.Outlined.Sensors),
    Analytic("Analytic", Icons.Outlined.BarChart),
    Capture("Capture",  Icons.Outlined.CameraAlt),
    Setting("Setting",  Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (PreviewView, CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    userEmail: String,
    userDisplayName: String?,
    onSignOut: () -> Unit,
    isDeletingAccount: Boolean = false,
    deleteAccountError: String? = null,
    onDeleteAccount: () -> Unit = {},
    onClearDeleteError: () -> Unit = {},
    currentUserRole: UserRole? = null,
    onModeSelected: (UserRole) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ReWattTab.Live) }
    var selectedHall by rememberSaveable { mutableStateOf(Hall.Hall1) }
    var pendingCaptureSource by remember { mutableStateOf<CaptureStartSource?>(null) }
    var lastAutoTriggerAtMillis by remember { mutableStateOf<Long?>(null) }
    var autoSuppressedUntilClear by remember { mutableStateOf(false) }

    val latestTelemetry = uiState.sensorState.latestTelemetry
    val hasFreshPresence = uiState.packetFreshness == PacketFreshness.Fresh &&
        latestTelemetry?.presence == true

    LaunchedEffect(hasFreshPresence) {
        if (!hasFreshPresence) autoSuppressedUntilClear = false
    }

    LaunchedEffect(
        hasFreshPresence,
        latestTelemetry?.capturedAtMillis,
        perceptionState.isRunning,
        autoSuppressedUntilClear,
    ) {
        val telemetryAtMillis = latestTelemetry?.capturedAtMillis
        if (
            shouldAutoTriggerCapture(
                hasFreshPresence = hasFreshPresence,
                isCaptureRunning = perceptionState.isRunning,
                autoSuppressedUntilClear = autoSuppressedUntilClear,
                latestTelemetryAtMillis = telemetryAtMillis,
                lastAutoTriggerAtMillis = lastAutoTriggerAtMillis,
            )
        ) {
            lastAutoTriggerAtMillis = telemetryAtMillis
            selectedTab = ReWattTab.Capture
            pendingCaptureSource = CaptureStartSource.Auto
        }
    }

    fun requestCapture(source: CaptureStartSource) {
        selectedTab = ReWattTab.Capture
        pendingCaptureSource = source
    }

    fun stopCapture() {
        if (hasFreshPresence) autoSuppressedUntilClear = true
        onStopPerception()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReWattLogoBadge()
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("REWATT", fontWeight = FontWeight.Bold)
                            Text(
                                text = userDisplayName?.takeIf { it.isNotBlank() } ?: userEmail,
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                ReWattTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(paddingValues),
        ) {
            when (selectedTab) {
                ReWattTab.Live -> LivePage(
                    uiState = uiState,
                    perceptionState = perceptionState,
                    selectedHall = selectedHall,
                    onHallChange = { selectedHall = it },
                    onStartCapture = { requestCapture(CaptureStartSource.Manual) },
                )

                ReWattTab.Analytic -> AnalyticPage(
                    uiState = uiState,
                    perceptionState = perceptionState,
                    selectedHall = selectedHall,
                    onHallChange = { selectedHall = it },
                )

                ReWattTab.Capture -> CapturePage(
                    state = perceptionState,
                    nowMillis = uiState.nowMillis,
                    pendingCaptureSource = pendingCaptureSource,
                    onPendingCaptureConsumed = { pendingCaptureSource = null },
                    onStartPerception = onStartPerception,
                    onStopPerception = ::stopCapture,
                    telemetry = uiState.sensorState.latestTelemetry,
                )

                ReWattTab.Setting -> SettingPage(
                    uiState = uiState,
                    onScanUsb = onScanUsb,
                    userEmail = userEmail,
                    userDisplayName = userDisplayName,
                    onSignOut = onSignOut,
                    isDeletingAccount = isDeletingAccount,
                    deleteAccountError = deleteAccountError,
                    onDeleteAccount = onDeleteAccount,
                    onClearDeleteError = onClearDeleteError,
                    currentUserRole = currentUserRole,
                    onModeSelected = onModeSelected,
                )
            }
        }
    }
}

internal fun shouldAutoTriggerCapture(
    hasFreshPresence: Boolean,
    isCaptureRunning: Boolean,
    autoSuppressedUntilClear: Boolean,
    latestTelemetryAtMillis: Long?,
    lastAutoTriggerAtMillis: Long?,
): Boolean =
    hasFreshPresence &&
        !isCaptureRunning &&
        !autoSuppressedUntilClear &&
        latestTelemetryAtMillis != null &&
        latestTelemetryAtMillis != lastAutoTriggerAtMillis

@Composable
internal fun ReWattLogoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(70.dp)
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x334DFFE9)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x5536A3FF)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.rewatt_logo),
                contentDescription = "REWATT logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun LivePage(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    selectedHall: Hall,
    onHallChange: (Hall) -> Unit,
    onStartCapture: () -> Unit,
) {
    val telemetry = uiState.sensorState.latestTelemetry
    val analysis = uiState.sensorState.activityAnalysisState.analysis

    val nowAction = remember(uiState.nowMillis, perceptionState.personCount, telemetry?.temperatureC, analysis?.occupancyHint, selectedHall) {
        deriveNowAction(
            nowMillis = uiState.nowMillis,
            personCount = perceptionState.personCount,
            temperatureC = telemetry?.temperatureC,
            occupancyHint = analysis?.occupancyHint,
            hall = selectedHall,
        )
    }
    val sessionEndTime = remember(uiState.nowMillis, selectedHall) { deriveSessionEndTime(uiState.nowMillis, selectedHall) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HallDropdown(selectedHall = selectedHall, onHallChange = onHallChange)

        // ── 6-card grid ──────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Occupancy",
                value = when {
                    perceptionState.personCount > 0 -> "${perceptionState.personCount} ppl"
                    telemetry?.presence == true -> "Detected"
                    telemetry != null -> "Empty"
                    else -> "--"
                },
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "AC & Lights",
                value = acLightStateLabel(nowAction.first),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Temperature",
                value = telemetry?.temperatureC?.let { "%.1f °C".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Humidity",
                value = telemetry?.humidityPercent?.let { "%.1f %%".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Session ends",
                value = sessionEndTime,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Top sound",
                value = perceptionState.soundDetections.firstOrNull()?.label ?: "--",
                modifier = Modifier.weight(1f),
            )
        }

        // ── Activity section ─────────────────────────────────────────────────
        Spacer(Modifier.height(4.dp))
        Text("ACTIVITY", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        InfoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "NOW",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(nowAction.first, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        nowAction.second,
                        fontSize = 13.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (analysis != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Gemini: ${analysis.activity} (${(analysis.confidence * 100).toInt()}%)",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Button(
                    onClick = onStartCapture,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text(if (perceptionState.isRunning) "Open" else "Capture")
                }
            }
        }

        // ── Room Context section ──────────────────────────────────────────────
        Spacer(Modifier.height(4.dp))
        Text("ROOM CONTEXT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        RoomContextCard(perceptionState = perceptionState)
    }
}

@Composable
private fun RoomContextCard(perceptionState: PerceptionUiState) {
    val sounds = perceptionState.soundDetections.take(6)
    InfoCard {
        Text("Sound scene", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        if (sounds.isEmpty()) {
            Text(
                text = if (perceptionState.isRunning) "Listening…" else "Start capture to detect sounds",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sounds.forEach { sound -> SoundChip(sound = sound) }
            }
        }
    }
}

@Composable
private fun SoundChip(sound: com.rewatt.app.perception.SoundDetection) {
    val containerColor = if (sound.indicatesHumanPresence) {
        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (sound.indicatesHumanPresence) {
        androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = "${sound.label}  ${(sound.confidence * 100).toInt()}%",
            fontSize = 12.sp,
            fontWeight = if (sound.indicatesHumanPresence) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

@Composable
private fun AnalyticPage(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    selectedHall: Hall,
    onHallChange: (Hall) -> Unit,
) {
    var isAction by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        ActionDataToggle(
            isAction = isAction,
            onAction = { isAction = true },
            onData = { isAction = false },
        )
        Spacer(Modifier.height(12.dp))
        if (isAction) {
            ActionPage(
                uiState = uiState,
                perceptionState = perceptionState,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HallDropdown(selectedHall = selectedHall, onHallChange = onHallChange)
                WeeklyOccupancyCard(hall = selectedHall)
            }
        }
    }
}

// ── Action / Data toggle ──────────────────────────────────────────────────────

@Composable
private fun ActionDataToggle(
    isAction: Boolean,
    onAction: () -> Unit,
    onData: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
    ) {
        SegmentButton("Action", isAction,  onAction, Modifier.weight(1f))
        SegmentButton("Data",   !isAction, onData,   Modifier.weight(1f))
    }
}

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) {
                androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
            } else {
                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
        )
    }
}

// ── Timetable primitives (shared by heatmap + action engine) ─────────────────

private enum class Hall(val label: String) {
    Hall1("Hall 1"),
    Hall2("Hall 2"),
    Hall3("Hall 3"),
    Hall4("Hall 4"),
}

private data class TR(val sh: Int, val sm: Int, val eh: Int, val em: Int)

private fun inRange(h: Int, m: Int, r: TR): Boolean {
    val cur = h * 60 + m
    val start = r.sh * 60 + r.sm
    val end = r.eh * 60 + r.em
    return if (start < end) cur in start until end
    else cur >= start || cur < end  // overnight wrap
}

private val timetableOvernight = TR(18, 30, 7, 30)

// Per-hall break intervals. 18:30–07:30 overnight zero applies to all halls.
// Hall 2–4 breaks are all >45 min each, 2–3 per weekday.
private val hallDayBreaks: Map<Hall, Map<Int, List<TR>>> = mapOf(
    Hall.Hall1 to mapOf(
        0 to listOf(TR(10, 15, 11, 45), TR(15, 15, 16, 45)),  // Mon  90+90 min
        1 to listOf(TR(11, 15, 12, 45)),                        // Tue  90 min
        2 to listOf(TR(11, 15, 12, 45), TR(15, 15, 16, 45)),  // Wed  90+90 min
        3 to listOf(TR(8, 0, 8, 45),    TR(12, 15, 15, 45)),  // Thu  45+210 min
        4 to listOf(TR(12, 15, 15, 45)),                        // Fri  210 min
    ),
    Hall.Hall2 to mapOf(
        0 to listOf(TR(9, 0, 10, 0),   TR(14, 0, 15, 30)),   // Mon  60+90 min
        1 to listOf(TR(10, 0, 11, 0),  TR(15, 30, 17, 0)),   // Tue  60+90 min
        2 to listOf(TR(9, 30, 11, 0),  TR(13, 0, 14, 0)),    // Wed  90+60 min
        3 to listOf(TR(11, 0, 12, 30), TR(15, 0, 16, 0)),    // Thu  90+60 min
        4 to listOf(TR(10, 30, 12, 0), TR(14, 30, 16, 0)),   // Fri  90+90 min
    ),
    Hall.Hall3 to mapOf(
        0 to listOf(TR(9, 0, 10, 30),  TR(13, 0, 14, 30)),   // Mon  90+90 min
        1 to listOf(TR(11, 0, 12, 0),  TR(15, 0, 16, 30)),   // Tue  60+90 min
        2 to listOf(TR(10, 0, 11, 30), TR(14, 30, 16, 0)),   // Wed  90+90 min
        3 to listOf(TR(8, 30, 10, 0),  TR(12, 0, 13, 0)),    // Thu  90+60 min
        4 to listOf(TR(11, 0, 12, 30), TR(15, 0, 16, 30)),   // Fri  90+90 min
    ),
    Hall.Hall4 to mapOf(
        0 to listOf(TR(9, 0, 10, 30),  TR(13, 0, 14, 0),  TR(16, 0, 17, 30)), // Mon  90+60+90 min
        1 to listOf(TR(10, 30, 12, 0), TR(14, 0, 15, 0)),                      // Tue  90+60 min
        2 to listOf(TR(9, 0, 10, 0),   TR(13, 30, 15, 0)),                     // Wed  60+90 min
        3 to listOf(TR(11, 30, 13, 0), TR(15, 30, 17, 0)),                     // Thu  90+90 min
        4 to listOf(TR(10, 0, 11, 30), TR(13, 0, 14, 30), TR(16, 0, 17, 30)), // Fri  90+90+90 min
    ),
)

private data class TimetableNotification(
    val id: String,
    val timeLabel: String,
    val title: String,
    val reason: String,
)

// ── Weekly occupancy heatmap ──────────────────────────────────────────────────

// 0 = empty, 1 = low (1-40), 2 = medium (41-80), 3 = high (81-120)
private fun buildWeeklySchedule(hall: Hall): Array<IntArray> {
    val breaks = hallDayBreaks[hall] ?: emptyMap()
    val schedule = Array(7) { IntArray(24) { 0 } }
    for (day in 0..4) {
        val dayBreaks = breaks[day] ?: emptyList()
        for (hour in 0..23) {
            var emptySlots = 0
            for (q in 0..3) {
                val m = q * 15
                if (inRange(hour, m, timetableOvernight) || dayBreaks.any { inRange(hour, m, it) })
                    emptySlots++
            }
            if (emptySlots >= 2) continue
            schedule[day][hour] = when {
                hour in 8..9 -> 2
                hour >= 17   -> 1
                else         -> 3
            }
        }
    }
    return schedule
}

@Composable
private fun WeeklyOccupancyCard(hall: Hall) {
    val schedule = remember(hall) { buildWeeklySchedule(hall) }
    val dayBreaks = hallDayBreaks[hall] ?: emptyMap()
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    InfoCard(modifier = Modifier.fillMaxWidth()) {
        Text("Weekly occupancy pattern", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OccupancyHeatmapLegend()
        Spacer(Modifier.height(10.dp))
        WeeklyHeatmapGrid(
            schedule = schedule,
            selectedDay = selectedDay,
            onDayClick = { day -> selectedDay = if (selectedDay == day) null else day },
        )
        Spacer(Modifier.height(4.dp))
        HourAxisRow()
        AnimatedVisibility(
            visible = selectedDay != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val day = selectedDay ?: 0
            Column {
                Spacer(Modifier.height(12.dp))
                DailyOccupancyChart(dayIndex = day, schedule = schedule, dayBreaks = dayBreaks, hall = hall)
            }
        }
    }
}

@Composable
private fun OccupancyHeatmapLegend() {
    val emptyColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeatmapLegendItem(emptyColor,        "Empty")
        HeatmapLegendItem(Color(0xFF90B4D8), "1-40")
        HeatmapLegendItem(Color(0xFF4A90D9), "41-80")
        HeatmapLegendItem(Color(0xFF1565C0), "81+")
    }
}

@Composable
private fun HeatmapLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklyHeatmapGrid(
    schedule: Array<IntArray>,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    Column {
        dayLabels.forEachIndexed { day, label ->
            if (day > 0) Spacer(Modifier.height(if (day == 6) 2.dp else 4.dp))
            val isSelected = selectedDay == day
            val isWeekend = day >= 5
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) primaryColor.copy(alpha = 0.10f)
                        else Color.Transparent,
                    )
                    .clickable { onDayClick(day) }
                    .padding(vertical = if (isWeekend) 1.dp else 3.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HeatmapRow(
                    hourLevels = schedule[day],
                    modifier = Modifier.weight(1f),
                    rowHeight = if (isWeekend) 13.dp else 26.dp,
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) primaryColor
                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(14.dp),
                )
            }
        }
    }
}

@Composable
private fun HeatmapRow(
    hourLevels: IntArray,
    modifier: Modifier = Modifier,
    rowHeight: androidx.compose.ui.unit.Dp = 26.dp,
) {
    val emptyColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier
            .height(rowHeight)
            .clip(RoundedCornerShape(13.dp))
            .background(emptyColor),
    ) {
        val cellW = size.width / 24f
        val gap = 1.5f
        hourLevels.forEachIndexed { hour, level ->
            val color = when (level) {
                1 -> Color(0xFF90B4D8)
                2 -> Color(0xFF4A90D9)
                3 -> Color(0xFF1565C0)
                else -> return@forEachIndexed
            }
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(hour * cellW + gap / 2f, gap / 2f),
                size = androidx.compose.ui.geometry.Size(cellW - gap, size.height - gap),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
            )
        }
    }
}

@Composable
private fun HourAxisRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("0", "6", "12", "18", "24").forEach { label ->
            Text(
                text = label,
                fontSize = 9.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DailyOccupancyChart(
    dayIndex: Int,
    schedule: Array<IntArray>,
    dayBreaks: Map<Int, List<TR>>,
    hall: Hall,
) {
    val fullDayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val emptyColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val refLineColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val breaks = dayBreaks[dayIndex] ?: emptyList()
    val occupancy = remember(hall, dayIndex) { buildHourlyOccupancy(schedule[dayIndex], hall, dayIndex) }
    val peak = occupancy.max()

    val breakLabel = if (breaks.isEmpty()) {
        if (dayIndex >= 5) "No classes — weekend" else "No breaks scheduled"
    } else {
        breaks.joinToString("  •  ") { br ->
            "Break %02d:%02d–%02d:%02d".format(br.sh, br.sm, br.eh, br.em)
        }
    }

    var selectedHour by remember(dayIndex, hall) { mutableStateOf<Int?>(null) }
    val firstHour = 7
    val lastHour = 18
    val numHours = lastHour - firstHour + 1  // 12 bars (07:00–18:59)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${fullDayNames[dayIndex]} — daily pattern",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                text = "Peak: $peak ppl",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .height(90.dp)
                    .width(26.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("120", "80", "40", "0").forEach { label ->
                    Text(
                        text = label,
                        fontSize = 8.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(emptyColor)
                    .pointerInput(dayIndex, hall) {
                        detectTapGestures { offset ->
                            val barW = size.width / numHours.toFloat()
                            val tapped = firstHour + (offset.x / barW).toInt().coerceIn(0, numHours - 1)
                            selectedHour = if (selectedHour == tapped) null else tapped
                        }
                    },
            ) {
                val maxCount = 120f
                val barW = size.width / numHours.toFloat()
                val gap = 1.5f
                // Reference lines at 40 and 80
                listOf(40f, 80f).forEach { count ->
                    val y = size.height * (1f - count / maxCount)
                    drawLine(
                        color = refLineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }

                // Draw bars for visible hours only (firstHour..lastHour)
                for (hour in firstHour..lastHour) {
                    val count = occupancy[hour]
                    if (count == 0) continue
                    val isSelected = selectedHour == hour
                    val barHeight = size.height * (count / maxCount)
                    val x = (hour - firstHour) * barW
                    val color = if (isSelected) when {
                        count > 80 -> Color(0xFF512DA8)   // dark purple  ≈ 0xFF1565C0
                        count > 40 -> Color(0xFF7E57C2)   // medium purple ≈ 0xFF4A90D9
                        else       -> Color(0xFFB39DDB)   // light purple  ≈ 0xFF90B4D8
                    } else when {
                        count > 80 -> Color(0xFF1565C0)
                        count > 40 -> Color(0xFF4A90D9)
                        else       -> Color(0xFF90B4D8)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(x = x + gap, y = size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(width = barW - gap * 2, height = barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                    )
                }

                // Stroke outline on selected column
                val selHour = selectedHour ?: return@Canvas
                if (selHour in firstHour..lastHour) {
                    val x = (selHour - firstHour) * barW
                    drawRoundRect(
                        color = Color(0xFF7E57C2).copy(alpha = 0.55f),
                        topLeft = androidx.compose.ui.geometry.Offset(x = x + gap, y = 0f),
                        size = androidx.compose.ui.geometry.Size(width = barW - gap * 2, height = size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                        style = Stroke(width = 1.5f),
                    )
                }
            }
        }
        // Zoomed axis: labels only for the visible 07–18 window
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("07", "09", "11", "13", "15", "17", "18").forEach { label ->
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val selHour = selectedHour
        AnimatedVisibility(
            visible = selHour != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (selHour != null) {
                val selCount = occupancy[selHour]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "%02d:00 – %02d:00".format(selHour, selHour + 1),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = if (selCount > 0) "$selCount people" else "Empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Text(
            text = breakLabel,
            fontSize = 11.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Action page composables ───────────────────────────────────────────────────

@Composable
private fun ActionPage(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    modifier: Modifier = Modifier,
) {
    val telemetry = uiState.sensorState.latestTelemetry
    val analysis = uiState.sensorState.activityAnalysisState.analysis

    var previewDayOffset by rememberSaveable { mutableStateOf(0) }

    val previewMillis = remember(previewDayOffset, uiState.nowMillis) {
        Calendar.getInstance().apply {
            timeInMillis = uiState.nowMillis
            add(Calendar.DAY_OF_YEAR, previewDayOffset)
        }.timeInMillis
    }

    val isToday = previewDayOffset == 0
    val nowActions = remember(previewMillis, isToday, perceptionState.personCount, telemetry?.temperatureC, analysis?.occupancyHint) {
        Hall.values().map { hall ->
            hall to deriveNowAction(
                nowMillis = previewMillis,
                personCount = if (isToday) perceptionState.personCount else 0,
                temperatureC = if (isToday) telemetry?.temperatureC else null,
                occupancyHint = if (isToday) analysis?.occupancyHint else null,
                hall = hall,
            )
        }
    }

    val dayNotifications = remember(previewDayOffset, uiState.nowMillis) {
        buildAllHallsDayNotifications(previewDayOffset, uiState.nowMillis)
    }
    var dismissedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var nowDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(nowActions.joinToString { it.second.first }) { nowDismissed = false }
    LaunchedEffect(previewDayOffset) { dismissedIds = emptySet() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = !nowDismissed,
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            AllHallsNowCard(
                nowActions = nowActions,
                onDone = { nowDismissed = true },
            )
        }
        TimePreviewControl(
            dayOffset = previewDayOffset,
            nowMillis = uiState.nowMillis,
            onDayChange = { previewDayOffset = it },
        )
        Text(
            text = if (isToday) "Today's schedule" else "Full day schedule",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(dayNotifications, key = { it.id }) { notification ->
                AnimatedVisibility(
                    visible = notification.id !in dismissedIds,
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    NotificationBlock(
                        notification = notification,
                        onDone = { dismissedIds = dismissedIds + notification.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun NowCard(title: String, reason: String, onDone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "NOW",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = reason,
                    fontSize = 13.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun AllHallsNowCard(
    nowActions: List<Pair<Hall, Pair<String, String>>>,
    onDone: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "NOW",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    )
                }
                OutlinedButton(onClick = onDone) { Text("Done") }
            }
            nowActions.forEach { (hall, action) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = hall.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.width(52.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = action.first,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = action.second,
                            fontSize = 11.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimePreviewControl(dayOffset: Int, nowMillis: Long, onDayChange: (Int) -> Unit) {
    val dayOptions = remember(nowMillis) {
        listOf(-1 to "Yesterday", 0 to "Today", 1 to "Tomorrow", 2 to "+2 Days").map { (offset, label) ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val dayName = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val date = "%d/%d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
            Triple(offset, label, if (offset == 0) "Today" else "$dayName $date")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (dayOffset != 0) {
            val selected = dayOptions.find { it.first == dayOffset }
            Text(
                text = "Showing: ${selected?.third ?: ""}",
                fontSize = 11.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dayOptions.forEach { (offset, label, _) ->
                val isSelected = dayOffset == offset
                if (isSelected) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.height(32.dp)) {
                        Text(label, fontSize = 11.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDayChange(offset) },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(label, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationBlock(
    notification: TimetableNotification,
    onDone: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = notification.timeLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(notification.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = notification.reason,
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Done", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CapturePage(
    state: PerceptionUiState,
    nowMillis: Long,
    pendingCaptureSource: CaptureStartSource?,
    onPendingCaptureConsumed: () -> Unit,
    onStartPerception: (PreviewView, CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    telemetry: RadarTelemetry? = null,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(previewView, pendingCaptureSource, state.isRunning) {
        val source = pendingCaptureSource
        val preview = previewView
        if (source == null) return@LaunchedEffect
        if (state.isRunning) {
            onPendingCaptureConsumed()
            return@LaunchedEffect
        }
        if (preview != null) {
            onStartPerception(preview, source)
            onPendingCaptureConsumed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaptureHeader(
            state = state,
            previewView = previewView,
            onStartPerception = onStartPerception,
            onStopPerception = onStopPerception,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Camera",
                value = if (state.cameraPermissionGranted) "Allowed" else "Need access",
                modifier = Modifier.weight(1f),
                warning = !state.cameraPermissionGranted,
            )
            MetricCard(
                title = "Mic",
                value = if (state.microphonePermissionGranted) "Allowed" else "Need access",
                modifier = Modifier.weight(1f),
                warning = !state.microphonePermissionGranted,
            )
        }
        CaptureDetectionSummary(state = state)
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { previewView = it }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        )
        LatestFrameCompactCard(
            state = state,
            nowMillis = nowMillis,
            modifier = Modifier.height(320.dp),
        )
        AudioCompactCard(
            state = state,
            nowMillis = nowMillis,
            modifier = Modifier.height(260.dp),
        )
        SensorLiveDataCard(telemetry = telemetry)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LivePerceptionSummary(perceptionState: PerceptionUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(
            title = "People detected",
            value = perceptionState.personCount.toString(),
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            title = "Top sound",
            value = perceptionState.soundDetections.firstOrNull()?.label ?: "--",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CaptureDetectionSummary(state: PerceptionUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "People detected",
                value = state.personCount.toString(),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Context objects",
                value = state.contextObjectDetections.size.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        InfoCard {
        Text("Top 3 sounds heard", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        val topSounds = state.soundDetections.take(3)
        if (topSounds.isEmpty()) {
            Text(
                text = "Waiting for YAMNet audio capture.",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        } else {
            topSounds.forEach { detection ->
                DetectionRow(
                    label = if (detection.indicatesHumanPresence) {
                        detection.label
                    } else {
                        "${detection.label} (support)"
                    },
                    confidence = detection.confidence,
                    highlight = detection.indicatesHumanPresence,
                )
            }
        }
        }
    }
}

@Composable
private fun CaptureHeader(
    state: PerceptionUiState,
    previewView: PreviewView?,
    onStartPerception: (PreviewView, CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Capture interface", fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.captureStatusLabel(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.isRunning) {
                OutlinedButton(onClick = onStopPerception) {
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = { previewView?.let { onStartPerception(it, CaptureStartSource.Manual) } },
                    enabled = previewView != null,
                ) {
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun SettingPage(
    uiState: DashboardUiState,
    onScanUsb: () -> Unit,
    userEmail: String,
    userDisplayName: String?,
    onSignOut: () -> Unit,
    isDeletingAccount: Boolean = false,
    deleteAccountError: String? = null,
    onDeleteAccount: () -> Unit = {},
    onClearDeleteError: () -> Unit = {},
    currentUserRole: UserRole? = null,
    onModeSelected: (UserRole) -> Unit = {},
) {
    val displayName = userDisplayName?.takeIf { it.isNotBlank() } ?: userEmail
    val avatarLetter = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "R"
    var showSetupGuide by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }

    if (showSetupGuide) {
        SetupGuideDialog(onDismiss = { showSetupGuide = false })
    }

    if (showModeDialog) {
        ModeSelectionDialog(
            currentRole = currentUserRole,
            canDismiss = true,
            onSelect = { role ->
                showModeDialog = false
                onModeSelected(role)
            },
            onDismiss = { showModeDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = {
                Text(
                    "This will permanently delete your account and all your data. This action cannot be undone.",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete Account") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 24.sp)

        // Setup Guide section
        Text("Device Setup", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        InfoCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSetupGuide = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Setup Guide", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Permissions, mount & camera view",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Profile section
        Text("Profile", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = avatarLetter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (userDisplayName != null) {
                            Text(
                                text = userEmail,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Phone Mode",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = when (currentUserRole) {
                                UserRole.Admin -> "Admin"
                                UserRole.Sensor -> "Sensor"
                                null -> "Not set"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                    TextButton(onClick = { showModeDialog = true }) {
                        Text("Change")
                    }
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDeletingAccount,
                ) {
                    Text("Sign Out")
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(
                        1.dp,
                        androidx.compose.material3.MaterialTheme.colorScheme.error,
                    ),
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete Account")
                    }
                }
                if (deleteAccountError != null) {
                    Text(
                        text = deleteAccountError,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                    TextButton(onClick = onClearDeleteError) { Text("Dismiss", fontSize = 12.sp) }
                }
            }
        }

        // Connection section
        Text("Connection", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        ConnectionCard(
            state = uiState.sensorState,
            freshness = uiState.packetFreshness,
            onScanUsb = onScanUsb,
        )

        // Status section
        Text("System status", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        StatusSummaryCard(
            sensorState = uiState.sensorState,
            freshness = uiState.packetFreshness,
            nowMillis = uiState.nowMillis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HallDropdown(
    selectedHall: Hall,
    onHallChange: (Hall) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = selectedHall.label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "▾",
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Hall.values().forEach { hall ->
                DropdownMenuItem(
                    text = {
                        Text(
                            hall.label,
                            fontWeight = if (hall == selectedHall) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { onHallChange(hall); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: SensorDashboardState,
    freshness: PacketFreshness,
    onScanUsb: () -> Unit,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(color = state.connectionStatus.statusColor(freshness))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = state.connectionStatus.label(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.connectionMessage,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(onClick = onScanUsb) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Scan")
            }
        }
    }
}

@Composable
private fun TelemetryMetricGrid(telemetry: RadarTelemetry?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Presence",
                value = when (telemetry?.presence) {
                    true -> "Detected"
                    false -> "Clear"
                    null -> "--"
                },
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Distance",
                value = telemetry?.distanceCm?.let { "$it cm" } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Temp",
                value = telemetry?.temperatureC?.let { "%.1f C".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Humidity",
                value = telemetry?.humidityPercent?.let { "%.1f %%".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RadarEnergyCard(
    telemetry: RadarTelemetry?,
    modifier: Modifier = Modifier,
) {
    InfoCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Usb,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text("LD2412", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        EnergyBar(label = "Moving", value = telemetry?.movingEnergy)
        Spacer(Modifier.height(10.dp))
        EnergyBar(label = "Stationary", value = telemetry?.stationaryEnergy)
        Spacer(Modifier.height(10.dp))
        Text(
            text = telemetry?.rawPacket ?: "Waiting for radar packet",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusSummaryCard(
    sensorState: SensorDashboardState,
    freshness: PacketFreshness,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    InfoCard(modifier = modifier) {
        if (freshness == PacketFreshness.Stale || sensorState.lastMalformedPacket != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Packet health", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text("Packet health", fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = freshness.label(sensorState.lastValidPacketAtMillis, nowMillis),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        FirebaseSyncStatusRow(sensorState = sensorState)
        Spacer(Modifier.height(8.dp))
        ActivityAnalysisStatusRow(sensorState = sensorState)
    }
}

@Composable
private fun FirebaseSyncStatusRow(sensorState: SensorDashboardState) {
    val syncState = sensorState.firebaseSyncState
    val color = when (syncState.status) {
        FirebaseSyncStatus.ConfigMissing -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
        FirebaseSyncStatus.Idle -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        FirebaseSyncStatus.Pending -> androidx.compose.material3.MaterialTheme.colorScheme.secondary
        FirebaseSyncStatus.Synced -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        FirebaseSyncStatus.Failed -> androidx.compose.material3.MaterialTheme.colorScheme.error
    }
    Column {
        Text("Firestore sync", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(
            text = syncState.message,
            color = color,
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivityAnalysisStatusRow(sensorState: SensorDashboardState) {
    val analysisState = sensorState.activityAnalysisState
    val color = when (analysisState.status) {
        ActivityAnalysisStatus.ConfigMissing -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
        ActivityAnalysisStatus.Idle -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        ActivityAnalysisStatus.Pending -> androidx.compose.material3.MaterialTheme.colorScheme.secondary
        ActivityAnalysisStatus.Complete -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        ActivityAnalysisStatus.Failed -> androidx.compose.material3.MaterialTheme.colorScheme.error
    }
    Column {
        Text("Gemini activity", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(
            text = analysisState.analysis?.let {
                "${it.activity} (${(it.confidence * 100).toInt()}%)"
            } ?: analysisState.message,
            color = color,
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LatestFrameCompactCard(
    state: PerceptionUiState,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    InfoCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("People detected", fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.latestFrameAtMillis.ageLabel(nowMillis),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = state.personCount.toString(),
                fontWeight = FontWeight.Bold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        FramePreview(
            frame = state.latestFrame,
            detections = state.yoloDetections,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Text(
            text = state.yoloMessage,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudioCompactCard(
    state: PerceptionUiState,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    InfoCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Human activity sounds", fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.latestAudioAtMillis.ageLabel(nowMillis),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (state.audioPresenceDetected) {
                    "${(state.audioPresenceScore * 100).toInt()}%"
                } else {
                    "${(state.audioRms * 100).toInt()}%"
                },
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        WaveformBar(waveform = state.audioWaveform)
        Text(
            text = state.yamnetMessage,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state.soundDetections.take(3).forEach { detection ->
            DetectionRow(
                label = if (detection.indicatesHumanPresence) {
                    detection.label
                } else {
                    "${detection.label} (support)"
                },
                confidence = detection.confidence,
                highlight = detection.indicatesHumanPresence,
            )
        }
    }
}

@Composable
private fun FramePreview(
    frame: Bitmap?,
    detections: List<ObjectDetection>,
    modifier: Modifier = Modifier,
) {
    val aspectRatio = frame?.let { it.width.toFloat() / it.height.toFloat() } ?: (16f / 9f)
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (frame == null) {
            Text(
                text = "No captured frame yet",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            return@Box
        }

        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = "Latest analyzed camera frame",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        DetectionOverlay(detections = detections)
    }
}

@Composable
private fun DetectionOverlay(detections: List<ObjectDetection>) {
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        detections.forEach { detection ->
            val box = detection.box
            val color = if (detection.isPerson) Color(0xFF69D7BF) else Color(0xFFFFB782)
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = box.left * size.width,
                    y = box.top * size.height,
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = (box.right - box.left) * size.width,
                    height = (box.bottom - box.top) * size.height,
                ),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

@Composable
private fun WaveformBar(waveform: List<Float>) {
    val barColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
    val emptyColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(emptyColor),
    ) {
        if (waveform.isEmpty()) return@Canvas
        val barWidth = size.width / waveform.size
        waveform.forEachIndexed { index, level ->
            val barHeight = (size.height * level).coerceAtLeast(2f)
            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * barWidth,
                    y = (size.height - barHeight) / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = (barWidth * 0.72f).coerceAtLeast(1f),
                    height = barHeight,
                ),
            )
        }
    }
}

@Composable
private fun DetectionRow(
    label: String,
    confidence: Float,
    highlight: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(confidence * 100).toInt()}%",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SensorLiveDataCard(telemetry: RadarTelemetry?) {
    val decision = remember(telemetry) { telemetry?.toOccupancyDecision() }

    val stateLabel = when (telemetry?.radarState) {
        0    -> "No Target"
        1    -> "Moving"
        2    -> "Stationary"
        3    -> "Both"
        else -> "--"
    }

    InfoCard {
        // ── Header ───────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Usb,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text("Live Sensor Data", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))

        // ── Occupancy verdict banner ──────────────────────────────────────────
        OccupancyVerdictBanner(decision = decision)
        Spacer(Modifier.height(16.dp))

        // ── 1 & 2: Environmental (SHT30) ─────────────────────────────────────
        Text(
            "Environmental (SHT30)",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Temperature",
                value = telemetry?.temperatureC?.let { "%.1f °C".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Humidity",
                value = telemetry?.humidityPercent?.let { "%.1f %%".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))

        // ── 3-7: Radar (LD2412) ───────────────────────────────────────────────
        Text(
            "Radar (LD2412)",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        // 3 — Radar State
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Radar State",
                value = stateLabel,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Distance",
                value = telemetry?.distanceCm?.let { "$it cm" } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        // 4 & 5 — Moving target (mov_d + mov_e)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Moving Target", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                text = telemetry?.movingDistanceCm?.let { "$it cm" } ?: "--",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        EnergyBar(
            label = "Moving Energy (≥${OccupancyThreshold.MOVING_ENERGY_MIN} = person)",
            value = telemetry?.movingEnergy,
            threshold = OccupancyThreshold.MOVING_ENERGY_MIN,
        )
        Spacer(Modifier.height(12.dp))

        // 6 & 7 — Stationary target (stat_d + stat_e)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Stationary Target", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                text = telemetry?.stationaryDistanceCm?.let { "$it cm" } ?: "--",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        EnergyBar(
            label = "Stationary Energy (≥${OccupancyThreshold.STATIONARY_ENERGY_MIN} = person)",
            value = telemetry?.stationaryEnergy,
            threshold = OccupancyThreshold.STATIONARY_ENERGY_MIN,
        )

        if (telemetry == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Connect sensor via OTG cable to see live data",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun OccupancyVerdictBanner(decision: com.rewatt.app.telemetry.OccupancyDecision?) {
    val occupied = decision?.occupied
    val containerColor = when (occupied) {
        true  -> Color(0xFF1B5E20)   // deep green
        false -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
        null  -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = when (occupied) {
        true  -> Color(0xFF69F0AE)   // bright green
        false -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        null  -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            when (occupied) {
                                true  -> Color(0xFF69F0AE)
                                false -> androidx.compose.material3.MaterialTheme.colorScheme.outline
                                null  -> androidx.compose.material3.MaterialTheme.colorScheme.outline
                            },
                        ),
                )
                Text(
                    text = when (occupied) {
                        true  -> "OCCUPIED — someone is in the room"
                        false -> "EMPTY — no one detected"
                        null  -> "WAITING — no sensor data"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = labelColor,
                )
            }
            if (decision != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = decision.reason,
                    fontSize = 12.sp,
                    color = labelColor.copy(alpha = 0.80f),
                )
            }
        }
    }
}

@Composable
private fun EnergyBar(label: String, value: Int?, threshold: Int? = null) {
    val progress = min((value ?: 0) / 100f, 1f)
    val aboveThreshold = threshold != null && (value ?: 0) >= threshold
    val barColor = if (aboveThreshold) {
        androidx.compose.material3.MaterialTheme.colorScheme.primary
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.outline
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                text = value?.toString() ?: "--",
                fontSize = 12.sp,
                fontWeight = if (aboveThreshold) FontWeight.Bold else FontWeight.Normal,
                color = if (aboveThreshold) androidx.compose.material3.MaterialTheme.colorScheme.primary
                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = barColor,
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) {
                androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
            } else {
                androidx.compose.material3.MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = title,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            content = content,
        )
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun PerceptionUiState.captureStatusLabel(): String =
    when {
        isRunning && captureStartSource == CaptureStartSource.Auto ->
            "Auto capture triggered by LD2412 presence."

        isRunning && captureStartSource == CaptureStartSource.Manual ->
            "Manual capture running."

        else -> statusMessage
    }

private fun UsbConnectionStatus.label(): String =
    when (this) {
        UsbConnectionStatus.Disconnected -> "Disconnected"
        UsbConnectionStatus.Scanning -> "Scanning"
        UsbConnectionStatus.PermissionRequired -> "Permission required"
        UsbConnectionStatus.PermissionDenied -> "Permission denied"
        UsbConnectionStatus.Connected -> "Connected"
        UsbConnectionStatus.DriverMissing -> "Driver missing"
        UsbConnectionStatus.Error -> "USB error"
    }

@Composable
private fun UsbConnectionStatus.statusColor(freshness: PacketFreshness): Color =
    when {
        this == UsbConnectionStatus.Connected && freshness == PacketFreshness.Fresh ->
            androidx.compose.material3.MaterialTheme.colorScheme.primary

        this == UsbConnectionStatus.Connected && freshness == PacketFreshness.Stale ->
            androidx.compose.material3.MaterialTheme.colorScheme.tertiary

        this == UsbConnectionStatus.Error || this == UsbConnectionStatus.PermissionDenied ->
            androidx.compose.material3.MaterialTheme.colorScheme.error

        else -> androidx.compose.material3.MaterialTheme.colorScheme.outline
    }

private fun PacketFreshness.label(lastValidPacketAtMillis: Long?, nowMillis: Long): String =
    when (this) {
        PacketFreshness.WaitingForFirstPacket -> "Waiting for first packet."
        PacketFreshness.Fresh -> "Live telemetry is fresh."
        PacketFreshness.Stale -> {
            val ageSeconds = lastValidPacketAtMillis?.let { (nowMillis - it) / 1_000L } ?: 0L
            "Stale: last packet ${ageSeconds}s ago."
        }
    }

private fun Long?.ageLabel(nowMillis: Long): String =
    this?.let { timestamp ->
        val ageSeconds = ((nowMillis - timestamp) / 1_000L).coerceAtLeast(0L)
        "Captured ${ageSeconds}s ago"
    } ?: "Waiting for capture"

private fun deriveNowAction(
    nowMillis: Long,
    personCount: Int,
    temperatureC: Float?,
    occupancyHint: String?,
    hall: Hall = Hall.Hall1,
): Pair<String, String> {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    val dayBreaks = hallDayBreaks[hall] ?: emptyMap()

    if (dow >= 5) return "Turn off all systems" to "Weekend — no classes scheduled"
    if (inRange(h, m, timetableOvernight)) return "Turn off all systems" to "Overnight — no classes until 07:30"

    for (br in dayBreaks[dow] ?: emptyList()) {
        if (inRange(h, m, br))
            return "Turn off AC & lights" to "Break period until %02d:%02d".format(br.eh, br.em)
    }

    if ((occupancyHint == "likely_empty" || occupancyHint == "unclear") && personCount > 2)
        return "Keep AC on" to "Unexpected occupancy ($personCount people detected)"
    if (temperatureC != null && temperatureC > 27f && personCount > 0)
        return "Increase AC cooling" to "Temperature is %.1f°C".format(temperatureC)
    if (temperatureC != null && temperatureC < 22f && personCount > 0)
        return "Reduce AC cooling" to "Temperature is %.1f°C — too cold".format(temperatureC)

    return if (personCount > 0)
        "Maintain current settings" to "Normal class session in progress"
    else
        "No immediate action needed" to "Room appears empty"
}

// Maps level array → realistic per-hour occupancy counts (0-120).
// Deterministic variation is seeded by hall + day + hour so each hall looks unique.
private fun buildHourlyOccupancy(levels: IntArray, hall: Hall, dayIndex: Int): IntArray =
    IntArray(24) { hour ->
        val v = ((hour + 1) * (hall.ordinal + 2) * (dayIndex + 3)) % 19  // 0-18
        when (levels[hour]) {
            0    -> 0
            1    -> 12 + v          // 12-30  (ramp / wind-down)
            2    -> 44 + v          // 44-62  (moderate)
            3    -> 82 + v          // 82-100 (peak class)
            else -> 0
        }
    }

private fun acLightStateLabel(action: String): String = when {
    action.contains("Turn off", ignoreCase = true) -> "OFF"
    action.contains("Increase", ignoreCase = true) -> "COOL +"
    action.contains("Reduce", ignoreCase = true) -> "COOL -"
    else -> "ON"
}

private fun deriveSessionEndTime(nowMillis: Long, hall: Hall = Hall.Hall1): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    if (dow >= 5) return "No class"
    if (inRange(h, m, timetableOvernight)) return "07:30"
    val breaks = (hallDayBreaks[hall] ?: emptyMap())[dow] ?: emptyList()
    for (br in breaks) {
        if (inRange(h, m, br)) return "%02d:%02d".format(br.eh, br.em)
    }
    val cur = h * 60 + m
    val next = breaks.filter { it.sh * 60 + it.sm > cur }.minByOrNull { it.sh * 60 + it.sm }
    return if (next != null) "%02d:%02d".format(next.sh, next.sm) else "18:30"
}

private fun buildDayNotifications(dayOffset: Int, nowMillis: Long, hall: Hall = Hall.Hall1): List<TimetableNotification> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, dayOffset)
    }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    if (dow >= 5) return listOf(
        TimetableNotification("weekend-off", "--", "No classes today", "Weekend — all systems off"),
    )
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val dayName = dayNames[dow]
    val result = mutableListOf<TimetableNotification>()
    val dayBreaks = (hallDayBreaks[hall] ?: emptyMap())[dow] ?: emptyList()

    fun add(h: Int, m: Int, id: String, title: String, reason: String) {
        result.add(TimetableNotification(
            id = "$dayName-$id",
            timeLabel = "%02d:%02d".format(h, m),
            title = title,
            reason = reason,
        ))
    }

    add(7, 30, "startup", "System startup check", "First class approaching ($dayName)")
    dayBreaks.forEachIndexed { i, br ->
        add(br.sh, br.sm, "break$i-off", "Turn off AC & lights",
            "Break starts at %02d:%02d ($dayName)".format(br.sh, br.sm))
        val preMin = (br.eh * 60 + br.em) - 30
        if (preMin > br.sh * 60 + br.sm) {
            add(preMin / 60, preMin % 60, "break$i-precool", "Pre-cool room to 23°C",
                "Class resumes at %02d:%02d ($dayName)".format(br.eh, br.em))
        }
    }
    add(18, 30, "overnight", "Turn off all systems", "No classes until tomorrow 07:30")
    return result.sortedBy { it.timeLabel }
}

private fun buildAllHallsDayNotifications(dayOffset: Int, nowMillis: Long): List<TimetableNotification> =
    Hall.values().flatMap { hall ->
        buildDayNotifications(dayOffset, nowMillis, hall).map { n ->
            n.copy(
                id = "${hall.name}-${n.id}",
                reason = "${hall.label} — ${n.reason}",
            )
        }
    }.sortedBy { it.timeLabel }

private fun buildUpcomingNotifications(nowMillis: Long, hall: Hall = Hall.Hall1): List<TimetableNotification> {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val nowDow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val nowTotalMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val hallBreaks = hallDayBreaks[hall] ?: emptyMap()

    data class Candidate(val offset: Int, val n: TimetableNotification)

    val candidates = mutableListOf<Candidate>()
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri")

    for (dayOffset in 0..1) {
        val dow = (nowDow + dayOffset) % 7
        if (dow >= 5) continue
        val dayName = dayNames[dow]
        val suffix = if (dayOffset == 1) " (tmr)" else ""

        fun add(h: Int, m: Int, id: String, title: String, reason: String) {
            val evtMin = h * 60 + m
            val diff = if (dayOffset == 0) evtMin - nowTotalMin else evtMin + (24 * 60 - nowTotalMin)
            if (diff > 0) candidates.add(Candidate(diff, TimetableNotification(
                id = "$dayName-$id",
                timeLabel = "%02d:%02d$suffix".format(h, m),
                title = title,
                reason = reason,
            )))
        }

        add(7, 30, "startup", "System startup check", "First class approaching ($dayName)")

        (hallBreaks[dow] ?: emptyList()).forEachIndexed { i, br ->
            add(br.sh, br.sm, "break$i-off", "Turn off AC & lights",
                "Break starts at %02d:%02d ($dayName)".format(br.sh, br.sm))
            val preMin = (br.eh * 60 + br.em) - 30
            if (preMin > br.sh * 60 + br.sm) {
                add(preMin / 60, preMin % 60, "break$i-precool", "Pre-cool room to 23°C",
                    "Class resumes at %02d:%02d ($dayName)".format(br.eh, br.em))
            }
        }

        val nextInfo = if (dow == 4) "Monday" else "tomorrow"
        add(18, 30, "overnight", "Turn off all systems", "No classes until $nextInfo 07:30")
    }

    return candidates.sortedBy { it.offset }.map { it.n }
}
