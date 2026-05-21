package com.rewatt.app.data

import android.content.Context
import android.provider.Settings
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.rewatt.app.telemetry.RadarTelemetry

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

    fun syncRadarTelemetry(
        telemetry: RadarTelemetry,
        perceptionSummary: PerceptionSyncSummary,
        onState: (FirebaseSyncState) -> Unit,
    ) {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            onState(FirebaseSyncState())
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        val payload = buildTelemetryPayload(
            telemetry = telemetry,
            perceptionSummary = perceptionSummary,
            roomId = roomId,
            nodeId = nodeId,
        )
        val historyId = "${telemetry.capturedAtMillis}-$nodeId"

        onState(
            FirebaseSyncState(
                status = FirebaseSyncStatus.Pending,
                message = "Syncing latest radar state to Firestore.",
            ),
        )

        val nodeTask = firestore.collection("rooms")
            .document(roomId)
            .collection("nodes")
            .document(nodeId)
            .set(payload, SetOptions.merge())

        val historyTask = firestore.collection("rooms")
            .document(roomId)
            .collection("telemetry")
            .document(historyId)
            .set(payload + mapOf("historyId" to historyId))

        Tasks.whenAll(nodeTask, historyTask)
            .addOnSuccessListener {
                onState(
                    FirebaseSyncState(
                        status = FirebaseSyncStatus.Synced,
                        message = "Firestore sync complete.",
                        lastSyncedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
            .addOnFailureListener { error ->
                onState(
                    FirebaseSyncState(
                        status = FirebaseSyncStatus.Failed,
                        message = "Firestore sync failed: ${error.message ?: error::class.java.simpleName}",
                    ),
                )
            }
    }

    companion object {
        private const val DEFAULT_ROOM_ID = "default-room"
    }
}

fun buildTelemetryPayload(
    telemetry: RadarTelemetry,
    perceptionSummary: PerceptionSyncSummary,
    roomId: String,
    nodeId: String,
): Map<String, Any?> =
    mapOf(
        "roomId" to roomId,
        "nodeId" to nodeId,
        "presence" to telemetry.presence,
        "movingEnergy" to telemetry.movingEnergy,
        "stationaryEnergy" to telemetry.stationaryEnergy,
        "distanceCm" to telemetry.distanceCm,
        "rawPacket" to telemetry.rawPacket,
        "lastSeenAt" to telemetry.capturedAtMillis,
        "personCount" to perceptionSummary.personCount,
        "topAudioLabels" to perceptionSummary.topAudioLabels,
        "audioPresenceDetected" to perceptionSummary.audioPresenceDetected,
        "audioPresenceScore" to perceptionSummary.audioPresenceScore,
        "perceptionCapturedAt" to perceptionSummary.capturedAtMillis,
    )
