package com.rewatt.app.data

data class PerceptionSyncSummary(
    val personCount: Int = 0,
    val activityLabels: List<String> = emptyList(),
    val audioPresenceDetected: Boolean = false,
    val audioPresenceScore: Float = 0f,
    val temperatureC: Float? = null,
    val humidityPercent: Float? = null,
    val lightsOn: Boolean? = null,
    val exposureEv: Double? = null,
    val exposureIso: Int? = null,
    val exposureTimeSeconds: Double? = null,
    val exposureAperture: Double = 1.8,
    val capturedAtMillis: Long? = null,
)
