package com.rewatt.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Once a day, summarizes how much electricity was wasted yesterday by lights/AC left on in an
 * empty room, and asks Gemini to translate that into kWh and RM for the dashboard card.
 *
 * "Wasted" is sensor-inferred: each 10-minute capture where the room was empty (personCount == 0)
 * but the lights were still on (lightsOn == true) counts as 10 wasted minutes. Gemini infers the
 * AC-unit and lightbulb counts from the room name + capacity to convert minutes into energy.
 */
class EnergyWasteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val firestore = FirebaseFirestore.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val prefs = applicationContext.getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
        val room = prefs.getString("space_name", null)?.takeIf { it.isNotBlank() } ?: "default-room"
        val capacity = prefs.getInt("space_capacity", -1).takeIf { it > 0 }

        // Yesterday's [start, end) window in local time.
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = cal.timeInMillis
        val dateLabel = dateFmt.format(Date(yesterdayStart))

        val docs: List<DocumentSnapshot> = suspendCoroutine { cont ->
            firestore.collection("rewatt_users").document(uid)
                .collection("rooms").document(room)
                .collection("telemetry")
                .whereGreaterThanOrEqualTo("perceptionCapturedAt", yesterdayStart)
                .whereLessThan("perceptionCapturedAt", todayStart)
                .get()
                .addOnSuccessListener { cont.resume(it.documents) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

        // No telemetry for yesterday → nothing to report; leave any prior summary untouched.
        if (docs.isEmpty()) return Result.success()

        val wastedSamples = docs.count { doc ->
            val empty = (doc.getLong("personCount")?.toInt() ?: 0) == 0
            val lightsOn = doc.getBoolean("lightsOn") == true
            empty && lightsOn
        }
        val wastedMinutes = wastedSamples * 10

        val analysis = if (wastedMinutes <= 0) {
            null
        } else {
            runCatching {
                GeminiEnergyWasteAnalyzer().analyze(
                    EnergyWasteInput(roomName = room, capacity = capacity, wastedMinutes = wastedMinutes),
                )
            }.getOrNull()
        }

        val payload = if (analysis != null) {
            mapOf(
                "dateLabel" to dateLabel,
                "wastedMinutes" to wastedMinutes,
                "wastedKwh" to analysis.wastedKwh,
                "costRm" to analysis.costRm,
                "acUnits" to analysis.acUnits,
                "lightbulbs" to analysis.lightbulbs,
                "roomType" to analysis.roomType,
                "summary" to analysis.summary,
                "generatedAt" to System.currentTimeMillis(),
            )
        } else {
            mapOf(
                "dateLabel" to dateLabel,
                "wastedMinutes" to wastedMinutes,
                "wastedKwh" to 0.0,
                "costRm" to 0.0,
                "acUnits" to 0,
                "lightbulbs" to 0,
                "roomType" to "",
                "summary" to if (wastedMinutes <= 0) {
                    "No wasted energy detected yesterday — the room was switched off when empty."
                } else {
                    "Detected $wastedMinutes minutes of lights left on in an empty room, but the energy estimate is unavailable."
                },
                "generatedAt" to System.currentTimeMillis(),
            )
        }

        return suspendCoroutine { cont ->
            firestore.collection("rewatt_users").document(uid)
                .collection("rooms").document(room)
                .collection("wasteSummary").document("latest")
                .set(payload)
                .addOnSuccessListener { cont.resume(Result.success()) }
                .addOnFailureListener { cont.resume(Result.retry()) }
        }
    }
}
