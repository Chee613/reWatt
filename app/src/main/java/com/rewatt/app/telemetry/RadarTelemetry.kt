package com.rewatt.app.telemetry

data class RadarTelemetry(
    val presence: Boolean,
    val movingEnergy: Int,
    val stationaryEnergy: Int,
    val distanceCm: Int,
    val capturedAtMillis: Long,
    val rawPacket: String,
)

