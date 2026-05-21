package com.rewatt.app.telemetry

data class SensorTelemetry(
    val tempC: Float,
    val humidityPercent: Float,
    val aqi: Int,
    val tvocPpb: Int,
    val eco2Ppm: Int,
    val movingEnergy: Int,
    val stationaryEnergy: Int,
    val presence: Boolean,
    val capturedAtMillis: Long,
    val rawPacket: String,
)

