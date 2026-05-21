package com.rewatt.app.telemetry

data class RadarTelemetry(
    val presence: Boolean,
    val movingEnergy: Int,
    val stationaryEnergy: Int,
    val distanceCm: Int,
    val temperatureC: Float?,
    val humidityPercent: Float?,
    val capturedAtMillis: Long,
    val rawPacket: String,
)
