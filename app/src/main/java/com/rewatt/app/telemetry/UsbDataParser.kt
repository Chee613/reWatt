package com.rewatt.app.telemetry

import org.json.JSONException
import org.json.JSONObject

object UsbDataParser {
    fun parseTelemetry(
        line: String,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): RadarTelemetry? {
        return try {
            val root = JSONObject(line.trim())
            val radar = root.getJSONObject("radar")

            val state = radar.getInt("state")
            val movD  = radar.optInt("mov_d", 0)
            val movE  = radar.optInt("mov_e", 0)
            val statD = radar.optInt("stat_d", 0)
            val statE = radar.optInt("stat_e", 0)

            val distanceCm = when (state) {
                1, 3 -> movD
                2    -> statD
                else -> 0
            }

            val temp = root.optDouble("temp", Double.NaN).takeIf { !it.isNaN() }?.toFloat()
            val hum  = root.optDouble("hum",  Double.NaN).takeIf { !it.isNaN() }?.toFloat()

            RadarTelemetry(
                presence          = state > 0,
                movingEnergy      = movE,
                stationaryEnergy  = statE,
                distanceCm        = distanceCm,
                temperatureC      = temp,
                humidityPercent   = hum,
                capturedAtMillis  = capturedAtMillis,
                rawPacket         = line.trim(),
            )
        } catch (_: JSONException) {
            null
        }
    }
}
