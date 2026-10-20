package com.rewatt.app.data

import com.rewatt.app.telemetry.RadarTelemetry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEntityTest {
    @Test
    fun toEntity_preservesSht30Values() {
        val entity = RadarTelemetry(
            presence = true,
            movingEnergy = 42,
            stationaryEnergy = 85,
            distanceCm = 120,
            temperatureC = 26.5f,
            humidityPercent = 58.2f,
            capturedAtMillis = 1_000L,
            rawPacket = "LD2412:PRES=1,MOV=42,STA=85,DIST=120|SHT30:T=26.5,H=58.2",
        ).toEntity()

        assertEquals(26.5f, entity.temperatureC)
        assertEquals(58.2f, entity.humidityPercent)
    }

    @Test
    fun database_schemaVersionIsThree() {
        val schema = File("schemas/com.rewatt.app.data.ReWattDatabase/3.json").readText()

        assertTrue(schema.contains("\"version\": 3"))
        assertTrue(schema.contains("\"temperatureC\""))
        assertTrue(schema.contains("\"humidityPercent\""))
    }
}
