package com.rewatt.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rewatt.app.telemetry.RadarTelemetry

@Entity(tableName = "telemetry_readings")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val capturedAtMillis: Long,
    val presence: Boolean,
    val movingEnergy: Int,
    val stationaryEnergy: Int,
    val distanceCm: Int,
    val temperatureC: Float?,
    val humidityPercent: Float?,
    val rawPacket: String,
)

fun RadarTelemetry.toEntity(): TelemetryEntity =
    TelemetryEntity(
        capturedAtMillis = capturedAtMillis,
        presence = presence,
        movingEnergy = movingEnergy,
        stationaryEnergy = stationaryEnergy,
        distanceCm = distanceCm,
        temperatureC = temperatureC,
        humidityPercent = humidityPercent,
        rawPacket = rawPacket,
    )
