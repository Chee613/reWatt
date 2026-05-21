package com.rewatt.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.telemetry.PacketFreshness
import com.rewatt.app.telemetry.SensorTelemetry
import com.rewatt.app.usb.UsbConnectionStatus
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onScanUsb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("REWATT", fontWeight = FontWeight.Bold)
                        Text("Sensor hub", fontSize = 12.sp)
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
private fun TelemetrySection(telemetry: SensorTelemetry?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Temperature",
                value = telemetry?.tempC?.let { "%.1f C".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Humidity",
                value = telemetry?.humidityPercent?.let { "%.1f %%".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "AQI",
                value = telemetry?.aqi?.toString() ?: "--",
                modifier = Modifier.weight(1f),
                warning = telemetry?.aqi?.let { it >= 3 } == true,
            )
            MetricCard(
                title = "eCO2",
                value = telemetry?.eco2Ppm?.let { "$it ppm" } ?: "--",
                modifier = Modifier.weight(1f),
                warning = telemetry?.eco2Ppm?.let { it >= 1_000 } == true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "TVOC",
                value = telemetry?.tvocPpb?.let { "$it ppb" } ?: "--",
                modifier = Modifier.weight(1f),
                warning = telemetry?.tvocPpb?.let { it >= 220 } == true,
            )
            MetricCard(
                title = "Presence",
                value = when (telemetry?.presence) {
                    true -> "Detected"
                    false -> "Clear"
                    null -> "--"
                },
                modifier = Modifier.weight(1f),
            )
        }
        RadarEnergyCard(telemetry = telemetry)
    }
}

@Composable
private fun RadarEnergyCard(telemetry: SensorTelemetry?) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Usb,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text("LD2412 radar energy", fontWeight = FontWeight.SemiBold)
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
