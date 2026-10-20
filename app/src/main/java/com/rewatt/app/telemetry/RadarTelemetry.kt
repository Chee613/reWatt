package com.rewatt.app.telemetry

data class RadarTelemetry(
    val presence: Boolean,
    val movingEnergy: Int,
    val stationaryEnergy: Int,
    val distanceCm: Int,       // resolved: mov_d for state 1/3, stat_d for state 2
    val temperatureC: Float?,
    val humidityPercent: Float?,
    val capturedAtMillis: Long,
    val rawPacket: String,
    val radarState: Int = 0,           // raw LD2412 state: 0=none 1=moving 2=stationary 3=both
    val movingDistanceCm: Int = 0,     // radar.mov_d
    val stationaryDistanceCm: Int = 0, // radar.stat_d
)
