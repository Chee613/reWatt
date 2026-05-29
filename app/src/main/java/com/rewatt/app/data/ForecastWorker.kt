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

class ForecastWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val firestore = FirebaseFirestore.getInstance()
    private val halls = listOf("Hall 1", "Hall 2", "Hall 3", "Hall 4")
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val cutoffMs = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        for (hall in halls) {
            try { computeAndStore(uid, hall, cutoffMs) } catch (_: Exception) { }
        }
        return Result.success()
    }

    private suspend fun computeAndStore(uid: String, hall: String, cutoffMs: Long) {
        val docs: List<DocumentSnapshot> = suspendCoroutine { cont ->
            firestore.collection("rewatt_users").document(uid)
                .collection("rooms").document(hall)
                .collection("telemetry")
                .whereGreaterThan("perceptionCapturedAt", cutoffMs)
                .get()
                .addOnSuccessListener { cont.resume(it.documents) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

        // Require data spanning at least 2 distinct calendar days
        val distinctDays = docs.mapNotNull { it.getLong("perceptionCapturedAt") }
            .map { dayFmt.format(Date(it)) }.toSet()
        if (distinctDays.size < 2) return

        // Build 7×24 seasonal average
        val sums   = Array(7) { IntArray(24) { 0 } }
        val counts = Array(7) { IntArray(24) { 0 } }
        val cal    = Calendar.getInstance()
        for (doc in docs) {
            val ts    = doc.getLong("perceptionCapturedAt") ?: continue
            val count = doc.getLong("personCount")?.toInt() ?: continue
            cal.timeInMillis = ts
            val dow  = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            sums[dow][hour]   += count
            counts[dow][hour] += 1
        }

        // Flatten to 168-int list for Firestore (index = day*24 + hour, values 0/1/2/3)
        val schedule = (0 until 7).flatMap { day ->
            (0 until 24).map { hour ->
                val n = counts[day][hour]
                if (n == 0) 0L else when (sums[day][hour] / n) {
                    0 -> 0L; in 1..40 -> 1L; in 41..80 -> 2L; else -> 3L
                }
            }
        }

        // Single-point forecast for next upcoming (dow, hour) slot
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.HOUR_OF_DAY, 1)
        val nextDow  = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val nextHour = cal.get(Calendar.HOUR_OF_DAY)
        val n        = counts[nextDow][nextHour]
        val avgCount = if (n == 0) 0 else sums[nextDow][nextHour] / n
        val level    = schedule[nextDow * 24 + nextHour].toInt()
        val predicted = when (level) { 1 -> 25; 2 -> 60; 3 -> 90; else -> 0 }
        val occupancyLevel = if (avgCount >= 5) "likely_occupied" else "likely_empty"
        val nextLabel = String.format(Locale.US, "%02d:00", nextHour)

        firestore.collection("rewatt_users").document(uid)
            .collection("rooms").document(hall)
            .collection("forecast").document("latest")
            .set(mapOf(
                "schedule"              to schedule,
                "predictedOccupancy"    to predicted,
                "occupancyLevel"        to occupancyLevel,
                "nextSessionStartLabel" to nextLabel,
                "forecastedAt"          to System.currentTimeMillis(),
                "source"                to "seasonal_avg_v1",
            ))
    }
}
