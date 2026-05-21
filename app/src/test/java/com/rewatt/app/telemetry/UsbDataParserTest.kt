package com.rewatt.app.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDataParserTest {
    @Test
    fun parseTelemetry_acceptsRadarPayloadWithPresence() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "LD2412:PRES=1,MOV=42,STA=85,DIST=120|SHT30:T=26.5,H=58.2",
            capturedAtMillis = 123L,
        )

        requireNotNull(telemetry)
        assertTrue(telemetry.presence)
        assertEquals(42, telemetry.movingEnergy)
        assertEquals(85, telemetry.stationaryEnergy)
        assertEquals(120, telemetry.distanceCm)
        assertEquals(26.5f, telemetry.temperatureC)
        assertEquals(58.2f, telemetry.humidityPercent)
        assertEquals(123L, telemetry.capturedAtMillis)
    }

    @Test
    fun parseTelemetry_acceptsLegacyRadarPrefix() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "RADAR:PRES=1,MOV=42,STA=85,DIST=120",
        )

        requireNotNull(telemetry)
        assertTrue(telemetry.presence)
    }

    @Test
    fun parseTelemetry_acceptsRadarPayloadWithoutPresence() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "LD2412:PRES=0,MOV=0,STA=0,DIST=0",
        )

        requireNotNull(telemetry)
        assertFalse(telemetry.presence)
        assertEquals(0, telemetry.distanceCm)
        assertNull(telemetry.temperatureC)
        assertNull(telemetry.humidityPercent)
    }

    @Test
    fun parseTelemetry_acceptsNegativeTemperature() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "LD2412:PRES=1,MOV=42,STA=85,DIST=120|SHT30:T=-2.5,H=58",
        )

        requireNotNull(telemetry)
        assertEquals(-2.5f, telemetry.temperatureC)
        assertEquals(58f, telemetry.humidityPercent)
    }

    @Test
    fun parseTelemetry_rejectsMalformedSht30Payload() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "LD2412:PRES=1,MOV=42,STA=85,DIST=120|SHT30:T=warm,H=58.2",
        )

        assertNull(telemetry)
    }

    @Test
    fun parseTelemetry_trimsWhitespaceAndNewlines() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "  RADAR:PRES=1,MOV=42,STA=85,DIST=120\r\n",
        )

        requireNotNull(telemetry)
        assertEquals("RADAR:PRES=1,MOV=42,STA=85,DIST=120", telemetry.rawPacket)
    }

    @Test
    fun parseTelemetry_rejectsOldEnvironmentalPayload() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "ENV:T=26.5,H=58.2,AQI=1,TVOC=45,CO2=415|RADAR:MOV=42,STA=85,PRES=1",
        )

        assertNull(telemetry)
    }

    @Test
    fun parseTelemetry_rejectsMissingRadarFields() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "RADAR:PRES=1,MOV=42,STA=85",
        )

        assertNull(telemetry)
    }

    @Test
    fun parseTelemetry_rejectsMalformedPrefixes() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "LD2410B:PRES=1,MOV=42,STA=85,DIST=120",
        )

        assertNull(telemetry)
    }
}
