package com.rewatt.app.data

import android.content.Context
import android.provider.Settings
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class HallSnapshot(
    val personCount: Int = 0,
    val primaryActivity: String? = null,
    val temperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val lastUpdatedAt: Long? = null,
)

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

    // Use the sensor's assigned space name as the Firestore room path so each
    // sensor's data lands under its hall (e.g. "Hall 1/nodes/...").
    // Falls back to the constructor-supplied roomId if no space name is set.
    private val effectiveRoomId: String
        get() {
            val name = appContext
                .getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
                .getString("space_name", null)
            return if (!name.isNullOrBlank()) name else roomId
        }

    // Current authenticated user's UID — all Firestore paths are scoped under it
    // so different buildings (accounts) never share data.
    private val uid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")

    // Base Firestore reference for a given room, scoped under the current user's UID.
    private fun roomRef(roomId: String) =
        FirebaseFirestore.getInstance()
            .collection("rewatt_users").document(uid)
            .collection("rooms").document(roomId)

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

        val room = effectiveRoomId
        val payload = buildPerceptionPayload(
            perceptionSummary = perceptionSummary,
            roomId = room,
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

        val nodePath = "rewatt_users/$uid/rooms/$room/nodes/$nodeId"
        val historyPath = "rewatt_users/$uid/rooms/$room/telemetry/$historyId"

        val nodeTask = roomRef(room)
            .collection("nodes")
            .document(nodeId)
            .set(payload, SetOptions.merge())

        val historyTask = roomRef(room)
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

        val updatedAtMillis = System.currentTimeMillis()
        val historyId = "$capturedAtMillis-$nodeId"
        val room = effectiveRoomId
        val payload = mapOf(
            "activityAnalysis" to buildActivityAnalysisPayload(
                analysis = activityAnalysis,
                status = "complete",
                analyzedAtMillis = updatedAtMillis,
            ),
            "lastUpdatedAt" to updatedAtMillis,
        )

        roomRef(room)
            .collection("nodes")
            .document(nodeId)
            .set(payload, SetOptions.merge())

        roomRef(room)
            .collection("telemetry")
            .document(historyId)
            .set(payload, SetOptions.merge())
    }

    fun listenToHall(roomId: String): Flow<HallSnapshot?> = callbackFlow {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { trySend(null); awaitClose { }; return@callbackFlow }
        val listener = FirebaseFirestore.getInstance()
            .collection("rewatt_users").document(uid)
            .collection("rooms").document(roomId)
            .collection("nodes")
            .addSnapshotListener { snapshot, _ ->
                val doc = snapshot?.documents
                    ?.maxByOrNull { it.getLong("lastUpdatedAt") ?: 0L }
                trySend(
                    doc?.let {
                        HallSnapshot(
                            personCount = it.getLong("personCount")?.toInt() ?: 0,
                            primaryActivity = it.getString("primaryActivity"),
                            temperatureC = it.getDouble("temperatureC"),
                            humidityPercent = it.getDouble("humidityPercent"),
                            lastUpdatedAt = it.getLong("lastUpdatedAt"),
                        )
                    },
                )
            }
        awaitClose { listener.remove() }
    }

    suspend fun loadHallSchedule(hallLabel: String): Array<IntArray> {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return Array(7) { IntArray(24) { 0 } }
        val cutoffMs = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        return suspendCoroutine { cont ->
            FirebaseFirestore.getInstance()
                .collection("rewatt_users").document(currentUid)
                .collection("rooms").document(hallLabel)
                .collection("telemetry")
                .whereGreaterThan("perceptionCapturedAt", cutoffMs)
                .get()
                .addOnSuccessListener { snapshot ->
                    val sums = Array(7) { IntArray(24) { 0 } }
                    val counts = Array(7) { IntArray(24) { 0 } }
                    val cal = java.util.Calendar.getInstance()
                    for (doc in snapshot.documents) {
                        val ts = doc.getLong("perceptionCapturedAt") ?: continue
                        val count = doc.getLong("personCount")?.toInt() ?: continue
                        cal.timeInMillis = ts
                        val dow = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        sums[dow][hour] += count
                        counts[dow][hour]++
                    }
                    cont.resume(Array(7) { day ->
                        IntArray(24) { hour ->
                            val n = counts[day][hour]
                            if (n == 0) 0
                            else when (sums[day][hour] / n) {
                                0 -> 0
                                in 1..40 -> 1
                                in 41..80 -> 2
                                else -> 3
                            }
                        }
                    })
                }
                .addOnFailureListener { cont.resume(Array(7) { IntArray(24) { 0 } }) }
        }
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
        "lightsOn" to perceptionSummary.lightsOn,
        "exposureEv" to perceptionSummary.exposureEv,
        "exposureIso" to perceptionSummary.exposureIso,
        "exposureTimeSeconds" to perceptionSummary.exposureTimeSeconds,
        "exposureAperture" to perceptionSummary.exposureAperture,
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
