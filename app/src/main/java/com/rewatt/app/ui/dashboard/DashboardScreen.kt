package com.rewatt.app.ui.dashboard

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewatt.app.R
import com.rewatt.app.data.FirebaseSyncStatus
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.perception.ObjectDetection
import com.rewatt.app.perception.PerceptionUiState
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.RadarTelemetry
import com.rewatt.app.usb.UsbConnectionStatus
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    perceptionState: PerceptionUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (PreviewView) -> Unit,
    onStopPerception: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.rewatt_logo),
                            contentDescription = "REWATT logo",
                            modifier = Modifier.size(36.dp),
                        )
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionCard(
                state = uiState.sensorState,
                freshness = uiState.packetFreshness,
                onScanUsb = onScanUsb,
            )
            TelemetrySection(telemetry = uiState.sensorState.latestTelemetry)
            PacketHealthCard(
                sensorState = uiState.sensorState,
                freshness = uiState.packetFreshness,
                nowMillis = uiState.nowMillis,
            )
            PerceptionSection(
                state = perceptionState,
                nowMillis = uiState.nowMillis,
                onStartPerception = onStartPerception,
                onStopPerception = onStopPerception,
            )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun TelemetrySection(telemetry: RadarTelemetry?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        RadarEnergyCard(telemetry = telemetry)
    }
}

@Composable
private fun RadarEnergyCard(telemetry: RadarTelemetry?) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Usb,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text("LD2410B radar energy", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Moving",
                value = telemetry?.movingEnergy?.toString() ?: "--",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Stationary",
                value = telemetry?.stationaryEnergy?.toString() ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        EnergyBar(label = "Moving", value = telemetry?.movingEnergy)
        Spacer(Modifier.height(10.dp))
        EnergyBar(label = "Stationary", value = telemetry?.stationaryEnergy)
    }
}

@Composable
private fun EnergyBar(label: String, value: Int?) {
    val progress = min((value ?: 0) / 100f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(88.dp), fontSize = 13.sp)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(value?.toString() ?: "--", modifier = Modifier.width(32.dp), fontSize = 13.sp)
    }
}

@Composable
private fun PacketHealthCard(
    sensorState: SensorDashboardState,
    freshness: PacketFreshness,
    nowMillis: Long,
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (freshness == PacketFreshness.Stale || sensorState.lastMalformedPacket != null) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text("Packet health", fontWeight = FontWeight.SemiBold)
                Text(
                    text = freshness.label(sensorState.lastValidPacketAtMillis, nowMillis),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Valid",
                value = sensorState.validPacketCount.toString(),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Malformed",
                value = sensorState.malformedPacketCount.toString(),
                modifier = Modifier.weight(1f),
                warning = sensorState.malformedPacketCount > 0,
            )
        }
        sensorState.lastMalformedPacket?.let { packet ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Last rejected: $packet",
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        FirebaseSyncStatusRow(sensorState = sensorState)
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
        Text("Firestore sync", fontWeight = FontWeight.SemiBold)
        Text(
            text = syncState.message,
            color = color,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PerceptionSection(
    state: PerceptionUiState,
    nowMillis: Long,
    onStartPerception: (PreviewView) -> Unit,
    onStopPerception: () -> Unit,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Perception", fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.statusMessage,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            if (state.isRunning) {
                OutlinedButton(onClick = onStopPerception) {
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = { previewView?.let(onStartPerception) },
                    enabled = previewView != null,
                ) {
                    Text("Start")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Spacer(Modifier.height(12.dp))
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { previewView = it }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(12.dp))
        LatestFrameCard(
            frame = state.latestFrame,
            detections = state.yoloDetections,
            contextDetections = state.contextObjectDetections,
            message = state.yoloMessage,
            personCount = state.personCount,
            latestFrameAtMillis = state.latestFrameAtMillis,
            nowMillis = nowMillis,
        )
        Spacer(Modifier.height(12.dp))
        AudioCaptureCard(state = state, nowMillis = nowMillis)
    }
}

@Composable
private fun LatestFrameCard(
    frame: Bitmap?,
    detections: List<ObjectDetection>,
    contextDetections: List<ObjectDetection>,
    message: String,
    personCount: Int,
    latestFrameAtMillis: Long?,
    nowMillis: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("People detected", fontWeight = FontWeight.SemiBold)
                Text(
                    text = latestFrameAtMillis.ageLabel(nowMillis),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Text(
                text = "$personCount ${if (personCount == 1) "person" else "people"}",
                color = if (personCount > 0) {
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                } else {
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
        FramePreview(frame = frame, detections = detections)
        Text(
            text = message,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        val peopleDetections = detections.filter { it.isPerson }
        if (peopleDetections.isNotEmpty()) {
            peopleDetections.take(5).forEach { detection ->
                DetectionRow(
                    label = detection.label,
                    confidence = detection.confidence,
                    highlight = detection.isPerson,
                )
            }
        }
        if (contextDetections.isNotEmpty()) {
            Text("Context objects", fontWeight = FontWeight.SemiBold)
            contextDetections.take(5).forEach { detection ->
                DetectionRow(
                    label = detection.label,
                    confidence = detection.confidence,
                    highlight = false,
                )
            }
        }
    }
}

@Composable
private fun FramePreview(
    frame: Bitmap?,
    detections: List<ObjectDetection>,
) {
    val aspectRatio = frame?.let { it.width.toFloat() / it.height.toFloat() } ?: (16f / 9f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (frame == null) {
            Text(
                text = "No captured frame yet",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun AudioCaptureCard(
    state: PerceptionUiState,
    nowMillis: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Human activity sounds", fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.latestAudioAtMillis.ageLabel(nowMillis),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Text(
                text = if (state.audioPresenceDetected) {
                    "${(state.audioPresenceScore * 100).toInt()}%"
                } else {
                    "${(state.audioRms * 100).toInt()}% level"
                },
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        WaveformBar(waveform = state.audioWaveform)
        Text(
            text = "${state.audioSampleCount} samples at 16 kHz",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Text(
            text = state.yamnetMessage,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        if (state.soundDetections.isNotEmpty()) {
            state.soundDetections.forEach { detection ->
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

@Composable
private fun WaveformBar(waveform: List<Float>) {
    val barColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
    val emptyColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
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
        )
        Text(
            text = "${(confidence * 100).toInt()}%",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
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
                .padding(14.dp),
        ) {
            Text(
                text = title,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
        PacketFreshness.WaitingForFirstPacket -> "Waiting for the first valid telemetry packet."
        PacketFreshness.Fresh -> "Live telemetry is fresh."
        PacketFreshness.Stale -> {
            val ageSeconds = lastValidPacketAtMillis?.let { (nowMillis - it) / 1_000L } ?: 0L
            "Telemetry is stale. Last valid packet was ${ageSeconds}s ago."
        }
    }

private fun Long?.ageLabel(nowMillis: Long): String =
    this?.let { timestamp ->
        val ageSeconds = ((nowMillis - timestamp) / 1_000L).coerceAtLeast(0L)
        "Captured ${ageSeconds}s ago"
    } ?: "Waiting for capture"
