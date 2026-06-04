package com.rewatt.app.data

data class PerceptionSyncSummary(
    val personCount: Int = 0,
    val temperatureC: Float? = null,
    val humidityPercent: Float? = null,
    val lightsOn: Boolean? = null,
    val exposureEv: Double? = null,
    val exposureIso: Int? = null,
    val exposureTimeSeconds: Double? = null,
    val exposureAperture: Double = 1.8,
    val capturedAtMillis: Long? = null,
)
