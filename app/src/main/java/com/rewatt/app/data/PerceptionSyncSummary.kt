package com.rewatt.app.data

data class PerceptionSyncSummary(
    val personCount: Int = 0,
    val topAudioLabels: List<String> = emptyList(),
    val audioPresenceDetected: Boolean = false,
    val audioPresenceScore: Float = 0f,
    val capturedAtMillis: Long? = null,
)
