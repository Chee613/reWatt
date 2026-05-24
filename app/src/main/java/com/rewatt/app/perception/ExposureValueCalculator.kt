package com.rewatt.app.perception

import androidx.exifinterface.media.ExifInterface
import kotlin.math.log2

object ExposureValueCalculator {
    const val FIXED_APERTURE: Double = 1.8
    const val LIGHTS_ON_EV_THRESHOLD: Double = 2.0

    fun fromExif(imagePath: String): ExposureReading? {
        val exif = ExifInterface(imagePath)
        return fromExifAttributes(
            isoAttribute = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            exposureTimeAttribute = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
        )
    }

    fun fromExifAttributes(
        isoAttribute: String?,
        exposureTimeAttribute: String?,
    ): ExposureReading? {
        val iso = isoAttribute?.trim()?.toIntOrNull() ?: return null
        val exposureTimeSeconds = exposureTimeAttribute.toPositiveDoubleOrNull() ?: return null
        return calculate(iso = iso, exposureTimeSeconds = exposureTimeSeconds)
    }

    fun calculate(
        iso: Int,
        exposureTimeSeconds: Double,
    ): ExposureReading? {
        if (iso <= 0 || !exposureTimeSeconds.isFinite() || exposureTimeSeconds <= 0.0) {
            return null
        }

        val isoValue = iso.toDouble()
        val ev = log2((FIXED_APERTURE * FIXED_APERTURE) / exposureTimeSeconds) -
            log2(isoValue / 100.0)
        return ExposureReading(
            ev = ev,
            iso = iso,
            exposureTimeSeconds = exposureTimeSeconds,
            aperture = FIXED_APERTURE,
            lightsOn = ev >= LIGHTS_ON_EV_THRESHOLD,
        )
    }
}

private fun String?.toPositiveDoubleOrNull(): Double? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parsed = if ('/' in value) {
        val parts = value.split("/", limit = 2)
        val numerator = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
        val denominator = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (numerator == null || denominator == null || denominator == 0.0) {
            null
        } else {
            numerator / denominator
        }
    } else {
        value.toDoubleOrNull()
    }
    return parsed?.takeIf { it.isFinite() && it > 0.0 }
}
