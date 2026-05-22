package com.rewatt.app.data

import android.content.Context
import android.provider.Settings
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions

class FirebaseSyncRepository(
    context: Context,
    private val roomId: String = DEFAULT_ROOM_ID,
) {
    private val appContext = context.applicationContext
    private val nodeId: String by lazy {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "unknown-node"
    }

    fun syncPerceptionSummary(
        perceptionSummary: PerceptionSyncSummary,
        onState: (FirebaseSyncState) -> Unit,
    ) {
        val capturedAtMillis = perceptionSummary.capturedAtMillis ?: return
        if (!hasFirebaseConfig()) {
            onState(
                FirebaseSyncState(
                    status = FirebaseSyncStatus.ConfigMissing,
                    message = "Firebase config missing. Add app/google-services.json, rebuild, and reinstall the app.",
                ),
            )
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        val payload = buildPerceptionPayload(
            perceptionSummary = perceptionSummary,
            roomId = roomId,
            nodeId = nodeId,
            updatedAtMillis = System.currentTimeMillis(),
        )
        val historyId = "$capturedAtMillis-$nodeId"

        onState(
            FirebaseSyncState(
                status = FirebaseSyncStatus.Pending,
                message = "Syncing latest people count and activity to Firestore.",
            ),
        )

        val nodePath = "rooms/$roomId/nodes/$nodeId"
        val historyPath = "rooms/$roomId/telemetry/$historyId"

        val nodeTask = firestore.collection("rooms")
            .document(roomId)
            .collection("nodes")
            .document(nodeId)
            .set(payload, SetOptions.merge())

        val historyTask = firestore.collection("rooms")
            .document(roomId)
            .collection("telemetry")
            .document(historyId)
            .set(payload + mapOf("historyId" to historyId), SetOptions.merge())

        observeWriteResults(
            writes = listOf(
                FirestoreWrite(path = nodePath, task = nodeTask),
                FirestoreWrite(path = historyPath, task = historyTask),
            ),
            onState = onState,
        )
    }

    fun syncActivityAnalysis(
        perceptionSummary: PerceptionSyncSummary,
        activityAnalysis: ActivityAnalysis,
    ) {
        val capturedAtMillis = perceptionSummary.capturedAtMillis ?: return
        if (!hasFirebaseConfig()) return

        val firestore = FirebaseFirestore.getInstance()
        val updatedAtMillis = System.currentTimeMillis()
        val historyId = "$capturedAtMillis-$nodeId"
        val payload = mapOf(
            "activityAnalysis" to buildActivityAnalysisPayload(
                analysis = activityAnalysis,
                status = "complete",
                analyzedAtMillis = updatedAtMillis,
            ),
            "lastUpdatedAt" to updatedAtMillis,
        )

        firestore.collection("rooms")
            .document(roomId)
            .collection("nodes")
            .document(nodeId)
            .set(payload, SetOptions.merge())

        firestore.collection("rooms")
            .document(roomId)
            .collection("telemetry")
            .document(historyId)
            .set(payload, SetOptions.merge())
    }

    companion object {
        private const val DEFAULT_ROOM_ID = "default-room"
    }

    private fun hasFirebaseConfig(): Boolean {
        if (FirebaseApp.getApps(appContext).isNotEmpty()) return true
        return runCatching { FirebaseApp.initializeApp(appContext) != null }
            .getOrDefault(false)
    }
}

private data class FirestoreWrite(
    val path: String,
    val task: Task<Void>,
)

private fun observeWriteResults(
    writes: List<FirestoreWrite>,
    onState: (FirebaseSyncState) -> Unit,
) {
    val lock = Any()
    val failures = linkedMapOf<String, String>()
    var remainingWrites = writes.size

    writes.forEach { write ->
        write.task.addOnCompleteListener { task ->
            val finalState = synchronized(lock) {
                if (!task.isSuccessful) {
                    failures[write.path] = task.exception.toFirestoreFailureMessage()
                }
                remainingWrites -= 1
                if (remainingWrites == 0) {
                    if (failures.isEmpty()) {
                        FirebaseSyncState(
                            status = FirebaseSyncStatus.Synced,
                            message = "Firestore sync complete.",
                            lastSyncedAtMillis = System.currentTimeMillis(),
                        )
                    } else {
                        FirebaseSyncState(
                            status = FirebaseSyncStatus.Failed,
                            message = buildFirestoreWriteFailureMessage(failures),
                        )
                    }
                } else {
                    null
                }
            }
            finalState?.let(onState)
        }
    }
}

fun buildFirestoreWriteFailureMessage(failures: Map<String, String>): String =
    "Firestore sync failed: " + failures.entries.joinToString(separator = " | ") { (path, error) ->
        "$path: $error"
    }

private fun Throwable?.toFirestoreFailureMessage(): String {
    if (this == null) return "Unknown Firestore error."
    val code = (this as? FirebaseFirestoreException)?.code?.name
    val detail = message ?: javaClass.simpleName
    return if (code.isNullOrBlank()) detail else "$code - $detail"
}

fun buildPerceptionPayload(
    perceptionSummary: PerceptionSyncSummary,
    roomId: String,
    nodeId: String,
    updatedAtMillis: Long,
): Map<String, Any?> =
    mapOf(
        "roomId" to roomId,
        "nodeId" to nodeId,
        "lastUpdatedAt" to updatedAtMillis,
        "perceptionCapturedAt" to perceptionSummary.capturedAtMillis,
        "personCount" to perceptionSummary.personCount,
        "primaryActivity" to perceptionSummary.activityLabels.firstOrNull(),
        "activityLabels" to perceptionSummary.activityLabels.take(MAX_ACTIVITY_LABELS),
        "temperatureC" to perceptionSummary.temperatureC,
        "humidityPercent" to perceptionSummary.humidityPercent,
    )

private const val MAX_ACTIVITY_LABELS = 3

fun buildActivityAnalysisPayload(
    analysis: ActivityAnalysis,
    status: String,
    analyzedAtMillis: Long,
): Map<String, Any> =
    mapOf(
        "activity" to analysis.activity,
        "confidence" to analysis.confidence,
        "reason" to analysis.reason,
        "occupancyHint" to analysis.occupancyHint,
        "status" to status,
        "model" to GeminiActivityAnalyzer.MODEL_ID,
        "analyzedAt" to analyzedAtMillis,
    )
