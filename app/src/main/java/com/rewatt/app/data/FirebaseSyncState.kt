package com.rewatt.app.data

enum class FirebaseSyncStatus {
    ConfigMissing,
    Idle,
    Pending,
    Synced,
    Failed,
}

data class FirebaseSyncState(
    val status: FirebaseSyncStatus = FirebaseSyncStatus.Idle,
    val message: String = "Firestore sync is waiting for a perception capture.",
    val lastSyncedAtMillis: Long? = null,
)
