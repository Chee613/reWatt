package com.rewatt.app.ui.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.rewatt.app.BuildConfig
import com.rewatt.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.rewatt.app.data.ActivityAnalysisStatus
import com.rewatt.app.data.EnergyAdviceState
import com.rewatt.app.data.EnergyWasteSummary
import com.rewatt.app.data.EnergyAdviceStatus
import com.rewatt.app.data.FirebaseSyncState
import com.rewatt.app.data.FirebaseSyncStatus
import com.rewatt.app.data.LearnedSegment
import com.rewatt.app.data.MalaysiaHolidays
import com.rewatt.app.data.RecentCapture
import androidx.compose.ui.platform.LocalContext
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.data.SensorNodeSnapshot
import com.rewatt.app.data.WeatherState
import com.rewatt.app.data.WeatherStatus
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.perception.ObjectDetection
import com.rewatt.app.perception.PerceptionUiState
import com.rewatt.app.perception.YoloModel
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.RadarTelemetry
import com.rewatt.app.usb.UsbConnectionStatus
import androidx.compose.animation.AnimatedVisibility
import com.rewatt.app.ui.theme.EaseOutStrong
import com.rewatt.app.ui.theme.pressScale
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
import com.rewatt.app.data.HallSnapshot
import com.rewatt.app.data.UserRole
import com.rewatt.app.telemetry.OccupancyThreshold
import com.rewatt.app.telemetry.toOccupancyDecision
import com.rewatt.app.ui.ModeSelectionDialog
import com.rewatt.app.ui.SetupGuideDialog
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.min

private enum class ReWattTab(val labelRes: Int, val icon: ImageVector) {
    Live(R.string.nav_live, Icons.Outlined.Sensors),
    Analytic(R.string.nav_analytic, Icons.Outlined.BarChart),
    Capture(R.string.nav_capture, Icons.Outlined.CameraAlt),
    Setting(R.string.nav_setting, Icons.Outlined.Settings),
}

/** Sub-tabs shown at the top of the LIVE page (Manage is Admin-only). */
private enum class LiveSubTab(val labelRes: Int) {
    Overview(R.string.subtab_overview),
    Manage(R.string.subtab_manage),
}

/** A node is considered online if it has reported within the last 15 minutes. */
private const val NODE_ONLINE_WINDOW_MILLIS = 15 * 60_000L

/** Fleet view: a vertical list of every tracking sensor node across the venue. */
@Composable
private fun ManageContent(
    nodes: List<SensorNodeSnapshot>,
    nowMillis: Long,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "Sensor fleet",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        if (nodes.isEmpty()) {
            item {
                Text(
                    text = "No sensors reporting yet.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        } else {
            items(nodes, key = { it.roomId + "/" + it.nodeId }) { node ->
                val online = node.lastUpdatedAt != null &&
                    nowMillis - node.lastUpdatedAt < NODE_ONLINE_WINDOW_MILLIS
                SensorNodeCard(node = node, online = online)
            }
        }
    }
}

@Composable
private fun SensorNodeCard(node: SensorNodeSnapshot, online: Boolean) {
    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val accent = if (online) colorScheme.primary else colorScheme.error
    val batteryMissing = node.batteryPercent == null || node.batteryPercent == 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = if (online) 0.4f else 1f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header: sensor name + connectivity pill ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Sensors,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = node.roomId,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
                StatusPill(online = online, accent = accent)
            }

            Spacer(Modifier.height(10.dp))

            if (!online) {
                NodeInfoRow(
                    icon = Icons.Outlined.WifiOff,
                    text = "Offline / Connection Lost",
                    tint = colorScheme.error,
                )
            } else {
                NodeInfoRow(
                    icon = Icons.Outlined.Groups,
                    text = if (node.personCount > 0) {
                        "Occupied · ${node.personCount} ${if (node.personCount == 1) "person" else "people"}"
                    } else {
                        "Empty"
                    },
                )
                node.lightsOn?.let { on ->
                    NodeInfoRow(
                        icon = Icons.Outlined.Lightbulb,
                        text = "Lights ${if (on) "ON" else "OFF"}",
                    )
                }
                node.temperatureC?.let { temp ->
                    val humidity = node.humidityPercent?.let { " · ${it.toInt()}% RH" } ?: ""
                    NodeInfoRow(
                        icon = Icons.Outlined.Thermostat,
                        text = "${"%.1f".format(temp)}°C$humidity",
                    )
                }
            }

            // Battery is shown even when offline (last-known reading).
            NodeInfoRow(
                icon = if (batteryMissing) Icons.Outlined.BatteryAlert else Icons.Outlined.BatteryFull,
                text = "Battery ${node.batteryPercent ?: "--"}%",
                tint = if (batteryMissing) colorScheme.error else null,
            )
        }
    }
}

/** A single icon + label row used inside [SensorNodeCard]. */
@Composable
private fun NodeInfoRow(
    icon: ImageVector,
    text: String,
    tint: Color? = null,
) {
    val color = tint ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun StatusPill(online: Boolean, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (online) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (online) "Online" else "Offline",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onUploadYoloImage: () -> Unit,
    onSelectYoloModel: (YoloModel) -> Unit = {},
    onDebugCapture: () -> Unit = {},
    onRefreshWeather: () -> Unit = {},
    onRequestEnergyAdvice: () -> Unit = {},
    onRefreshWaste: () -> Unit = {},
    wasteRefreshing: Boolean = false,
    userEmail: String,
    userDisplayName: String?,
    onSignOut: () -> Unit,
    isDeletingAccount: Boolean = false,
    deleteAccountError: String? = null,
    onDeleteAccount: () -> Unit = {},
    onClearDeleteError: () -> Unit = {},
    currentUserRole: UserRole? = null,
    spaceName: String? = null,
    hallSnapshot: HallSnapshot? = null,
    hallSchedules: Map<String, Array<IntArray>> = emptyMap(),
    hallMeanSchedules: Map<String, Array<IntArray>> = emptyMap(),
    hallMinuteSchedules: Map<String, Array<IntArray>> = emptyMap(),
    learnedSchedules: Map<String, List<List<LearnedSegment>>> = emptyMap(),
    recentCaptures: List<RecentCapture> = emptyList(),
    wasteSummary: EnergyWasteSummary? = null,
    sensorFleet: List<SensorNodeSnapshot> = emptyList(),
    onRefreshFleet: () -> Unit = {},
    onHallSelect: (String) -> Unit = {},
    onModeSelected: (UserRole) -> Unit = {},
    onRenameSpace: (String) -> Unit = {},
    onSetCapacity: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ReWattTab.Live) }
    var selectedHall by rememberSaveable { mutableStateOf(Hall.Hall1) }

    // Keep ViewModel in sync with the locally-selected hall so Firestore listener switches
    LaunchedEffect(selectedHall) { onHallSelect(selectedHall.label) }
    var showYoloUpload by rememberSaveable { mutableStateOf(true) }
    var pendingCaptureSource by remember { mutableStateOf<CaptureStartSource?>(null) }
    var lastAutoTriggerAtMillis by remember { mutableStateOf<Long?>(null) }
    var autoSuppressedUntilClear by remember { mutableStateOf(false) }

    // If role changes to Admin while Capture tab is active, fall back to Live
    LaunchedEffect(currentUserRole) {
        if (currentUserRole == UserRole.Admin && selectedTab == ReWattTab.Capture) {
            selectedTab = ReWattTab.Live
        }
        // Sensor mode hides Live & Analytic, so redirect off them to Capture.
        if (currentUserRole == UserRole.Sensor &&
            (selectedTab == ReWattTab.Live || selectedTab == ReWattTab.Analytic)
        ) {
            selectedTab = ReWattTab.Capture
        }
    }

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
            if (currentUserRole != UserRole.Admin) selectedTab = ReWattTab.Capture
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
                            val modeLabel = when (currentUserRole) {
                                UserRole.Admin -> "Admin"
                                UserRole.Sensor -> "Sensor"
                                else -> null
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = userDisplayName?.takeIf { it.isNotBlank() } ?: userEmail,
                                    fontSize = 12.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (modeLabel != null) {
                                    Text(
                                        text = "($modeLabel)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                    )
                                }
                            }
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
                val visibleTabs = ReWattTab.values().filter { tab ->
                    when (currentUserRole) {
                        UserRole.Admin -> tab != ReWattTab.Capture
                        UserRole.Sensor -> tab != ReWattTab.Live && tab != ReWattTab.Analytic
                        else -> true
                    }
                }
                visibleTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
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
                    onRefreshWeather = onRefreshWeather,
                    onRequestEnergyAdvice = onRequestEnergyAdvice,
                    onRefreshWaste = onRefreshWaste,
                    wasteRefreshing = wasteRefreshing,
                    currentUserRole = currentUserRole,
                    spaceName = spaceName,
                    hallSnapshot = hallSnapshot,
                    wasteSummary = wasteSummary,
                    hallMeanSchedules = hallMeanSchedules,
                    sensorFleet = sensorFleet,
                    nowMillis = uiState.nowMillis,
                    onRefreshFleet = onRefreshFleet,
                )

                ReWattTab.Analytic -> AnalyticPage(
                    uiState = uiState,
                    perceptionState = perceptionState,
                    selectedHall = selectedHall,
                    onHallChange = { selectedHall = it },
                    hallSchedules = hallSchedules,
                    hallMeanSchedules = hallMeanSchedules,
                    hallMinuteSchedules = hallMinuteSchedules,
                    learnedSchedules = learnedSchedules,
                    userEmail = userEmail,
                )

                ReWattTab.Capture -> CapturePage(
                    state = perceptionState,
                    nowMillis = uiState.nowMillis,
                    pendingCaptureSource = pendingCaptureSource,
                    onPendingCaptureConsumed = { pendingCaptureSource = null },
                    onStartPerception = onStartPerception,
                    onStopPerception = ::stopCapture,
                    onUploadYoloImage = onUploadYoloImage,
                    showYoloUpload = showYoloUpload,
                    onSelectYoloModel = onSelectYoloModel,
                    onDebugCapture = onDebugCapture,
                    telemetry = uiState.sensorState.latestTelemetry,
                    currentUserRole = currentUserRole,
                    spaceName = spaceName,
                    firebaseSyncState = uiState.sensorState.firebaseSyncState,
                    recentCaptures = recentCaptures,
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
                    spaceName = spaceName,
                    onModeSelected = onModeSelected,
                    onRenameSpace = onRenameSpace,
                    onSetCapacity = onSetCapacity,
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
    onRefreshWeather: () -> Unit = {},
    onRequestEnergyAdvice: () -> Unit = {},
    onRefreshWaste: () -> Unit = {},
    wasteRefreshing: Boolean = false,
    currentUserRole: UserRole? = null,
    spaceName: String? = null,
    hallSnapshot: HallSnapshot? = null,
    wasteSummary: EnergyWasteSummary? = null,
    hallMeanSchedules: Map<String, Array<IntArray>> = emptyMap(),
    sensorFleet: List<SensorNodeSnapshot> = emptyList(),
    nowMillis: Long = System.currentTimeMillis(),
    onRefreshFleet: () -> Unit = {},
) {
    @Composable
    fun overview() = OverviewContent(
        uiState = uiState,
        perceptionState = perceptionState,
        selectedHall = selectedHall,
        onHallChange = onHallChange,
        onStartCapture = onStartCapture,
        onRefreshWeather = onRefreshWeather,
        onRequestEnergyAdvice = onRequestEnergyAdvice,
        onRefreshWaste = onRefreshWaste,
        wasteRefreshing = wasteRefreshing,
        currentUserRole = currentUserRole,
        spaceName = spaceName,
        hallSnapshot = hallSnapshot,
        wasteSummary = wasteSummary,
        hallMeanSchedules = hallMeanSchedules,
    )

    // The Manage fleet view is Admin-only; everyone else sees the Overview directly.
    if (currentUserRole != UserRole.Admin) {
        overview()
        return
    }

    var subTab by rememberSaveable { mutableStateOf(LiveSubTab.Overview) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab.ordinal) {
            LiveSubTab.values().forEach { tab ->
                Tab(
                    selected = subTab == tab,
                    onClick = { subTab = tab },
                    text = { Text(stringResource(tab.labelRes)) },
                )
            }
        }
        when (subTab) {
            LiveSubTab.Overview -> overview()
            LiveSubTab.Manage -> {
                // Force-fetch the latest stored node data each time Manage opens, so the
                // admin sees the freshest values without waiting for the next capture sync.
                LaunchedEffect(Unit) { onRefreshFleet() }
                ManageContent(nodes = sensorFleet, nowMillis = nowMillis)
            }
        }
    }
}

@Composable
private fun OverviewContent(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    selectedHall: Hall,
    onHallChange: (Hall) -> Unit,
    onStartCapture: () -> Unit,
    onRefreshWeather: () -> Unit = {},
    onRequestEnergyAdvice: () -> Unit = {},
    onRefreshWaste: () -> Unit = {},
    wasteRefreshing: Boolean = false,
    currentUserRole: UserRole? = null,
    spaceName: String? = null,
    hallSnapshot: HallSnapshot? = null,
    wasteSummary: EnergyWasteSummary? = null,
    hallMeanSchedules: Map<String, Array<IntArray>> = emptyMap(),
) {
    val telemetry = uiState.sensorState.latestTelemetry
    val analysis = uiState.sensorState.activityAnalysisState.analysis

    // For Admin, pull live values from the Firestore snapshot of the selected hall.
    // For Sensor, use the local perception state and USB radar readings.
    val isAdmin = currentUserRole == UserRole.Admin
    val displayPersonCount = if (isAdmin) hallSnapshot?.personCount ?: 0 else perceptionState.personCount
    val displayTempC = if (isAdmin && hallSnapshot?.temperatureC != null) hallSnapshot.temperatureC else telemetry?.temperatureC?.toDouble()
    val displayHumidity = if (isAdmin && hallSnapshot?.humidityPercent != null) hallSnapshot.humidityPercent else telemetry?.humidityPercent?.toDouble()

    val nowAction = remember(uiState.nowMillis, displayPersonCount, displayTempC, displayHumidity, analysis?.occupancyHint, selectedHall, hallMeanSchedules) {
        deriveNowAction(
            nowMillis = uiState.nowMillis,
            personCount = displayPersonCount,
            temperatureC = displayTempC?.toFloat(),
            occupancyHint = analysis?.occupancyHint,
            hall = selectedHall,
            schedule = hallMeanSchedules[selectedHall.label],
            humidityPercent = displayHumidity,
        )
    }
    val sessionEndTime = remember(uiState.nowMillis, selectedHall, hallMeanSchedules) {
        deriveSessionEndTime(uiState.nowMillis, selectedHall, hallMeanSchedules[selectedHall.label])
    }

    val weather = uiState.weather
    val energyAdvice = uiState.energyAdvice
    val outdoorTempC = weather.info?.temperatureC
    val deltaT = if (outdoorTempC != null && displayTempC != null) outdoorTempC - displayTempC else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (currentUserRole == UserRole.Sensor && spaceName != null) {
            Text(spaceName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        } else {
            HallDropdown(selectedHall = selectedHall, onHallChange = onHallChange)
        }

        // ── Outdoor weather + deltaT ──────────────────────────────────────────
        WeatherCard(
            weather = weather,
            deltaT = deltaT,
            onRefreshWeather = onRefreshWeather,
        )

        // ── Gemini insights: AC suggestion + yesterday's wasted energy, framed together ───────
        GeminiFrame {
            EnergyAdviceCard(
                state = energyAdvice,
                onRequestEnergyAdvice = onRequestEnergyAdvice,
            )
            wasteSummary?.let {
                EnergyWasteCard(summary = it, onRefresh = onRefreshWaste, refreshing = wasteRefreshing)
            }
        }

        // ── 6-card grid ──────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.m_occupancy),
                value = when {
                    displayPersonCount > 0 -> stringResource(R.string.occ_ppl, displayPersonCount)
                    telemetry?.presence == true -> stringResource(R.string.occ_detected)
                    isAdmin && hallSnapshot != null -> stringResource(R.string.occ_empty)
                    telemetry != null -> stringResource(R.string.occ_empty)
                    else -> "--"
                },
                modifier = Modifier.weight(1f),
                leadingEmoji = "👥",
            )
            MetricCard(
                title = stringResource(R.string.m_ac_lights),
                value = acLightStateLabel(nowAction.acState),
                modifier = Modifier.weight(1f),
                leadingEmoji = "❄️",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.m_temperature),
                value = displayTempC?.let { "%.1f °C".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
                leadingEmoji = "🌡️",
            )
            MetricCard(
                title = stringResource(R.string.m_humidity),
                value = displayHumidity?.let { "%.1f %%".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
                leadingEmoji = "💧",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.m_session_ends),
                value = sessionEndTime,
                modifier = Modifier.weight(1f),
                leadingEmoji = "⏱️",
            )
        }

        // ── Activity section ─────────────────────────────────────────────────
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.dash_activity), fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                            stringResource(R.string.label_now),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(nowAction.label(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        nowAction.reason(),
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
                    Text(if (perceptionState.isRunning) stringResource(R.string.btn_open) else stringResource(R.string.btn_capture))
                }
            }
        }

    }
}

@Composable
private fun WeatherCard(
    weather: WeatherState,
    deltaT: Double?,
    onRefreshWeather: () -> Unit,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.weather_title), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            // Subtle inline refresh: a circular arrow that spins while loading instead of a big pill.
            val spin = rememberInfiniteTransition(label = "wxSpin").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "wxRot",
            ).value
            val rotation = if (weather.status == WeatherStatus.Loading) spin else 0f
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = weather.status != WeatherStatus.Loading,
                        onClick = onRefreshWeather,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "↻",
                    fontSize = 18.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val info = weather.info
        if (info != null) {
            // Weather state at a glance: a dynamic emoji next to the condition text.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(weatherEmoji(info.conditionType, info.conditionText), fontSize = 30.sp)
                Text(
                    text = info.conditionText ?: stringResource(R.string.weather_current_conditions),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            // Color-code the indoor/outdoor delta: warm when hotter outside (AC strain),
            // cool when cooler outside (free-cooling opportunity).
            val deltaColor = when {
                deltaT == null -> Color.Unspecified
                deltaT >= 0.5 -> Color(0xFFE2683C)
                deltaT <= -0.5 -> Color(0xFF2E9E6B)
                else -> Color.Unspecified
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(
                    title = stringResource(R.string.weather_outdoor_temp),
                    value = info.temperatureC?.let { "%.1f °C".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = stringResource(R.string.weather_outdoor_rh),
                    value = info.humidityPercent?.let { "%.0f %%".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = stringResource(R.string.weather_delta_indoor),
                    value = deltaT?.let { "%+.1f °C".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                    valueColor = deltaColor,
                )
            }
        } else {
            Text(
                text = weather.message,
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Pick a weather emoji that tracks the actual weather. Prefers the Google Weather API's stable English
 * [conditionType] enum (e.g. CLOUDY, RAIN, THUNDERSTORM) so it stays correct even when [conditionText]
 * is localized (e.g. Malay "Mendung"); falls back to text keywords, then a neutral icon.
 */
private fun weatherEmoji(conditionType: String?, conditionText: String? = null): String {
    val t = (conditionType ?: "").uppercase()
    val byType = when {
        t.isBlank() -> null
        "THUNDER" in t -> "⛈️"
        "RAIN" in t || "SHOWER" in t || "DRIZZLE" in t -> "🌧️"
        "SNOW" in t || "HAIL" in t || "SLEET" in t || "BLIZZARD" in t -> "❄️"
        "FOG" in t || "HAZE" in t || "MIST" in t || "SMOKE" in t -> "🌫️"
        "WIND" in t -> "💨"
        "PARTLY" in t || "MOSTLY_CLEAR" in t -> "🌤️"
        "CLOUD" in t -> "☁️"
        "CLEAR" in t || "SUNNY" in t -> "☀️"
        else -> null
    }
    if (byType != null) return byType
    val c = (conditionText ?: "").lowercase()
    return when {
        "thunder" in c || "storm" in c -> "⛈️"
        "rain" in c || "drizzle" in c || "shower" in c -> "🌧️"
        "snow" in c || "sleet" in c || "ice" in c -> "❄️"
        "fog" in c || "mist" in c || "haze" in c || "smoke" in c -> "🌫️"
        "partly" in c || "few" in c || "scattered" in c -> "🌤️"
        "cloud" in c || "overcast" in c -> "☁️"
        "clear" in c || "sun" in c -> "☀️"
        else -> "🌡️"
    }
}

@Composable
private fun EnergyAdviceCard(
    state: EnergyAdviceState,
    onRequestEnergyAdvice: () -> Unit,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ac_suggestion_title), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = "Powered by Gemini",
                    fontSize = 11.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onRequestEnergyAdvice,
                enabled = state.status != EnergyAdviceStatus.Pending,
                shape = RoundedCornerShape(16.dp),
                elevation = null,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                // Match the Gemini gradient of the surrounding frame; compact size.
                modifier = Modifier
                    .height(34.dp)
                    .background(
                        Brush.linearGradient(GeminiGradient),
                        RoundedCornerShape(16.dp),
                    ),
            ) {
                Text(
                    text = if (state.status == EnergyAdviceStatus.Pending) stringResource(R.string.btn_thinking) else stringResource(R.string.btn_suggest_action),
                    fontSize = 12.sp,
                )
            }
        }

        val advice = state.advice
        if (advice != null) {
            Spacer(Modifier.height(10.dp))
            Text(advice.acAction, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                advice.recommendedAcStartTime?.let { BulletPoint(stringResource(R.string.adv_start_ac, it)) }
                advice.targetSetpointC?.let { BulletPoint(stringResource(R.string.adv_target_setpoint, "%.0f".format(it))) }
                state.occupancy?.let {
                    BulletPoint(stringResource(R.string.adv_occupancy, it.predictedOccupancy, occupancyLevelLabel(it.occupancyLevel)))
                }
                BulletPoint(stringResource(R.string.adv_reason, advice.reason))
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                state.message,
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Red used for the wasted-energy headline figures, so the cost always reads as a warning. */
private val WasteRed = Color(0xFFD32F2F)

@Composable
private fun EnergyWasteCard(
    summary: EnergyWasteSummary,
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
) {
    InfoCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.waste_title), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                // Manual recompute via Gemini; the arrow spins while a refresh is in flight.
                val spin = rememberInfiniteTransition(label = "wasteSpin").animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                    label = "wasteRot",
                ).value
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !refreshing, onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↻",
                        fontSize = 18.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(if (refreshing) spin else 0f),
                    )
                }
            }
            Text(
                text = "Powered by Gemini" + summary.dateLabel.takeIf { it.isNotBlank() }?.let { "  ·  $it" }.orEmpty(),
                fontSize = 11.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (summary.wastedKwh > 0.0 || summary.costRm > 0.0) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text(
                            "%.2f kWh".format(summary.wastedKwh),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = WasteRed,
                        )
                        Text("wasted", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text(
                            "RM %.2f".format(summary.costRm),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = WasteRed,
                        )
                        Text("est. cost", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BulletPoint("${summary.wastedMinutes} min of lights/AC left on while empty")
                    if (summary.roomType.isNotBlank()) {
                        BulletPoint("${summary.roomType}: ~${summary.acUnits} AC units, ~${summary.lightbulbs} lights")
                    }
                    if (summary.summary.isNotBlank()) BulletPoint(summary.summary)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    summary.summary.ifBlank { "No wasted energy detected yesterday." },
                    fontSize = 13.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "•",
            fontSize = 13.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AnalyticPage(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    selectedHall: Hall,
    onHallChange: (Hall) -> Unit,
    hallSchedules: Map<String, Array<IntArray>> = emptyMap(),
    hallMeanSchedules: Map<String, Array<IntArray>> = emptyMap(),
    hallMinuteSchedules: Map<String, Array<IntArray>> = emptyMap(),
    learnedSchedules: Map<String, List<List<LearnedSegment>>> = emptyMap(),
    userEmail: String = "",
) {
    var isAction by rememberSaveable { mutableStateOf(true) }
    // Demo (fsktm) account has no live sensor schedule, so the Class Schedule, Weekly/Daily
    // occupancy and Action timeline are all derived from the SAME canonical class-schedule
    // sessions (hardcoded hallDayBreaks) instead of three separate Firestore collections that
    // would disagree. The History week tabs are untouched.
    val isDemo = userEmail.equals("tjh3281@gmail.com", ignoreCase = true)

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
                hallSchedules = hallSchedules,
                hallMeanSchedules = hallMeanSchedules,
                hallMinuteSchedules = hallMinuteSchedules,
                isDemo = isDemo,
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HallDropdown(selectedHall = selectedHall, onHallChange = onHallChange)
                ClassScheduleCard(
                    hall = selectedHall,
                    learnedDays = learnedSchedules[selectedHall.label],
                    minuteSchedule = hallMinuteSchedules[selectedHall.label],
                    showFallbackEstimate = isDemo,
                )
                WeeklyOccupancyCard(
                    hall = selectedHall,
                    // Demo: drive the heatmap + daily chart from the SAME canonical class-schedule
                    // grid as the Class Schedule above, so occupied/empty windows line up exactly.
                    firestoreSchedule = if (isDemo) null else hallSchedules[selectedHall.label],
                    minuteSchedule = if (isDemo) {
                        canonicalMinuteGrid(selectedHall, hallMinuteSchedules[selectedHall.label])
                    } else {
                        hallMinuteSchedules[selectedHall.label]
                    },
                )
                WeeklyHistoryCard(
                    hall = selectedHall,
                    minuteSchedule = hallMinuteSchedules[selectedHall.label],
                    showPreset = userEmail.equals("tjh3281@gmail.com", ignoreCase = true),
                )
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
        SegmentButton(stringResource(R.string.toggle_action), isAction,  onAction, Modifier.weight(1f))
        SegmentButton(stringResource(R.string.toggle_data),   !isAction, onData,   Modifier.weight(1f))
    }
}

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Animate the pill's fill + label colours so selection glides instead of snapping.
    val bg = androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary
        else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(220, easing = EaseOutStrong),
        label = "segBg",
    ).value
    val fg = androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
        else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = androidx.compose.animation.core.tween(220, easing = EaseOutStrong),
        label = "segFg",
    ).value
    Box(
        modifier = modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
        )
    }
}

// ── Timetable primitives (shared by heatmap + action engine) ─────────────────

internal enum class Hall(val label: String) {
    Hall1("DK1"),
    Hall2("DK2"),
    Hall3("MM8"),
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
// DK2/MM8 breaks are all >45 min each, 2–3 per weekday.
private val hallDayBreaks: Map<Hall, Map<Int, List<TR>>> = mapOf(
    Hall.Hall1 to mapOf(
        0 to listOf(TR(9, 30, 10, 30), TR(13, 0, 14, 30)),   // Mon  60+90 min
        1 to listOf(TR(10, 0, 11, 0),  TR(14, 0, 15, 30)),   // Tue  60+90 min
        2 to listOf(TR(9, 0, 10, 30),  TR(13, 30, 14, 30)),  // Wed  90+60 min
        3 to listOf(TR(10, 30, 12, 0), TR(15, 0, 16, 0)),    // Thu  90+60 min
        4 to listOf(TR(9, 30, 11, 0),  TR(13, 0, 14, 30)),   // Fri  90+90 min
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
)

internal data class TimetableNotification(
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
private fun WeeklyOccupancyCard(
    hall: Hall,
    firestoreSchedule: Array<IntArray>? = null,
    minuteSchedule: Array<IntArray>? = null,
) {
    val hasData = firestoreSchedule?.any { row -> row.any { v -> v != 0 } } == true
    val schedule = if (hasData) firestoreSchedule!! else remember(hall) { buildWeeklySchedule(hall) }
    val dayBreaks = if (hasData) emptyMap() else (hallDayBreaks[hall] ?: emptyMap())
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // 30-minute mean-occupancy grid (48 cells/day) for the heatmap. Built from the 10-min
    // bins when available; otherwise derived from the hourly levels so it still renders.
    val mean30: Array<IntArray> = remember(minuteSchedule, schedule) {
        val hasMinute = minuteSchedule != null && minuteSchedule.any { r -> r.any { it > 0 } }
        if (hasMinute) {
            Array(7) { d ->
                IntArray(48) { half ->
                    val b0 = half * 3
                    val total = (0..2).sumOf { minuteSchedule!![d].getOrElse(b0 + it) { 0 } }
                    total / 3   // mean over the three 10-min bins (missing counts as 0)
                }
            }
        } else {
            Array(7) { d -> IntArray(48) { half -> levelToCount(schedule[d].getOrElse(half / 2) { 0 }) } }
        }
    }

    InfoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.analytics_weekly_title), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        if (firestoreSchedule != null && !hasData) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.analytics_no_history), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.analytics_no_history_desc),
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        } else {
            OccupancyHeatmapLegend()
            Spacer(Modifier.height(10.dp))
            WeeklyHeatmapGrid(
                schedule = mean30,
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
                    DailyOccupancyChart(
                        dayIndex = day, schedule = schedule, dayBreaks = dayBreaks, hall = hall,
                        half30 = mean30[day],
                    )
                }
            }
        }
    }
}

// ── Weekly History (demo: 3 collected weeks rendered like the Class Schedule) ──────────────
private val HISTORY_WEEK_LABELS = listOf("10/5 – 16/5", "17/5 – 23/5", "24/5 – 30/5", "31/5 – 6/6")

private data class SchedRun(val start: Int, val end: Int, val mean: Int)

/** Contiguous non-zero runs (class sessions) in a day's 10-min bins, with each run's mean count. */
private fun deriveRuns(row: IntArray): List<SchedRun> {
    val runs = mutableListOf<SchedRun>()
    var start = -1; var sum = 0; var count = 0
    for (b in row.indices) {
        if (row[b] > 0) {
            if (start < 0) { start = b; sum = 0; count = 0 }
            sum += row[b]; count++
        } else if (start >= 0) {
            runs.add(SchedRun(start, b, if (count > 0) sum / count else 0)); start = -1
        }
    }
    if (start >= 0) runs.add(SchedRun(start, row.size, if (count > 0) sum / count else 0))
    return runs
}

/** Deterministic permutation of [0,1,2] from a seed. */
private fun perm3(seed: Int): IntArray = when (((seed % 6) + 6) % 6) {
    0 -> intArrayOf(0, 1, 2); 1 -> intArrayOf(0, 2, 1); 2 -> intArrayOf(1, 0, 2)
    3 -> intArrayOf(1, 2, 0); 4 -> intArrayOf(2, 0, 1); else -> intArrayOf(2, 1, 0)
}

/**
 * Build a distinct demo week from a base per-day 10-min schedule. Variation is applied at the
 * SESSION level:
 *  - occupancy: three values spaced by ≥25 (so pairwise >10) within ±~50 of the base mean,
 *  - start & end: only a small ±10-min drift per week (kept low; the displayed start is then
 *    snapped to :10/:50 at render), so weeks differ mainly by occupancy, not timing.
 * Sessions are kept separated by ≥1 empty bin so they never merge into one box. Deterministic.
 */
private fun varyWeekSchedule(base: Array<IntArray>, weekIndex: Int): Array<IntArray> =
    Array(base.size) { d ->
        val row = base[d]
        val out = IntArray(row.size) { 0 }
        var prevEnd = -2
        deriveRuns(row).forEachIndexed { idx, run ->
            val seed = d * 131 + run.start * 17 + idx * 7 + 1
            val spacing = 25 + (seed % 25)                       // 25..49 → pairwise >10, within ±50
            val low = (run.mean - spacing).coerceAtLeast(6)
            val trio = intArrayOf(low, low + spacing, low + 2 * spacing)
            // Map the tab index to a 0..2 variant (perm3/trio are 3-wide; the no-data week 2 never
            // reaches here, so data weeks 0,1,3 map to distinct variants 0,1,2).
            val w = weekIndex.coerceAtMost(2)
            val occ = trio[perm3(seed)[w]]
            // Low time drift (±10 min); start is snapped to :10/:50 when rendered.
            val startShift = ((seed + w * 13) % 3) - 1
            val endShift = ((seed + w * 29 + 5) % 3) - 1
            var ns = (run.start + startShift).coerceIn(0, row.size - 1)
            if (ns <= prevEnd) ns = (prevEnd + 1).coerceAtMost(row.size - 1)  // keep a gap → no merge
            var ne = (run.end + endShift).coerceIn(ns + 1, row.size)
            for (b in ns until ne) out[b] = occ
            prevEnd = ne
        }
        out
    }

/**
 * "History" block under the Weekly occupancy pattern: 3 week tabs, each showing the space's
 * class schedule (same grid as [ClassScheduleCard]) with its boxes' occupancy = the mean of the
 * 10-min bins for that day, slightly varied per week. Preset data is shown only when [showPreset]
 * is true (fsktm); other accounts get an empty "collecting" state. Follows the space picked by the
 * shared dropdown above (via [hall]).
 */
@Composable
private fun WeeklyHistoryCard(
    hall: Hall,
    minuteSchedule: Array<IntArray>?,
    showPreset: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.history_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

        if (!showPreset) {
            InfoCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.history_empty), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.history_empty_desc),
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return
        }

        var weekTab by rememberSaveable(hall) { mutableStateOf(0) }
        TabRow(selectedTabIndex = weekTab) {
            HISTORY_WEEK_LABELS.forEachIndexed { i, label ->
                Tab(
                    selected = weekTab == i,
                    onClick = { weekTab = i },
                    text = { Text(label, fontSize = 12.sp) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val weekSchedule = remember(minuteSchedule, weekTab, hall) {
            if (weekTab == 2) {
                // 24/5–30/5: no real data. Dummy bins only on the holiday days so the grid still
                // renders (those days are hidden behind the public-holiday banner anyway).
                Array(7) { d -> IntArray(144) { b -> if (d in setOf(1, 2, 3) && b == 60) 1 else 0 } }
            } else {
                minuteSchedule?.let { base ->
                    val varied = varyWeekSchedule(base, weekTab)
                    // DK1 / 10–16 May / Monday's first class was an outlier — make it identical to
                    // the 17–23 May week's Monday first class.
                    if (hall == Hall.Hall1 && weekTab == 0) {
                        matchMondayFirstClassToWeek(base, varied, weekIndex = 1)
                    } else {
                        varied
                    }
                }
            }
        }
        ClassScheduleCard(
            hall = hall,
            learnedDays = null,
            minuteSchedule = weekSchedule,
            showFallbackEstimate = weekTab != 2,
            titleOverride = HISTORY_WEEK_LABELS[weekTab],
            holidayDays = when (weekTab) {
                2 -> setOf(1, 2, 3)   // 24/5–30/5: Tue, Wed, Thu public holiday; other days blank
                3 -> setOf(0, 1)      // 31/5–6/6: Mon, Tue public holiday
                else -> emptySet()
            },
        )
    }
}

/**
 * Make Monday's first class in [varied] identical to the Monday first class of week [weekIndex]
 * (same time span + occupancy), to tame the DK1 / 10–16 May outlier without touching any other day,
 * week or hall.
 */
private fun matchMondayFirstClassToWeek(
    base: Array<IntArray>,
    varied: Array<IntArray>,
    weekIndex: Int,
): Array<IntArray> {
    val out = Array(varied.size) { varied[it].copyOf() }
    val monday = out.getOrNull(0) ?: return out
    val refMonday = varyWeekSchedule(base, weekIndex).getOrNull(0) ?: return out

    // start, endInclusive, occupancy of the first contiguous occupied run in a day's row.
    fun firstRun(row: IntArray): Triple<Int, Int, Int>? {
        var s = -1
        var e = -1
        for (b in row.indices) {
            if (row[b] > 0) { if (s < 0) s = b; e = b } else if (s >= 0) break
        }
        return if (s < 0) null else Triple(s, e, row[s])
    }

    val current = firstRun(monday) ?: return out
    val reference = firstRun(refMonday) ?: return out
    // Clear this week's first class, then stamp the reference week's (same bins + occupancy).
    for (b in current.first..current.second) monday[b] = 0
    for (b in reference.first..reference.second) monday[b] = reference.third
    return out
}

/** Representative headcount for a 0-3 occupancy level (cold-start fallback only). */
private fun levelToCount(level: Int): Int = when (level) {
    1 -> 25
    2 -> 60
    3 -> 120
    else -> 0
}

/** 6-step blue gradient spreading the 0-300 occupancy range so differences are visible. */
private fun heatColorForCount(c: Int): Color = when {
    c <= 0   -> Color.Transparent
    c < 50   -> Color(0xFFBBDEFB) // 1-49
    c < 100  -> Color(0xFF90CAF9) // 50-99
    c < 150  -> Color(0xFF64B5F6) // 100-149
    c < 200  -> Color(0xFF42A5F5) // 150-199
    c < 250  -> Color(0xFF1E88E5) // 200-249
    else     -> Color(0xFF0D47A1) // 250+
}

@Composable
private fun OccupancyHeatmapLegend() {
    val emptyColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeatmapLegendItem(emptyColor,        "0")
        HeatmapLegendItem(Color(0xFFBBDEFB), "50")
        HeatmapLegendItem(Color(0xFF90CAF9), "100")
        HeatmapLegendItem(Color(0xFF64B5F6), "150")
        HeatmapLegendItem(Color(0xFF42A5F5), "200")
        HeatmapLegendItem(Color(0xFF1E88E5), "250")
        HeatmapLegendItem(Color(0xFF0D47A1), "300")
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
    hourLevels: IntArray,   // 48 cells of 30-min mean occupancy counts
    modifier: Modifier = Modifier,
    rowHeight: androidx.compose.ui.unit.Dp = 26.dp,
) {
    val emptyColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val cells = hourLevels.size.coerceAtLeast(1)
    Canvas(
        modifier = modifier
            .height(rowHeight)
            .clip(RoundedCornerShape(13.dp))
            .background(emptyColor),
    ) {
        val cellW = size.width / cells
        val gap = 0.8f
        hourLevels.forEachIndexed { idx, count ->
            if (count <= 0) return@forEachIndexed
            drawRoundRect(
                color = heatColorForCount(count),
                topLeft = androidx.compose.ui.geometry.Offset(idx * cellW + gap / 2f, gap / 2f),
                size = androidx.compose.ui.geometry.Size(cellW - gap, size.height - gap),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
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
    half30: IntArray? = null,   // 48 cells of 30-min mean occupancy
) {
    val fullDayNames = stringArrayResource(R.array.day_names_full)
    val emptyColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val refLineColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val breaks = dayBreaks[dayIndex] ?: emptyList()

    // 48-cell 30-min day; fall back to expanding hourly levels when no real data.
    val occ48 = half30 ?: remember(hall, dayIndex) {
        val hourly = buildHourlyOccupancy(schedule[dayIndex], hall, dayIndex)
        IntArray(48) { hourly[it / 2] }
    }
    val firstBin = CLASS_GRID_START_HOUR * 2   // 16 → 08:00
    val lastBin  = CLASS_GRID_END_HOUR * 2 - 1  // 37 → 18:30 slot
    val numBins = lastBin - firstBin + 1        // 22 half-hour bars
    val peak = (firstBin..lastBin).maxOf { occ48.getOrElse(it) { 0 } }

    fun binLabel(bin: Int) = "%02d:%02d".format((bin * 30) / 60, (bin * 30) % 60)

    val weekendLabel = stringResource(R.string.sched_weekend)
    val noBreaksLabel = stringResource(R.string.sched_no_breaks)
    val breakTemplate = stringResource(R.string.sched_break)
    val breakLabel = if (breaks.isEmpty()) {
        if (dayIndex >= 5) weekendLabel else noBreaksLabel
    } else {
        breaks.joinToString("  •  ") { br ->
            breakTemplate.format(br.sh, br.sm, br.eh, br.em)
        }
    }

    var selectedBin by remember(dayIndex, hall) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.analytics_daily_pattern, fullDayNames[dayIndex]),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                text = stringResource(R.string.analytics_peak, peak),
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
                listOf("300", "200", "100", "0").forEach { label ->
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
                            val barW = size.width / numBins.toFloat()
                            val tapped = firstBin + (offset.x / barW).toInt().coerceIn(0, numBins - 1)
                            selectedBin = if (selectedBin == tapped) null else tapped
                        }
                    },
            ) {
                val maxCount = 300f
                val barW = size.width / numBins.toFloat()
                val gap = 1f
                // Reference lines at 100 and 200
                listOf(100f, 200f).forEach { count ->
                    val y = size.height * (1f - count / maxCount)
                    drawLine(
                        color = refLineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }

                // Draw a bar per 30-min bin in the 08:00–19:00 window
                for (bin in firstBin..lastBin) {
                    val count = occ48.getOrElse(bin) { 0 }
                    if (count == 0) continue
                    val isSelected = selectedBin == bin
                    val barHeight = size.height * (count / maxCount)
                    val x = (bin - firstBin) * barW
                    val color = if (isSelected) Color(0xFF7E57C2) else heatColorForCount(count)
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(x = x + gap, y = size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(width = barW - gap * 2, height = barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                    )
                }

                val selBin = selectedBin ?: return@Canvas
                if (selBin in firstBin..lastBin) {
                    val x = (selBin - firstBin) * barW
                    drawRoundRect(
                        color = Color(0xFF7E57C2).copy(alpha = 0.55f),
                        topLeft = androidx.compose.ui.geometry.Offset(x = x + gap, y = 0f),
                        size = androidx.compose.ui.geometry.Size(width = barW - gap * 2, height = size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                        style = Stroke(width = 1.5f),
                    )
                }
            }
        }
        // Hour axis across the 08–19 window
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("08", "10", "12", "14", "16", "18", "19").forEach { label ->
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val selBin = selectedBin
        AnimatedVisibility(
            visible = selBin != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (selBin != null) {
                val selCount = occ48.getOrElse(selBin) { 0 }
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
                        text = "${binLabel(selBin)} – ${binLabel(selBin + 1)}",
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
    hallSchedules: Map<String, Array<IntArray>> = emptyMap(),
    hallMeanSchedules: Map<String, Array<IntArray>> = emptyMap(),
    hallMinuteSchedules: Map<String, Array<IntArray>> = emptyMap(),
    isDemo: Boolean = false,
) {
    val hasAnySensorHistory = hallMeanSchedules.values.any { s -> s.any { row -> row.any { v -> v != 0 } } }
    // Demo always has the canonical class-schedule to draw from, so never show the empty state.
    val showEmptyState = !isDemo && hallMeanSchedules.isNotEmpty() && !hasAnySensorHistory

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
    val nowActions = remember(previewMillis, isToday, perceptionState.personCount, telemetry?.temperatureC, telemetry?.humidityPercent, analysis?.occupancyHint, hallMeanSchedules, hallMinuteSchedules) {
        Hall.values().map { hall ->
            hall to if (isDemo) {
                deriveNowActionFromSessions(
                    nowMillis = previewMillis,
                    personCount = if (isToday) perceptionState.personCount else 0,
                    temperatureC = if (isToday) telemetry?.temperatureC else null,
                    hall = hall,
                    humidityPercent = if (isToday) telemetry?.humidityPercent?.toDouble() else null,
                    minuteSchedule = hallMinuteSchedules[hall.label],
                )
            } else {
                deriveNowAction(
                    nowMillis = previewMillis,
                    personCount = if (isToday) perceptionState.personCount else 0,
                    temperatureC = if (isToday) telemetry?.temperatureC else null,
                    occupancyHint = if (isToday) analysis?.occupancyHint else null,
                    hall = hall,
                    schedule = hallMeanSchedules[hall.label],
                    humidityPercent = if (isToday) telemetry?.humidityPercent?.toDouble() else null,
                )
            }
        }
    }

    val dayNotifications = remember(previewDayOffset, uiState.nowMillis, hallMeanSchedules, hallMinuteSchedules, isDemo) {
        if (isDemo) {
            buildAllHallsSessionNotifications(previewDayOffset, uiState.nowMillis, hallMinuteSchedules)
        } else {
            buildAllHallsDayNotifications(previewDayOffset, uiState.nowMillis, hallMeanSchedules)
        }
    }
    var dismissedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var nowDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(nowActions.joinToString { "${it.second.labelRes}:${it.second.reasonArgs}" }) { nowDismissed = false }
    LaunchedEffect(previewDayOffset) { dismissedIds = emptySet() }

    if (showEmptyState) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Text("No sensor history yet", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    text = "Action recommendations will appear once the sensor has collected occupancy data for this building.",
                    fontSize = 13.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {

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
            text = if (isToday) stringResource(R.string.sched_today_schedule) else stringResource(R.string.sched_full_day_schedule),
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
    } // end Column
    } // end else (has sensor history)
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
                        text = stringResource(R.string.label_now),
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
    nowActions: List<Pair<Hall, NowAction>>,
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
                        text = stringResource(R.string.label_now),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    )
                }
                val doneInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.pressScale(doneInteraction),
                    interactionSource = doneInteraction,
                ) { Text("Done") }
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
                            text = action.label(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = action.reason(),
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
    val yesterdayLabel = stringResource(R.string.day_yesterday)
    val todayLabel = stringResource(R.string.day_today)
    val tomorrowLabel = stringResource(R.string.day_tomorrow)
    val plus2Label = stringResource(R.string.day_plus2)
    val dayOptions = remember(nowMillis, yesterdayLabel, todayLabel, tomorrowLabel, plus2Label) {
        listOf(-1 to yesterdayLabel, 0 to todayLabel, 1 to tomorrowLabel, 2 to plus2Label).map { (offset, label) ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val dayName = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val date = "%d/%d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
            Triple(offset, label, if (offset == 0) todayLabel else "$dayName $date")
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
                val doneInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .pressScale(doneInteraction),
                    interactionSource = doneInteraction,
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
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onUploadYoloImage: () -> Unit,
    showYoloUpload: Boolean,
    onSelectYoloModel: (YoloModel) -> Unit,
    onDebugCapture: () -> Unit = {},
    telemetry: RadarTelemetry? = null,
    currentUserRole: UserRole? = null,
    spaceName: String? = null,
    firebaseSyncState: FirebaseSyncState = FirebaseSyncState(),
    recentCaptures: List<RecentCapture> = emptyList(),
) {
    LaunchedEffect(pendingCaptureSource, state.isRunning) {
        val source = pendingCaptureSource ?: return@LaunchedEffect
        if (state.isRunning) {
            onPendingCaptureConsumed()
            return@LaunchedEffect
        }
        onStartPerception(source)
        onPendingCaptureConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (currentUserRole == UserRole.Sensor && spaceName != null) {
            Text(spaceName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                stringResource(R.string.capture_your_space),
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CaptureHeader(
            state = state,
            onStartPerception = onStartPerception,
            onStopPerception = onStopPerception,
            onUploadYoloImage = onUploadYoloImage,
            showYoloUpload = showYoloUpload,
            onSelectYoloModel = onSelectYoloModel,
            onDebugCapture = onDebugCapture,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.m_camera),
                value = if (state.cameraPermissionGranted) stringResource(R.string.setup_allowed) else stringResource(R.string.capture_need_access),
                modifier = Modifier.weight(1f),
                warning = !state.cameraPermissionGranted,
            )
        }
        CaptureDetectionSummary(state = state)
        LatestFrameCompactCard(
            state = state,
            nowMillis = nowMillis,
            modifier = Modifier.fillMaxWidth(),
        )
        SensorLiveDataCard(telemetry = telemetry)
        if (currentUserRole == UserRole.Sensor && spaceName != null) {
            DatabaseSection(
                spaceName = spaceName,
                firebaseSyncState = firebaseSyncState,
                recentCaptures = recentCaptures,
                nowMillis = nowMillis,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DatabaseSection(
    spaceName: String,
    firebaseSyncState: FirebaseSyncState,
    recentCaptures: List<RecentCapture>,
    nowMillis: Long,
) {
    val context = LocalContext.current
    val nodeId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.take(8) ?: "unknown"
    }
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    InfoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.db_database), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "$spaceName  ·  node $nodeId",
            fontSize = 12.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        )
        if (uid != null) {
            Text(
                "rewatt_users/$uid/rooms/$spaceName/telemetry",
                fontSize = 10.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Sync status row
        val (dotColor, statusLabel) = when (firebaseSyncState.status) {
            FirebaseSyncStatus.Synced        -> Color(0xFF4CAF50) to stringResource(R.string.sync_synced)
            FirebaseSyncStatus.Pending       -> Color(0xFFFFA000) to stringResource(R.string.sync_syncing)
            FirebaseSyncStatus.Failed        -> Color(0xFFF44336) to stringResource(R.string.sync_failed)
            FirebaseSyncStatus.ConfigMissing -> Color(0xFFF44336) to stringResource(R.string.sync_config_missing)
            FirebaseSyncStatus.Idle          -> Color(0xFF9E9E9E) to stringResource(R.string.sync_idle)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(6.dp))
            Text(statusLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            firebaseSyncState.lastSyncedAtMillis?.let { lastMs ->
                val ageS = (nowMillis - lastMs) / 1000
                val ageLabel = when {
                    ageS < 60   -> "${ageS}s ago"
                    ageS < 3600 -> "${ageS / 60}m ago"
                    else        -> "${ageS / 3600}h ago"
                }
                Spacer(Modifier.width(4.dp))
                Text("· $ageLabel", fontSize = 11.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (firebaseSyncState.message.isNotBlank()) {
            Text(
                firebaseSyncState.message,
                fontSize = 11.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Recent captures header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.db_recent_captures), fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "${recentCaptures.size} / 20  (this session)",
                fontSize = 11.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))

        if (recentCaptures.isEmpty()) {
            Text(
                stringResource(R.string.db_no_captures),
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(min(recentCaptures.size * 72, 280).dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(recentCaptures, key = { it.capturedAtMillis }) { capture ->
                    CaptureHistoryRow(capture = capture)
                }
            }
        }
    }
}

@Composable
private fun CaptureHistoryRow(capture: RecentCapture) {
    val cal = remember(capture.capturedAtMillis) {
        Calendar.getInstance().apply { timeInMillis = capture.capturedAtMillis }
    }
    val timeLabel = remember(capture.capturedAtMillis) {
        "%02d:%02d".format(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
        )
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    timeLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                )
                Text("👤 ${capture.personCount}", fontSize = 11.sp)
                capture.lightsOn?.let { on ->
                    Text(if (on) "💡 On" else "💡 Off", fontSize = 11.sp)
                }
                capture.temperatureC?.let { t ->
                    Text("🌡 ${"%.1f".format(t)}°C", fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (capture.synced) "✓" else "✗",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (capture.synced) Color(0xFF4CAF50) else Color(0xFFF44336),
                )
            }
            val detail = buildString {
                capture.geminiActivity?.let {
                    append("Gemini: ")
                    append(it)
                    capture.geminiOccupancyHint?.let { hint -> append(" ($hint)") }
                }
            }
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    fontSize = 11.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LivePerceptionSummary(perceptionState: PerceptionUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(
            title = stringResource(R.string.m_people_detected),
            value = perceptionState.personCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CaptureDetectionSummary(state: PerceptionUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(
            title = stringResource(R.string.m_people_detected),
            value = state.personCount.toString(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.m_room_light),
                value = state.lightsStateLabel(),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.m_exposure),
                value = state.exposureValueLabel(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CaptureHeader(
    state: PerceptionUiState,
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onUploadYoloImage: () -> Unit,
    showYoloUpload: Boolean,
    onSelectYoloModel: (YoloModel) -> Unit,
    onDebugCapture: () -> Unit,
) {
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.capture_interface), fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.captureStatusLabel(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showYoloUpload) {
                    OutlinedButton(onClick = onUploadYoloImage) {
                        Text(stringResource(R.string.capture_upload))
                    }
                }
                if (state.isRunning) {
                    OutlinedButton(onClick = onStopPerception) {
                        Text(stringResource(R.string.capture_stop))
                    }
                } else {
                    Button(
                        onClick = { onStartPerception(CaptureStartSource.Manual) },
                    ) {
                        Text(stringResource(R.string.capture_start))
                    }
                }
            }
        }

        // Debug control: force an immediate capture. The model is fixed to yolo26n-1920.
        if (showYoloUpload) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDebugCapture,
                enabled = state.isRunning,
            ) {
                Text(stringResource(R.string.capture_capture_now))
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
    spaceName: String? = null,
    onModeSelected: (UserRole) -> Unit = {},
    onRenameSpace: (String) -> Unit = {},
    onSetCapacity: (Int) -> Unit = {},
) {
    val displayName = userDisplayName?.takeIf { it.isNotBlank() } ?: userEmail
    val avatarLetter = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "R"
    var showSetupGuide by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    if (showSetupGuide) {
        SetupGuideDialog(onDismiss = { name, capacity ->
            showSetupGuide = false
            if (name != null) onRenameSpace(name)
            if (capacity != null) onSetCapacity(capacity)
        })
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Space") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameInput,
                    onValueChange = { if (it.length <= 40) renameInput = it },
                    label = { Text("Space name") },
                    placeholder = { Text("e.g. DK1, MM8, Lab 3") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenameSpace(renameInput.trim())
                            showRenameDialog = false
                        }
                    },
                    enabled = renameInput.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
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
        Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, fontSize = 24.sp)

        // Setup Guide section — Sensor only
        if (currentUserRole != UserRole.Admin) {
            Text(stringResource(R.string.settings_device_setup), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                        Text(stringResource(R.string.settings_setup_guide), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            stringResource(R.string.settings_setup_guide_desc),
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
        }

        // Your Space section — Sensor only
        if (currentUserRole == UserRole.Sensor) {
            Text(stringResource(R.string.settings_your_space), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            InfoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(spaceName ?: "Not set", fontWeight = FontWeight.SemiBold)
                        Text(
                            "The space this sensor monitors",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        renameInput = spaceName ?: ""
                        showRenameDialog = true
                    }) { Text("Rename") }
                }
            }
        }

        // Profile section
        Text(stringResource(R.string.settings_profile), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                        Text(
                            text = stringResource(R.string.auth_building),
                            fontSize = 11.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            stringResource(R.string.settings_phone_mode),
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
                    Text(stringResource(R.string.btn_sign_out))
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
                        Text(stringResource(R.string.btn_delete_account))
                    }
                }
                if (deleteAccountError != null) {
                    Text(
                        text = deleteAccountError,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                    TextButton(onClick = onClearDeleteError) { Text(stringResource(R.string.btn_dismiss), fontSize = 12.sp) }
                }
            }
        }

        // Language preference — applies to the whole app + Gemini suggestions (all roles)
        Text(stringResource(R.string.settings_language), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        InfoCard {
            val isMalay = com.rewatt.app.core.LocaleManager.currentTag() == com.rewatt.app.core.LocaleManager.MALAY
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isMalay) {
                    OutlinedButton(
                        onClick = { com.rewatt.app.core.LocaleManager.setLanguage(com.rewatt.app.core.LocaleManager.ENGLISH) },
                        modifier = Modifier.weight(1f),
                    ) { Text("English") }
                    Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Bahasa Melayu") }
                } else {
                    Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("English") }
                    OutlinedButton(
                        onClick = { com.rewatt.app.core.LocaleManager.setLanguage(com.rewatt.app.core.LocaleManager.MALAY) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Bahasa Melayu") }
                }
            }
        }

        // Connection and System Status sections — Sensor only
        if (currentUserRole != UserRole.Admin) {
            Text(stringResource(R.string.settings_connection), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            ConnectionCard(
                state = uiState.sensorState,
                freshness = uiState.packetFreshness,
                onScanUsb = onScanUsb,
            )

            Text(stringResource(R.string.settings_system_status), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            StatusSummaryCard(
                sensorState = uiState.sensorState,
                freshness = uiState.packetFreshness,
                nowMillis = uiState.nowMillis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Class Schedule Card (learned session timetable) ──────────────────────────

private val CLASS_GRID_START_HOUR = 8   // 08:00
private val CLASS_GRID_END_HOUR   = 19  // 19:00  → 11 hours (08:00–19:00), fits on screen
private val CLASS_DAY_LABEL_WIDTH_DP = 36  // width of the pinned MON/TUE… column
private val CLASS_ROW_HEIGHT_DP   = 92  // taller rows so all 3 stacked labels fit without clipping

/** Sat (5) & Sun (6) rows are rendered at half height since weekends rarely have classes. */
private fun classRowHeight(dow: Int): Int = if (dow >= 5) CLASS_ROW_HEIGHT_DP / 2 else CLASS_ROW_HEIGHT_DP

/** Snap a minute-of-day to the nearest :10 or :50 mark (class start times must land on those). */
private fun snapTo1050(minute: Int): Int {
    val base = (minute / 60) * 60
    val marks = intArrayOf(base - 10, base + 10, base + 50, base + 70)  // (H-1):50, H:10, H:50, (H+1):10
    return marks.minByOrNull { kotlin.math.abs(it - minute) } ?: minute
}

/** Snap a minute-of-day to the nearest whole hour (:00). Used for the first session of each day. */
private fun snapToHour(minute: Int): Int = ((minute + 30) / 60) * 60

@Composable
private fun ClassScheduleCard(
    hall: Hall,
    learnedDays: List<List<LearnedSegment>>?,
    minuteSchedule: Array<IntArray>? = null,
    showFallbackEstimate: Boolean = false,
    titleOverride: String? = null,
    holidayDays: Set<Int> = emptySet(),
) {
    val dayLabels = stringArrayResource(R.array.sched_day_labels)
    val gridStartMin = CLASS_GRID_START_HOUR * 60
    val gridEndMin   = CLASS_GRID_END_HOUR   * 60
    val totalHours   = CLASS_GRID_END_HOUR - CLASS_GRID_START_HOUR
    val startBin = CLASS_GRID_START_HOUR * 6   // 10-min bins (6 per hour)
    val endBin   = CLASS_GRID_END_HOUR   * 6

    // Derive class sessions from the 10-minute occupancy bins so start/end land on the real
    // session edges (not snapped to whole hours). A "session" is a contiguous run of occupied
    // 10-min bins (mean > 0); empty bins (breaks / overnight / weekend) split them.
    val hasHeatmapData = minuteSchedule?.any { row -> row.any { v -> v > 0 } } == true

    // No data yet AND not the demo account → show an empty "collecting data" state, NOT the
    // hardcoded FSKTM estimate. The estimate is only a demo fallback for the FSKTM account.
    if (!hasHeatmapData && !showFallbackEstimate) {
        InfoCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sched_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.sched_no_data), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sched_no_data_desc),
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        return
    }

    val isEstimated = !hasHeatmapData
    val displayDays: List<List<LearnedSegment>> = if (hasHeatmapData) {
        List(7) { dow ->
            val bins = minuteSchedule!![dow]
            val sessions = mutableListOf<LearnedSegment>()
            var runStartBin = -1
            var runSum = 0
            var runCount = 0
            for (b in startBin until endBin) {
                val mean = bins.getOrElse(b) { 0 }
                if (mean > 0) {
                    if (runStartBin < 0) { runStartBin = b; runSum = 0; runCount = 0 }
                    runSum += mean; runCount++
                } else if (runStartBin >= 0) {
                    sessions.add(meanSegment(runStartBin * 10, b * 10, if (runCount > 0) runSum / runCount else 0))
                    runStartBin = -1
                }
            }
            if (runStartBin >= 0) {
                sessions.add(meanSegment(runStartBin * 10, endBin * 10, if (runCount > 0) runSum / runCount else 0))
            }
            sessions
        }
    } else {
        // Cold-start (no sensor data yet): hardcoded hallDayBreaks estimate, derived from the single
        // canonical session source so the Weekly/Daily occupancy and Action timeline (which read the
        // same classScheduleSessions) line up exactly with this card.
        List(7) { dow ->
            classScheduleSessions(hall, dow).map { r ->
                LearnedSegment(
                    startMinuteOfDay = r.first,
                    endMinuteOfDay = r.last,
                    predictedOccupancy = 60,
                    occupancyLevel = "medium",
                    weeksSeen = 0,
                    lastSeenMillis = 0L,
                )
            }
        }
    }

    // Determine if today is a Malaysian public holiday
    val todayCal = Calendar.getInstance()
    val todayDow = (todayCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val todayIsHoliday = MalaysiaHolidays.isHoliday(
        todayCal.get(Calendar.YEAR),
        todayCal.get(Calendar.MONTH) + 1,
        todayCal.get(Calendar.DAY_OF_MONTH),
    )

    InfoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titleOverride ?: stringResource(R.string.sched_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (isEstimated) stringResource(R.string.sched_estimated) else stringResource(R.string.sched_from_data),
                fontSize = 11.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // Pinned day-label column
            Column(modifier = Modifier.width(CLASS_DAY_LABEL_WIDTH_DP.dp)) {
                // Blank cell above time header
                Box(modifier = Modifier.height(20.dp))
                dayLabels.forEachIndexed { idx, label ->
                    val isToday = idx == todayDow
                    val isHoliday = idx in holidayDays || (isToday && todayIsHoliday)
                    Box(
                        modifier = Modifier
                            .height(classRowHeight(idx).dp)
                            .fillMaxWidth()
                            .background(
                                if (isHoliday) Color(0xFFFFF3E0)
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label,
                                fontSize = 10.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                color = if (isHoliday) Color(0xFFBF360C)
                                        else if (isToday) androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isHoliday) {
                                Text(
                                    stringResource(R.string.sched_ph),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBF360C),
                                )
                            } else if (isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(
                                            androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Time grid sized to fit the whole 12-hour span on screen (no horizontal scroll).
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val hourWidth = maxWidth / totalHours   // Dp per hour column, fills available width
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Time header row — 24-hour labels (08, 09, … 18)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (h in CLASS_GRID_START_HOUR until CLASS_GRID_END_HOUR) {
                            Box(modifier = Modifier.width(hourWidth).height(20.dp),
                                contentAlignment = Alignment.Center) {
                                Text(
                                    "%02d".format(h),
                                    fontSize = 9.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Day rows
                    displayDays.forEachIndexed { dow, segments ->
                        val isHolidayRow = dow in holidayDays || ((dow == todayDow) && todayIsHoliday)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(classRowHeight(dow).dp)
                                .background(
                                    if (isHolidayRow) Color(0xFFFFF3E0)
                                    else if (dow % 2 == 0)
                                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    else Color.Transparent
                                ),
                        ) {
                            // Hour grid lines
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val lineColor = Color(0x22000000)
                                for (h in 0..totalHours) {
                                    val x = h * size.width / totalHours
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                                        strokeWidth = 0.5.dp.toPx(),
                                    )
                                }
                            }
                            if (isHolidayRow) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.sched_public_holiday),
                                        fontSize = 10.sp,
                                        color = Color(0xFFBF360C),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    )
                                }
                            }
                            // Session blocks (hidden on holiday rows)
                            if (!isHolidayRow) segments.forEachIndexed { segIdx, seg ->
                                // First session of the day starts on a whole hour (:00); the rest
                                // land on :10 or :50. This guarantees ≥4 XX:00 boxes per space.
                                val rawStart = seg.startMinuteOfDay
                                val startMin = (if (segIdx == 0) snapToHour(rawStart) else snapTo1050(rawStart))
                                    .coerceIn(gridStartMin, gridEndMin)
                                val endMin   = seg.endMinuteOfDay.coerceIn(gridStartMin, gridEndMin)
                                if (endMin <= startMin) return@forEachIndexed
                                val leftDp  = hourWidth * ((startMin - gridStartMin) / 60f)
                                val widthDp = hourWidth * ((endMin - startMin) / 60f)

                                // Same blue gradient as the heatmap / daily pattern, keyed on the
                                // session's average expected occupants.
                                val expected = seg.predictedOccupancy
                                val blockColor = if (isEstimated) Color(0xFFFFE082) else heatColorForCount(expected)
                                val textColor = if (expected >= 150) Color.White else Color(0xFF0D2B5E)
                                val occupancyLabel = if (isEstimated) "~$expected" else stringResource(R.string.occ_ppl, expected)
                                val startLabel = minuteLabel(startMin)
                                val endLabel   = minuteLabel(endMin)

                                Box(
                                    modifier = Modifier
                                        .padding(start = leftDp, top = 3.dp, bottom = 3.dp, end = 1.dp)
                                        .width((widthDp - 1.dp).coerceAtLeast(4.dp))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(blockColor),
                                ) {
                                    // Stacked layout: start / occupants / end, centered as a tight group
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(startLabel,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            lineHeight = 13.sp,
                                            color = textColor, maxLines = 1, softWrap = false,
                                            overflow = TextOverflow.Visible)
                                        Text(occupancyLabel,
                                            fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                            lineHeight = 11.sp,
                                            color = textColor, maxLines = 1, softWrap = false,
                                            overflow = TextOverflow.Visible)
                                        Text(endLabel,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            lineHeight = 13.sp,
                                            color = textColor, maxLines = 1, softWrap = false,
                                            overflow = TextOverflow.Visible)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isEstimated) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.sched_estimated_note),
                fontSize = 10.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// 24-hour military format, e.g. 480 -> "0800", 570 -> "0930"
private fun minuteLabel(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    return "%02d%02d".format(h, m)
}

/** Build a session segment from a minute range [startMin, endMin) and its mean occupancy. */
private fun meanSegment(startMin: Int, endMin: Int, meanCount: Int): LearnedSegment {
    val lvl = when {
        meanCount >= 81 -> "high"
        meanCount >= 41 -> "medium"
        else            -> "low"
    }
    return LearnedSegment(
        startMinuteOfDay = startMin,
        endMinuteOfDay = endMin,
        predictedOccupancy = meanCount,
        occupancyLevel = lvl,
        weeksSeen = 0,
        lastSeenMillis = 0L,
    )
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
                Text(stringResource(R.string.sensor_scan))
            }
        }
    }
}

@Composable
private fun TelemetryMetricGrid(telemetry: RadarTelemetry?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.sensor_presence),
                value = when (telemetry?.presence) {
                    true -> stringResource(R.string.occ_detected)
                    false -> stringResource(R.string.sensor_clear)
                    null -> "--"
                },
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.sensor_distance),
                value = telemetry?.distanceCm?.let { "$it cm" } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.sensor_temp),
                value = telemetry?.temperatureC?.let { "%.1f C".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.m_humidity),
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
        EnergyBar(label = stringResource(R.string.sensor_moving), value = telemetry?.movingEnergy)
        Spacer(Modifier.height(10.dp))
        EnergyBar(label = stringResource(R.string.sensor_stationary), value = telemetry?.stationaryEnergy)
        Spacer(Modifier.height(10.dp))
        Text(
            text = telemetry?.rawPacket ?: stringResource(R.string.sensor_waiting_packet),
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
                Text(stringResource(R.string.db_packet_health), fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(stringResource(R.string.db_packet_health), fontWeight = FontWeight.SemiBold)
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
        Text(stringResource(R.string.db_firestore_sync), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
        Text(stringResource(R.string.db_gemini_activity), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = state.yoloMessage,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = state.exposureMessage,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FramePreview(
    frame: Bitmap?,
    detections: List<ObjectDetection>,
    modifier: Modifier = Modifier,
) {
    // Always show the capture in a 4:3 box to match the 4:3 camera frames.
    Box(
        modifier = modifier
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (frame == null) {
            Text(
                text = stringResource(R.string.capture_no_frame),
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
        // Only draw person boxes, regardless of model (other categories are not shown).
        detections.filter { it.isPerson }.forEach { detection ->
            val box = detection.box
            drawRect(
                color = Color(0xFF69D7BF),
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
private fun SensorLiveDataCard(telemetry: RadarTelemetry?) {
    val decision = remember(telemetry) { telemetry?.toOccupancyDecision() }

    val stateLabel = when (telemetry?.radarState) {
        0    -> stringResource(R.string.radar_no_target)
        1    -> stringResource(R.string.radar_moving)
        2    -> stringResource(R.string.radar_stationary)
        3    -> stringResource(R.string.radar_both)
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
            Text(stringResource(R.string.capture_live_sensor), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))

        // ── Occupancy verdict banner ──────────────────────────────────────────
        OccupancyVerdictBanner(decision = decision)
        Spacer(Modifier.height(16.dp))

        // ── 1 & 2: Environmental (SHT30) ─────────────────────────────────────
        Text(
            stringResource(R.string.sensor_environmental),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.m_temperature),
                value = telemetry?.temperatureC?.let { "%.1f °C".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.m_humidity),
                value = telemetry?.humidityPercent?.let { "%.1f %%".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))

        // ── 3-7: Radar (LD2412) ───────────────────────────────────────────────
        Text(
            stringResource(R.string.sensor_radar),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        // 3 — Radar State
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = stringResource(R.string.sensor_radar_state),
                value = stateLabel,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.sensor_distance),
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
            Text(stringResource(R.string.sensor_moving_target), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                text = telemetry?.movingDistanceCm?.let { "$it cm" } ?: "--",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        EnergyBar(
            label = stringResource(R.string.sensor_moving_energy, OccupancyThreshold.MOVING_ENERGY_MIN),
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
            Text(stringResource(R.string.sensor_stationary_target), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                text = telemetry?.stationaryDistanceCm?.let { "$it cm" } ?: "--",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        EnergyBar(
            label = stringResource(R.string.sensor_stationary_energy, OccupancyThreshold.STATIONARY_ENERGY_MIN),
            value = telemetry?.stationaryEnergy,
            threshold = OccupancyThreshold.STATIONARY_ENERGY_MIN,
        )

        if (telemetry == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sensor_connect_otg),
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
                        true  -> stringResource(R.string.verdict_occupied)
                        false -> stringResource(R.string.verdict_empty)
                        null  -> stringResource(R.string.verdict_waiting)
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
    valueColor: Color = Color.Unspecified,
    leadingEmoji: String? = null,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (leadingEmoji != null) Text(leadingEmoji, fontSize = 13.sp)
                Text(
                    text = title,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Gemini brand gradient (blue → purple → coral) used to mark Gemini-powered surfaces. */
private val GeminiGradient = listOf(
    Color(0xFF4285F4),
    Color(0xFF9B72CB),
    Color(0xFFD96570),
)

/**
 * Wraps Gemini-powered cards in one bigger rounded box with a colourful gradient edge + a gradient
 * "Gemini insights" header, so the AC suggestion and yesterday's waste read as one AI section.
 */
@Composable
private fun GeminiFrame(content: @Composable ColumnScope.() -> Unit) {
    val edge = Brush.linearGradient(GeminiGradient)
    val tint = Brush.linearGradient(GeminiGradient.map { it.copy(alpha = 0.07f) })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(tint)
            .border(1.5.dp, edge, RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "✦  Gemini insights",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                style = androidx.compose.ui.text.TextStyle(brush = edge),
            )
            content()
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

private fun PerceptionUiState.lightsStateLabel(): String =
    when (lightsOn) {
        true -> "Lights On"
        false -> "Lights Off"
        null -> "--"
    }

private fun PerceptionUiState.exposureValueLabel(): String =
    exposureEv?.let { "EV %.1f".format(it) } ?: "--"

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

/** Comfort threshold below which the hall feels too cold — the AC is over-cooling and wasting energy. */
private const val THI_TOO_COLD = 21.0

/** Temperature-Humidity Index: THI = 0.8*T + (RH*T)/500, with T in °C and RH in %. */
private fun computeThi(temperatureC: Double, humidityPercent: Double): Double =
    0.8 * temperatureC + (humidityPercent * temperatureC) / 500.0

/**
 * Solve the THI formula for the temperature that lands exactly on [THI_TOO_COLD] at the current
 * humidity, then round UP to the next whole °C so the result stays at or above the comfort
 * threshold. This is the AC setpoint we suggest raising to when the hall is too cold.
 */
private fun suggestedComfortTemp(humidityPercent: Double): Int =
    ceil(THI_TOO_COLD / (0.8 + humidityPercent / 500.0)).toInt()

/** AC/Lights status chip state. Kept as a stable key so it doesn't depend on translated text. */
internal enum class AcState { OFF, COOL_PLUS, COOL_MINUS, ON }

/**
 * A NOW-block recommendation as language-independent data: the AC chip state plus string-resource
 * IDs (and format args) for the action label and reason. The composable resolves the IDs to the
 * user's selected language; logic (e.g. the AC chip) keys off [acState], never translated text.
 */
internal data class NowAction(
    val acState: AcState,
    val labelRes: Int,
    val reasonRes: Int,
    val labelArgs: List<Any> = emptyList(),
    val reasonArgs: List<Any> = emptyList(),
)

@Composable
private fun NowAction.label(): String = stringResource(labelRes, *labelArgs.toTypedArray())

@Composable
private fun NowAction.reason(): String = stringResource(reasonRes, *reasonArgs.toTypedArray())

/**
 * If the hall is occupied and the THI is below [THI_TOO_COLD], returns the "raise the AC setpoint"
 * action (prioritized over generic comfort actions). Requires both temperature and humidity from
 * the sensor; returns null when either is missing or the room is comfortable.
 */
private fun tooColdAction(temperatureC: Float?, humidityPercent: Double?): NowAction? {
    if (temperatureC == null || humidityPercent == null) return null
    val thi = computeThi(temperatureC.toDouble(), humidityPercent)
    if (thi >= THI_TOO_COLD) return null
    return NowAction(
        acState = AcState.COOL_MINUS,
        labelRes = R.string.now_label_set_ac,
        labelArgs = listOf(suggestedComfortTemp(humidityPercent)),
        reasonRes = R.string.now_reason_too_cold,
        reasonArgs = listOf(thi),
    )
}

private fun deriveNowAction(
    nowMillis: Long,
    personCount: Int,
    temperatureC: Float?,
    occupancyHint: String?,
    hall: Hall = Hall.Hall1,
    schedule: Array<IntArray>? = null,
    humidityPercent: Double? = null,
): NowAction {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)

    fun keepAcOnUnexpected() = NowAction(
        acState = AcState.ON,
        labelRes = R.string.now_label_keep_ac_on,
        reasonRes = R.string.now_reason_unexpected_occupancy,
        reasonArgs = listOf(personCount),
    )
    fun increaseCooling(temp: Float) = NowAction(
        acState = AcState.COOL_PLUS,
        labelRes = R.string.now_label_increase_cooling,
        reasonRes = R.string.now_reason_temperature,
        reasonArgs = listOf(temp),
    )

    // Highest priority: a too-cold hall that is occupied RIGHT NOW wastes energy. Occupancy here is
    // the live YOLO person count (the detection driven by the mmWave-triggered capture), never the
    // predicted schedule — so this fires identically for accounts with and without history, the
    // moment YOLO reports someone present. Temperature, humidity and occupancy are all current data.
    if (personCount > 0) tooColdAction(temperatureC, humidityPercent)?.let { return it }

    // Data-driven: derive the action from the account's real occupancy grid when available.
    val hasData = schedule?.any { row -> row.any { v -> v > 0 } } == true
    if (hasData) {
        val row = schedule!!.getOrNull(dow)
        val expected = row?.getOrElse(h) { 0 } ?: 0
        val occupiedNow = expected > 0
        if (!occupiedNow && personCount > 2) return keepAcOnUnexpected()
        if (!occupiedNow) {
            val resumeH = ((h + 1)..23).firstOrNull { (row?.getOrElse(it) { 0 } ?: 0) > 0 }
            return if (resumeH != null)
                NowAction(AcState.OFF, R.string.now_label_turn_off_ac_lights, R.string.now_reason_no_class_until, reasonArgs = listOf(resumeH))
            else
                NowAction(AcState.OFF, R.string.now_label_turn_off_all, R.string.now_reason_no_more_classes)
        }
        if (temperatureC != null && temperatureC > 27f) return increaseCooling(temperatureC)
        return NowAction(AcState.ON, R.string.now_label_maintain, R.string.now_reason_class_in_session, reasonArgs = listOf(expected))
    }

    // Fallback: hardcoded timetable (used only before any occupancy data exists)
    val dayBreaks = hallDayBreaks[hall] ?: emptyMap()
    if (dow >= 5) return NowAction(AcState.OFF, R.string.now_label_turn_off_all, R.string.now_reason_weekend)
    if (inRange(h, m, timetableOvernight)) return NowAction(AcState.OFF, R.string.now_label_turn_off_all, R.string.now_reason_overnight)

    for (br in dayBreaks[dow] ?: emptyList()) {
        if (inRange(h, m, br))
            return NowAction(AcState.OFF, R.string.now_label_turn_off_ac_lights, R.string.now_reason_break_until, reasonArgs = listOf(br.eh, br.em))
    }

    if ((occupancyHint == "likely_empty" || occupancyHint == "unclear") && personCount > 2) return keepAcOnUnexpected()
    if (temperatureC != null && temperatureC > 27f && personCount > 0) return increaseCooling(temperatureC)

    return if (personCount > 0)
        NowAction(AcState.ON, R.string.now_label_maintain, R.string.now_reason_normal_session)
    else
        NowAction(AcState.ON, R.string.now_label_no_action, R.string.now_reason_room_empty)
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

@Composable
private fun acLightStateLabel(state: AcState): String = stringResource(
    when (state) {
        AcState.OFF -> R.string.ac_state_off
        AcState.COOL_PLUS -> R.string.ac_state_cool_plus
        AcState.COOL_MINUS -> R.string.ac_state_cool_minus
        AcState.ON -> R.string.ac_state_on
    }
)

/** Localized label for a forecast occupancy level; falls back to the raw value for unknown levels. */
@Composable
private fun occupancyLevelLabel(level: String): String = when (level) {
    "occupied" -> stringResource(R.string.occlvl_occupied)
    "likely_occupied" -> stringResource(R.string.occlvl_likely_occupied)
    "unclear" -> stringResource(R.string.occlvl_unclear)
    "likely_empty" -> stringResource(R.string.occlvl_likely_empty)
    else -> level
}

private fun deriveSessionEndTime(nowMillis: Long, hall: Hall = Hall.Hall1, schedule: Array<IntArray>? = null): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)

    // Data-driven: next hour the room becomes empty, from the real occupancy grid.
    val hasData = schedule?.any { row -> row.any { v -> v > 0 } } == true
    if (hasData) {
        val row = schedule!!.getOrNull(dow)
        val occupiedNow = (row?.getOrElse(h) { 0 } ?: 0) > 0
        if (!occupiedNow) return "No class"
        val endH = ((h + 1)..23).firstOrNull { (row?.getOrElse(it) { 0 } ?: 0) == 0 } ?: 24
        return "%02d:00".format(endH.coerceAtMost(23))
    }

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

private fun buildDayNotifications(dayOffset: Int, nowMillis: Long, hall: Hall = Hall.Hall1, schedule: Array<IntArray>? = null): List<TimetableNotification> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, dayOffset)
    }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayName = dayNames[dow]
    val result = mutableListOf<TimetableNotification>()

    fun add(h: Int, m: Int, id: String, title: String, reason: String) {
        result.add(TimetableNotification(
            id = "$dayName-$id",
            timeLabel = "%02d:%02d".format(h, m),
            title = title,
            reason = reason,
        ))
    }

    // Data-driven: build the day's schedule from the real occupancy grid when available.
    val hasData = schedule?.any { row -> row.any { v -> v > 0 } } == true
    if (hasData) {
        val row = schedule!!.getOrNull(dow)
        val occupied = (0..23).map { (row?.getOrElse(it) { 0 } ?: 0) > 0 }
        val firstOcc = occupied.indexOfFirst { it }
        val lastOcc = occupied.indexOfLast { it }
        if (firstOcc < 0) return listOf(
            TimetableNotification("$dayName-noclass", "--", "No classes today", "No occupancy recorded ($dayName)")
        )
        add(firstOcc, 0, "startup", "System startup check", "First class at %02d:00 ($dayName)".format(firstOcc))
        for (hh in (firstOcc + 1)..lastOcc) {
            if (!occupied[hh] && occupied[hh - 1]) {
                add(hh, 0, "break$hh-off", "Turn off AC & lights",
                    "Break starts at %02d:00 ($dayName)".format(hh))
            }
            if (occupied[hh] && !occupied[hh - 1]) {
                val preMin = hh * 60 - 30
                add(preMin / 60, preMin % 60, "break$hh-precool", "Pre-cool room to 23°C",
                    "Class resumes at %02d:00 ($dayName)".format(hh))
            }
        }
        val shutdownH = (lastOcc + 1).coerceAtMost(23)
        add(shutdownH, 0, "shutdown", "Turn off all systems", "No more classes today ($dayName)")
        return result.sortedBy { it.timeLabel }
    }

    // Fallback: hardcoded timetable
    if (dow >= 5) return listOf(
        TimetableNotification("weekend-off", "--", "No classes today", "Weekend — all systems off"),
    )
    val dayBreaks = (hallDayBreaks[hall] ?: emptyMap())[dow] ?: emptyList()
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

private fun buildAllHallsDayNotifications(
    dayOffset: Int,
    nowMillis: Long,
    hallSchedules: Map<String, Array<IntArray>> = emptyMap(),
): List<TimetableNotification> =
    Hall.values().flatMap { hall ->
        buildDayNotifications(dayOffset, nowMillis, hall, hallSchedules[hall.label]).map { n ->
            n.copy(
                id = "${hall.name}-${n.id}",
                reason = "${hall.label} — ${n.reason}",
            )
        }
    }.sortedBy { it.timeLabel }

// ── Canonical class schedule (single source for Class Schedule + Weekly/Daily occupancy + Action) ──
//
// For the demo (fsktm) account there is no live sensor schedule, so all four views derive from these
// minute-accurate sessions built from the hardcoded hallDayBreaks — the exact same logic the Class
// Schedule card uses — guaranteeing identical class/break times everywhere. History weeks are separate.

/** Merged class sessions (minute-of-day ranges) for [hall] on weekday [dow]; empty on weekends. */
private fun classScheduleSessions(hall: Hall, dow: Int): List<IntRange> {
    if (dow >= 5) return emptyList()
    val gridStartMin = CLASS_GRID_START_HOUR * 60
    val gridEndMin = CLASS_GRID_END_HOUR * 60
    val dayBreaks = (hallDayBreaks[hall] ?: emptyMap())[dow] ?: emptyList()
    val activeRanges = mutableListOf<IntRange>()
    var rangeStart: Int? = null
    for (minuteOfDay in gridStartMin until gridEndMin) {
        val h = minuteOfDay / 60; val m = minuteOfDay % 60
        val inBreak = inRange(h, m, timetableOvernight) || dayBreaks.any { inRange(h, m, it) }
        if (!inBreak) {
            if (rangeStart == null) rangeStart = minuteOfDay
        } else {
            rangeStart?.let { start -> activeRanges.add(start until minuteOfDay); rangeStart = null }
        }
    }
    rangeStart?.let { start -> activeRanges.add(start until gridEndMin) }
    val merged = mutableListOf<IntRange>()
    for (r in activeRanges) {
        if (merged.isNotEmpty() && r.first - merged.last().last <= 30)
            merged[merged.lastIndex] = merged.last().first..r.last
        else merged.add(r)
    }
    return merged
}

/**
 * Representative occupancy count for a session hour. Varies by time of day (morning ramp / midday
 * peak / evening wind-down) with a deterministic per-hall/day jitter, mirroring [buildHourlyOccupancy]
 * so the heatmap keeps a natural gradient while the occupied/empty TIMES stay class-schedule exact.
 */
private fun occupancyForHour(hall: Hall, dayIndex: Int, hour: Int): Int {
    val v = ((hour + 1) * (hall.ordinal + 2) * (dayIndex + 3)) % 19   // 0-18
    return when {
        hour in 8..9 -> 44 + v   // 44-62  (moderate)
        hour >= 17   -> 12 + v   // 12-30  (wind-down)
        else         -> 82 + v   // 82-100 (peak)
    }
}

/**
 * Canonical 10-min-bin occupancy grid (7×144) for the demo account: real [minuteSchedule] when it
 * has data, otherwise filled from [classScheduleSessions] with the [occupancyForHour] gradient (zero
 * exactly during breaks/overnight/weekend). Fed to the Weekly heatmap + Daily chart.
 */
private fun canonicalMinuteGrid(hall: Hall, minuteSchedule: Array<IntArray>?): Array<IntArray> {
    if (minuteSchedule?.any { row -> row.any { it > 0 } } == true) return minuteSchedule
    return Array(7) { d ->
        val grid = IntArray(144) { 0 }
        for (s in classScheduleSessions(hall, d)) {
            val startBin = s.first / 10
            val endBin = (s.last + 1) / 10
            for (b in startBin until endBin) grid[b] = occupancyForHour(hall, d, (b * 10) / 60)
        }
        grid
    }
}

/**
 * Class sessions (minute-of-day ranges, end-exclusive = `.last + 1`) for [hall] on [dow], from the
 * SAME source the Class Schedule card uses: the real 10-min [minuteSchedule] when it has data
 * (contiguous mean>0 runs over the 08:00–19:00 grid), otherwise the hardcoded [classScheduleSessions]
 * fallback. This is the single source the Class Schedule, Weekly/Daily occupancy and Action timeline
 * all share, so per-space times line up exactly.
 */
private fun sessionsForDay(hall: Hall, dow: Int, minuteSchedule: Array<IntArray>?): List<IntRange> {
    val hasData = minuteSchedule?.any { row -> row.any { v -> v > 0 } } == true
    if (hasData) {
        val bins = minuteSchedule!!.getOrNull(dow) ?: return emptyList()
        val startBin = CLASS_GRID_START_HOUR * 6
        val endBin = CLASS_GRID_END_HOUR * 6
        val out = mutableListOf<IntRange>()
        var runStart = -1
        for (b in startBin until endBin) {
            val mean = bins.getOrElse(b) { 0 }
            if (mean > 0) {
                if (runStart < 0) runStart = b
            } else if (runStart >= 0) {
                out.add((runStart * 10) until (b * 10)); runStart = -1
            }
        }
        if (runStart >= 0) out.add((runStart * 10) until (endBin * 10))
        return out
    }
    return classScheduleSessions(hall, dow)
}

/** Occupancy count at [minOfDay]: the real 10-min bin mean when available, else the hourly gradient. */
private fun occupancyAt(hall: Hall, dow: Int, minOfDay: Int, minuteSchedule: Array<IntArray>?): Int {
    val hasData = minuteSchedule?.any { row -> row.any { v -> v > 0 } } == true
    return if (hasData) {
        minuteSchedule!!.getOrNull(dow)?.getOrElse(minOfDay / 10) { 0 } ?: 0
    } else {
        occupancyForHour(hall, dow, minOfDay / 60)
    }
}

/** NOW recommendation from the shared class sessions (demo account), matching the Class Schedule. */
internal fun deriveNowActionFromSessions(
    nowMillis: Long,
    personCount: Int,
    temperatureC: Float?,
    hall: Hall,
    humidityPercent: Double?,
    minuteSchedule: Array<IntArray>?,
): NowAction {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    // Live YOLO occupancy still wins (same as the sensor-data path).
    if (personCount > 0) tooColdAction(temperatureC, humidityPercent)?.let { return it }

    val sessions = sessionsForDay(hall, dow, minuteSchedule)
    val occupiedNow = sessions.any { minOfDay in it.first..it.last }
    if (!occupiedNow) {
        if (personCount > 2) return NowAction(
            AcState.ON, R.string.now_label_keep_ac_on, R.string.now_reason_unexpected_occupancy,
            reasonArgs = listOf(personCount),
        )
        val nextStart = sessions.map { it.first }.filter { it > minOfDay }.minOrNull()
        return if (nextStart != null)
            NowAction(AcState.OFF, R.string.now_label_turn_off_ac_lights, R.string.now_reason_no_class_until, reasonArgs = listOf(nextStart / 60))
        else
            NowAction(AcState.OFF, R.string.now_label_turn_off_all, R.string.now_reason_no_more_classes)
    }
    if (temperatureC != null && temperatureC > 27f) return NowAction(
        AcState.COOL_PLUS, R.string.now_label_increase_cooling, R.string.now_reason_temperature,
        reasonArgs = listOf(temperatureC),
    )
    return NowAction(
        AcState.ON, R.string.now_label_maintain, R.string.now_reason_class_in_session,
        reasonArgs = listOf(occupancyAt(hall, dow, minOfDay, minuteSchedule)),
    )
}

/** Minute-accurate day timeline from the shared class sessions (demo account). */
internal fun buildSessionDayNotifications(dayOffset: Int, nowMillis: Long, hall: Hall, minuteSchedule: Array<IntArray>?): List<TimetableNotification> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, dayOffset)
    }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val dayName = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[dow]
    val sessions = sessionsForDay(hall, dow, minuteSchedule)
    if (sessions.isEmpty()) return listOf(
        TimetableNotification(
            "$dayName-noclass", "--", "No classes today",
            if (dow >= 5) "Weekend — all systems off" else "No classes scheduled ($dayName)",
        ),
    )
    val result = mutableListOf<TimetableNotification>()
    fun add(min: Int, id: String, title: String, reason: String) {
        result.add(TimetableNotification("$dayName-$id", "%02d:%02d".format(min / 60, min % 60), title, reason))
    }
    val firstStart = sessions.first().first
    add(firstStart, "startup", "System startup check", "First class at %02d:%02d ($dayName)".format(firstStart / 60, firstStart % 60))
    for (i in 0 until sessions.size - 1) {
        val breakStart = sessions[i].last + 1   // exclusive end of this session
        val resume = sessions[i + 1].first
        add(breakStart, "break$i-off", "Turn off AC & lights", "Break starts at %02d:%02d ($dayName)".format(breakStart / 60, breakStart % 60))
        val preMin = resume - 30
        if (preMin > breakStart) {
            add(preMin, "break$i-precool", "Pre-cool room to 23°C", "Class resumes at %02d:%02d ($dayName)".format(resume / 60, resume % 60))
        }
    }
    val lastEnd = sessions.last().last + 1
    add(lastEnd, "shutdown", "Turn off all systems", "No more classes today ($dayName)")
    return result.sortedBy { it.timeLabel }
}

/** Demo timeline across all halls, prefixed with each hall label (same shape as the sensor-data path). */
internal fun buildAllHallsSessionNotifications(
    dayOffset: Int,
    nowMillis: Long,
    hallMinuteSchedules: Map<String, Array<IntArray>>,
): List<TimetableNotification> =
    Hall.values().flatMap { hall ->
        buildSessionDayNotifications(dayOffset, nowMillis, hall, hallMinuteSchedules[hall.label]).map { n ->
            n.copy(id = "${hall.name}-${n.id}", reason = "${hall.label} — ${n.reason}")
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
