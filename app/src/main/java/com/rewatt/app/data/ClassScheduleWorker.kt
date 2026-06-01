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
import kotlin.math.max
import kotlin.math.min

class ClassScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val firestore = FirebaseFirestore.getInstance()
    private val halls = listOf("Hall 1", "Hall 2", "Hall 3", "Hall 4")
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        // Refresh holiday list from Google Calendar (uses disk cache if still fresh)
        MalaysiaHolidays.refresh(applicationContext)
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

        val distinctDays = docs.mapNotNull { it.getLong("perceptionCapturedAt") }
            .map { dayFmt.format(Date(it)) }.toSet()
        if (distinctDays.size < 2) return

        // Group into (dow, bin10) → list of person counts across all weeks
        // bin10 = minuteOfDay / 10  →  144 bins per day
        val rawBins = Array(7) { Array(144) { mutableListOf<Int>() } }
        val cal = Calendar.getInstance()
        for (doc in docs) {
            val ts = doc.getLong("perceptionCapturedAt") ?: continue
            val count = doc.getLong("personCount")?.toInt() ?: continue
            cal.timeInMillis = ts
            // Skip public holidays — they have anomalously low occupancy and corrupt the seasonal mean
            if (MalaysiaHolidays.isHoliday(cal)) continue
            val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val bin = minuteOfDay / 10
            rawBins[dow][bin].add(count)
        }

        // Rolling max across all halls for relative threshold
        val globalMax = rawBins.flatMap { it.map { list -> list.median() } }.maxOrNull() ?: 0.0
        val threshold = max(5.0, 0.2 * globalMax)

        val learnedDays = Array(7) { dow ->
            val smoothed = smoothBins(rawBins[dow])
            val rawSegments = hysteresisSegments(smoothed, threshold)
            val merged = mergeShortGaps(rawSegments, gapBins = 2)
            clusterAndAggregate(merged, rawBins[dow])
        }

        // Find next upcoming session for forecast/latest update
        cal.timeInMillis = System.currentTimeMillis()
        val nowDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val nextLabel = findNextSessionLabel(learnedDays, nowDow, nowMinute)

        // Write learned_schedule/{0..6}
        for (dow in 0..6) {
            val segments = learnedDays[dow]
            val payload = mapOf(
                "segments" to segments.map { seg ->
                    mapOf(
                        "startMinuteOfDay" to seg.startMinuteOfDay,
                        "endMinuteOfDay" to seg.endMinuteOfDay,
                        "predictedOccupancy" to seg.predictedOccupancy,
                        "occupancyLevel" to seg.occupancyLevel,
                        "weeksSeen" to seg.weeksSeen,
                        "lastSeenMillis" to seg.lastSeenMillis,
                    )
                },
                "lastUpdatedAt" to System.currentTimeMillis(),
            )
            firestore.collection("rewatt_users").document(uid)
                .collection("rooms").document(hall)
                .collection("learned_schedule").document(dow.toString())
                .set(payload)
        }

        // Update nextSessionStartLabel in forecast/latest if we found one
        if (nextLabel != null) {
            firestore.collection("rewatt_users").document(uid)
                .collection("rooms").document(hall)
                .collection("forecast").document("latest")
                .update("nextSessionStartLabel", nextLabel)
        }
    }

    private fun smoothBins(rawBins: Array<MutableList<Int>>): DoubleArray {
        val medians = DoubleArray(144) { rawBins[it].median() }
        // 3-tap median filter
        return DoubleArray(144) { i ->
            val a = if (i > 0) medians[i - 1] else medians[i]
            val b = medians[i]
            val c = if (i < 143) medians[i + 1] else medians[i]
            median3(a, b, c)
        }
    }

    private fun hysteresisSegments(smoothed: DoubleArray, threshold: Double): List<RawSegment> {
        val segments = mutableListOf<RawSegment>()
        var state = HystState.IDLE
        var candidateStart = 0
        var sessionStart = 0
        var consecutiveCount = 0

        for (i in smoothed.indices) {
            val above = smoothed[i] > threshold
            when (state) {
                HystState.IDLE -> {
                    if (above) {
                        state = HystState.CANDIDATE_START
                        candidateStart = i
                        consecutiveCount = 1
                    }
                }
                HystState.CANDIDATE_START -> {
                    if (above) {
                        consecutiveCount++
                        if (consecutiveCount >= 3) {
                            state = HystState.ACTIVE
                            sessionStart = candidateStart
                        }
                    } else {
                        state = HystState.IDLE
                        consecutiveCount = 0
                    }
                }
                HystState.ACTIVE -> {
                    if (!above) {
                        state = HystState.CANDIDATE_END
                        consecutiveCount = 1
                    }
                }
                HystState.CANDIDATE_END -> {
                    if (!above) {
                        consecutiveCount++
                        if (consecutiveCount >= 3) {
                            segments.add(RawSegment(sessionStart * 10, (i - 3) * 10 + 10))
                            state = HystState.IDLE
                            consecutiveCount = 0
                        }
                    } else {
                        state = HystState.ACTIVE
                        consecutiveCount = 0
                    }
                }
            }
        }
        if (state == HystState.ACTIVE || state == HystState.CANDIDATE_END) {
            segments.add(RawSegment(sessionStart * 10, 143 * 10 + 10))
        }
        return segments
    }

    private fun mergeShortGaps(segments: List<RawSegment>, gapBins: Int): List<RawSegment> {
        if (segments.size < 2) return segments
        val gapMinutes = gapBins * 10
        val merged = mutableListOf(segments[0])
        for (i in 1 until segments.size) {
            val last = merged.last()
            if (segments[i].startMinute - last.endMinute <= gapMinutes) {
                merged[merged.lastIndex] = RawSegment(last.startMinute, segments[i].endMinute)
            } else {
                merged.add(segments[i])
            }
        }
        return merged
    }

    private fun clusterAndAggregate(
        segments: List<RawSegment>,
        rawBins: Array<MutableList<Int>>,
    ): List<LearnedSegment> {
        // Each RawSegment is already a per-day-of-week aggregate (smoothed median bins).
        // Compute median occupancy from the raw bins within the segment window.
        return segments.map { seg ->
            val startBin = seg.startMinute / 10
            val endBin = min(seg.endMinute / 10, 143)
            val counts = (startBin..endBin).flatMap { rawBins[it] }
            val medOcc = counts.median().toInt()
            val level = when {
                medOcc >= 80 -> "high"
                medOcc >= 40 -> "medium"
                else -> "low"
            }
            // weeksSeen = number of distinct weeks that contributed a capture in this window
            val weeksSeen = (startBin..endBin).sumOf { rawBins[it].size }
                .coerceAtLeast(1)
            LearnedSegment(
                startMinuteOfDay = seg.startMinute,
                endMinuteOfDay = seg.endMinute,
                predictedOccupancy = medOcc,
                occupancyLevel = level,
                weeksSeen = weeksSeen,
                lastSeenMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun findNextSessionLabel(
        learnedDays: Array<List<LearnedSegment>>,
        nowDow: Int,
        nowMinute: Int,
    ): String? {
        for (offset in 0..6) {
            val dow = (nowDow + offset) % 7
            val segments = learnedDays[dow]
            for (seg in segments.sortedBy { it.startMinuteOfDay }) {
                val startMin = seg.startMinuteOfDay
                if (offset == 0 && startMin <= nowMinute) continue
                val h = startMin / 60
                val m = startMin % 60
                return String.format(Locale.US, "%02d:%02d", h, m)
            }
        }
        return null
    }

    private enum class HystState { IDLE, CANDIDATE_START, ACTIVE, CANDIDATE_END }
    private data class RawSegment(val startMinute: Int, val endMinute: Int)
}

private fun List<Int>.median(): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    return if (sorted.size % 2 == 0)
        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    else
        sorted[sorted.size / 2].toDouble()
}

private fun median3(a: Double, b: Double, c: Double): Double {
    return if (a <= b) {
        if (b <= c) b else if (a <= c) c else a
    } else {
        if (a <= c) a else if (b <= c) c else b
    }
}
