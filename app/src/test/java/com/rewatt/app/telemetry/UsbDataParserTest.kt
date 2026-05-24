package com.rewatt.app.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDataParserTest {

    // --- Valid packets ---

    @Test
    fun parseTelemetry_state3_bothTargets() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = """{"temp":29.06,"hum":60.45,"radar":{"state":3,"mov_d":227,"mov_e":44,"stat_d":153,"stat_e":100}}""",
            capturedAtMillis = 1000L,
        )

        requireNotNull(telemetry)
        assertTrue(telemetry.presence)
        assertEquals(44, telemetry.movingEnergy)
        assertEquals(100, telemetry.stationaryEnergy)
        assertEquals(227, telemetry.distanceCm)   // state 3 → mov_d
        assertEquals(3, telemetry.radarState)
        assertEquals(227, telemetry.movingDistanceCm)
        assertEquals(153, telemetry.stationaryDistanceCm)
        assertEquals(29.06f, telemetry.temperatureC)
        assertEquals(60.45f, telemetry.humidityPercent)
        assertEquals(1000L, telemetry.capturedAtMillis)
    }

    @Test
    fun parseTelemetry_state2_stationaryOnly() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = """{"temp":29.06,"hum":60.35,"radar":{"state":2,"mov_d":0,"mov_e":0,"stat_d":153,"stat_e":100}}""",
            capturedAtMillis = 2000L,
        )

        requireNotNull(telemetry)
        assertTrue(telemetry.presence)
        assertEquals(0, telemetry.movingEnergy)
        assertEquals(100, telemetry.stationaryEnergy)
        assertEquals(153, telemetry.distanceCm)   // state 2 → stat_d
        assertEquals(2, telemetry.radarState)
        assertEquals(0, telemetry.movingDistanceCm)
        assertEquals(153, telemetry.stationaryDistanceCm)
        assertEquals(29.06f, telemetry.temperatureC)
        assertEquals(60.35f, telemetry.humidityPercent)
    }

    @Test
    fun parseTelemetry_state1_movingOnly() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = """{"temp":28.0,"hum":55.0,"radar":{"state":1,"mov_d":80,"mov_e":60,"stat_d":0,"stat_e":0}}""",
        )

        requireNotNull(telemetry)
        assertTrue(telemetry.presence)
        assertEquals(60, telemetry.movingEnergy)
        assertEquals(0, telemetry.stationaryEnergy)
        assertEquals(80, telemetry.distanceCm)    // state 1 → mov_d
        assertEquals(1, telemetry.radarState)
        assertEquals(80, telemetry.movingDistanceCm)
        assertEquals(0, telemetry.stationaryDistanceCm)
    }

    @Test
    fun parseTelemetry_state0_noTarget() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = """{"temp":29.10,"hum":60.36,"radar":{"state":0,"mov_d":0,"mov_e":0,"stat_d":0,"stat_e":0}}""",
        )

        requireNotNull(telemetry)
        assertFalse(telemetry.presence)
        assertEquals(0, telemetry.distanceCm)
        assertEquals(0, telemetry.movingEnergy)
        assertEquals(0, telemetry.stationaryEnergy)
    }

    @Test
    fun parseTelemetry_trimsLeadingTrailingWhitespace() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = """  {"temp":29.0,"hum":60.0,"radar":{"state":2,"mov_d":0,"mov_e":0,"stat_d":100,"stat_e":80}}  """,
        )
        requireNotNull(telemetry)
        assertTrue(telemetry.presence)
    }

    @Test
    fun parseTelemetry_missingTempAndHum_returnsNullFields() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = """{"radar":{"state":2,"mov_d":0,"mov_e":0,"stat_d":100,"stat_e":80}}""",
        )

        requireNotNull(telemetry)
        assertNull(telemetry.temperatureC)
        assertNull(telemetry.humidityPercent)
    }

    // --- Invalid / malformed packets ---

    @Test
    fun parseTelemetry_missingRadarObject_returnsNull() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = """{"temp":29.0,"hum":60.0}""",
        )
        assertNull(telemetry)
    }

    @Test
    fun parseTelemetry_plainText_returnsNull() {
        assertNull(UsbDataParser.parseTelemetry("LD2412:PRES=1,MOV=42,STA=85,DIST=120"))
    }

    @Test
    fun parseTelemetry_emptyString_returnsNull() {
        assertNull(UsbDataParser.parseTelemetry(""))
    }

    @Test
    fun parseTelemetry_truncatedJson_returnsNull() {
        assertNull(UsbDataParser.parseTelemetry("""{"temp":29.06,"hum":60"""))
    }
}
