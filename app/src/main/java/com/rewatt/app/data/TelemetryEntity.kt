package com.rewatt.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rewatt.app.telemetry.SensorTelemetry

@Entity(tableName = "telemetry_readings")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val capturedAtMillis: Long,
    val tempC: Float,
    val humidityPercent: Float,
    val aqi: Int,
    val tvocPpb: Int,
    val eco2Ppm: Int,
    val movingEnergy: Int,
    val stationaryEnergy: Int,
    val presence: Boolean,
    val rawPacket: String,
)

fun SensorTelemetry.toEntity(): TelemetryEntity =
    TelemetryEntity(
        capturedAtMillis = capturedAtMillis,
        tempC = tempC,
        humidityPercent = humidityPercent,
        aqi = aqi,
        tvocPpb = tvocPpb,
        eco2Ppm = eco2Ppm,
        movingEnergy = movingEnergy,
        stationaryEnergy = stationaryEnergy,
        presence = presence,
        rawPacket = rawPacket,
    )

