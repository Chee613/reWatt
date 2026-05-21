package com.rewatt.app.data

enum class ActivityAnalysisStatus {
    ConfigMissing,
    Idle,
    Pending,
    Complete,
    Failed,
}

data class ActivityAnalysis(
    val activity: String,
    val confidence: Float,
    val reason: String,
    val occupancyHint: String,
)

data class ActivityAnalysisState(
    val status: ActivityAnalysisStatus = ActivityAnalysisStatus.Idle,
    val message: String = "Gemini activity analysis is waiting for top-3 YAMNet labels.",
    val analysis: ActivityAnalysis? = null,
    val lastAnalyzedAtMillis: Long? = null,
)
