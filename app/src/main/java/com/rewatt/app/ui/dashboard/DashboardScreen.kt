package com.rewatt.app.ui.dashboard

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import kotlin.math.min

private enum class ReWattTab(val label: String) {
    Live("LIVE"),
    Analytic("Analytic"),
    Capture("Capture"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (PreviewView, CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ReWattTab.Live) }
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
                            Text("Radar + perception hub", fontSize = 12.sp)
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
                        icon = { Text(tab.label.first().toString(), fontWeight = FontWeight.Bold) },
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
                    onScanUsb = onScanUsb,
                    onStartCapture = { requestCapture(CaptureStartSource.Manual) },
                )

                ReWattTab.Analytic -> AnalyticPage(uiState = uiState, perceptionState = perceptionState)

                ReWattTab.Capture -> CapturePage(
                    state = perceptionState,
                    nowMillis = uiState.nowMillis,
                    pendingCaptureSource = pendingCaptureSource,
                    onPendingCaptureConsumed = { pendingCaptureSource = null },
                    onStartPerception = onStartPerception,
                    onStopPerception = ::stopCapture,
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
private fun ReWattLogoBadge(modifier: Modifier = Modifier) {
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
    onScanUsb: () -> Unit,
    onStartCapture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionCard(
            state = uiState.sensorState,
            freshness = uiState.packetFreshness,
            onScanUsb = onScanUsb,
        )
        TelemetryMetricGrid(telemetry = uiState.sensorState.latestTelemetry)
        LivePerceptionSummary(perceptionState = perceptionState)
        Row(
            modifier = Modifier.height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadarEnergyCard(
                telemetry = uiState.sensorState.latestTelemetry,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            StatusSummaryCard(
                sensorState = uiState.sensorState,
                freshness = uiState.packetFreshness,
                nowMillis = uiState.nowMillis,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Button(
            onClick = onStartCapture,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (perceptionState.isRunning) "Open Capture" else "Start Capture")
        }
    }
}

@Composable
private fun AnalyticPage(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
) {
    val telemetry = uiState.sensorState.latestTelemetry
    val analysis = uiState.sensorState.activityAnalysisState.analysis
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("TimeFM Analysis", fontWeight = FontWeight.Bold, fontSize = 24.sp)
        InfoCard(modifier = Modifier.fillMaxWidth()) {
            Text("Analytic tab ready for future TimeFM code.", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Live context is kept here so the TimeFM page has room state before forecasting is added.",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                title = "People",
                value = perceptionState.personCount.toString(),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Activity",
                value = analysis?.activity ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        InfoCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Text("Latest Gemini activity", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = analysis?.reason ?: uiState.sensorState.activityAnalysisState.message,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
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
private fun EnergyBar(label: String, value: Int?) {
    val progress = min((value ?: 0) / 100f, 1f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 12.sp)
            Text(value?.toString() ?: "--", fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
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
