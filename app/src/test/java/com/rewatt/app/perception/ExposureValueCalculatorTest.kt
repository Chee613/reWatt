package com.rewatt.app.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureValueCalculatorTest {
    @Test
    fun calculate_usesFixedAperture() {
        val reading = ExposureValueCalculator.calculate(
            iso = 100,
            exposureTimeSeconds = 0.25,
        )

        requireNotNull(reading)
        assertEquals(ExposureValueCalculator.FIXED_APERTURE, reading.aperture, 0.0)
        assertEquals(3.696, reading.ev, 0.001)
        assertTrue(reading.lightsOn)
    }

    @Test
    fun calculate_mapsEvBoundaryToBinaryLightState() {
        val belowThreshold = ExposureValueCalculator.calculate(
            iso = 100,
            exposureTimeSeconds = 1.0,
        )
        val atThreshold = ExposureValueCalculator.calculate(
            iso = 100,
            exposureTimeSeconds = 0.81,
        )
        val aboveThreshold = ExposureValueCalculator.calculate(
            iso = 100,
            exposureTimeSeconds = 0.25,
        )

        requireNotNull(belowThreshold)
        requireNotNull(atThreshold)
        requireNotNull(aboveThreshold)
        assertFalse(belowThreshold.lightsOn)
        assertEquals(2.0, atThreshold.ev, 0.000_001)
        assertTrue(atThreshold.lightsOn)
        assertTrue(aboveThreshold.lightsOn)
    }

    @Test
    fun fromExifAttributes_requiresIsoAndExposureTime() {
        assertNull(
            ExposureValueCalculator.fromExifAttributes(
                isoAttribute = null,
                exposureTimeAttribute = "1/60",
            ),
        )
        assertNull(
            ExposureValueCalculator.fromExifAttributes(
                isoAttribute = "100",
                exposureTimeAttribute = null,
            ),
        )
        assertNull(
            ExposureValueCalculator.fromExifAttributes(
                isoAttribute = "bad",
                exposureTimeAttribute = "1/60",
            ),
        )
        assertNull(
            ExposureValueCalculator.fromExifAttributes(
                isoAttribute = "100",
                exposureTimeAttribute = "bad",
            ),
        )
    }

    @Test
    fun fromExifAttributes_acceptsRationalExposureTime() {
        val reading = ExposureValueCalculator.fromExifAttributes(
            isoAttribute = "100",
            exposureTimeAttribute = "1/60",
        )

        requireNotNull(reading)
        assertEquals(1.0 / 60.0, reading.exposureTimeSeconds, 0.000_001)
        assertTrue(reading.lightsOn)
    }
}
