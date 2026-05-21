package com.rewatt.app.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDataParserTest {
    @Test
    fun parseTelemetry_acceptsValidPayload() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "ENV:T=26.5,H=58.2,AQI=1,TVOC=45,CO2=415|RADAR:MOV=42,STA=85,PRES=1",
            capturedAtMillis = 123L,
        )

        requireNotNull(telemetry)
        assertEquals(26.5f, telemetry.tempC)
        assertEquals(58.2f, telemetry.humidityPercent)
        assertEquals(1, telemetry.aqi)
        assertEquals(45, telemetry.tvocPpb)
        assertEquals(415, telemetry.eco2Ppm)
        assertEquals(42, telemetry.movingEnergy)
        assertEquals(85, telemetry.stationaryEnergy)
        assertTrue(telemetry.presence)
        assertEquals(123L, telemetry.capturedAtMillis)
    }

    @Test
    fun parseTelemetry_acceptsNegativeTemperature() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "ENV:T=-4.5,H=40.0,AQI=1,TVOC=20,CO2=390|RADAR:MOV=0,STA=0,PRES=0",
        )

        requireNotNull(telemetry)
        assertEquals(-4.5f, telemetry.tempC)
        assertFalse(telemetry.presence)
    }

    @Test
    fun parseTelemetry_acceptsIntegerAndDecimalValues() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "ENV:T=27,H=59.5,AQI=2,TVOC=70,CO2=500|RADAR:MOV=10,STA=20,PRES=1",
        )

        requireNotNull(telemetry)
        assertEquals(27f, telemetry.tempC)
        assertEquals(59.5f, telemetry.humidityPercent)
    }

    @Test
    fun parseTelemetry_trimsWhitespaceAndNewlines() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "  ENV:T=26.5,H=58.2,AQI=1,TVOC=45,CO2=415|RADAR:MOV=42,STA=85,PRES=1\r\n",
        )

        requireNotNull(telemetry)
        assertEquals("ENV:T=26.5,H=58.2,AQI=1,TVOC=45,CO2=415|RADAR:MOV=42,STA=85,PRES=1", telemetry.rawPacket)
    }

    @Test
    fun parseTelemetry_rejectsMissingFields() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "ENV:T=26.5,H=58.2,AQI=1,TVOC=45|RADAR:MOV=42,STA=85,PRES=1",
        )

        assertNull(telemetry)
    }

    @Test
    fun parseTelemetry_rejectsMalformedPrefixes() {
        val telemetry = UsbDataParser.parseTelemetry(
            line = "TMP:T=26.5,H=58.2,AQI=1,TVOC=45,CO2=415|RADAR:MOV=42,STA=85,PRES=1",
        )

        assertNull(telemetry)
    }
}

