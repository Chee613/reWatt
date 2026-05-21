package com.rewatt.app.telemetry

object UsbDataParser {
    private val telemetryRegex = Regex(
        pattern = """^\s*(?:RADAR|LD2412):PRES=([01]),MOV=(\d+),STA=(\d+),DIST=(\d+)(?:\|SHT30:T=(-?\d+(?:\.\d+)?),H=(\d+(?:\.\d+)?))?\s*$""",
        option = RegexOption.IGNORE_CASE,
    )

    fun parseTelemetry(
        line: String,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): RadarTelemetry? {
        val match = telemetryRegex.find(line) ?: return null
        val values = match.groupValues

        return RadarTelemetry(
            presence = values[1] == "1",
            movingEnergy = values[2].toIntOrNull() ?: return null,
            stationaryEnergy = values[3].toIntOrNull() ?: return null,
            distanceCm = values[4].toIntOrNull() ?: return null,
            temperatureC = values.getOrNull(5)?.takeIf { it.isNotBlank() }?.toFloatOrNull(),
            humidityPercent = values.getOrNull(6)?.takeIf { it.isNotBlank() }?.toFloatOrNull(),
            capturedAtMillis = capturedAtMillis,
            rawPacket = line.trim(),
        )
    }
}
