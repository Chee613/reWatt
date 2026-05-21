package com.rewatt.app.data

enum class FirebaseSyncStatus {
    ConfigMissing,
    Idle,
    Pending,
    Synced,
    Failed,
}

data class FirebaseSyncState(
    val status: FirebaseSyncStatus = FirebaseSyncStatus.ConfigMissing,
    val message: String = "Firebase config missing. Add app/google-services.json to enable Firestore sync.",
    val lastSyncedAtMillis: Long? = null,
)

