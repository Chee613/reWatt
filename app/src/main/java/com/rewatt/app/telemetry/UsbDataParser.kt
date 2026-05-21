package com.rewatt.app.telemetry

object UsbDataParser {
    private val telemetryRegex = Regex(
        pattern = """^\s*ENV:T=(-?\d+(?:\.\d+)?),H=(\d+(?:\.\d+)?),AQI=(\d+),TVOC=(\d+),CO2=(\d+)\|RADAR:MOV=(\d+),STA=(\d+),PRES=([01])\s*$""",
        option = RegexOption.IGNORE_CASE,
    )

    fun parseTelemetry(
        line: String,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): SensorTelemetry? {
        val match = telemetryRegex.find(line) ?: return null
        val values = match.groupValues

        return SensorTelemetry(
            tempC = values[1].toFloatOrNull() ?: return null,
            humidityPercent = values[2].toFloatOrNull() ?: return null,
            aqi = values[3].toIntOrNull() ?: return null,
            tvocPpb = values[4].toIntOrNull() ?: return null,
            eco2Ppm = values[5].toIntOrNull() ?: return null,
            movingEnergy = values[6].toIntOrNull() ?: return null,
            stationaryEnergy = values[7].toIntOrNull() ?: return null,
            presence = values[8] == "1",
            capturedAtMillis = capturedAtMillis,
            rawPacket = line.trim(),
        )
    }
}

