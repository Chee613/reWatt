package com.rewatt.app.energy

import com.rewatt.app.perception.PerceptionStateHolder
import com.rewatt.app.security.SecurityStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Occupancy checks for the ENERGY tab, run whenever [EnergyStateHolder.requestCheck] is called
 * (nothing calls it yet — the triggers are being replaced; the mmWave does not drive it):
 *
 *  ENTER → "Movement detected" banner → photo right away → YOLO headcount.
 *  EXIT  → "No movement detected" banner → photo → YOLO headcount.
 *  People seen → occupied. 0 people → the room must stay empty for [EMPTY_CONFIRM_MS] (another
 *  check or a capture that finds people cancels it) → "Turn OFF lights and aircond" banner.
 *
 * Paused while CCTV is Armed. Every photo also goes through the normal occupancy pipeline
 * (dashboard frame, EV, MQTT summary) exactly like the 10-minute capture.
 */
class EnergyMonitor(
    private val scope: CoroutineScope,
    private val perception: PerceptionStateHolder,
    private val security: SecurityStateHolder,
    private val holder: EnergyStateHolder,
    /** Take one occupancy photo. */
    private val requestCapture: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            launch { watchResults() }
            launch { watchCountdown() }
            for (check in holder.checkRequests) {
                if (!security.state.value.armed) runCheck(check)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        holder.update { EnergyUiState(personCount = it.personCount) }
    }

    /** Every finished analysis (any capture source, e.g. the 10-minute one) updates the count. */
    private suspend fun watchResults() {
        perception.state.distinctUntilChangedBy { it.analyzedAtMillis }.collect { s ->
            if (s.analyzedAtMillis == null || !s.analysisOk) return@collect
            holder.update { it.copy(personCount = s.personCount) }
        }
    }

    private suspend fun watchCountdown() {
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            if (security.state.value.armed) {
                holder.update {
                    if (it.pausedForCctv) it else it.copy(phase = EnergyPhase.IDLE, pausedForCctv = true, countdownEndsAt = null)
                }
            } else {
                holder.update { state ->
                    when {
                        state.pausedForCctv -> state.copy(pausedForCctv = false)
                        state.phase != EnergyPhase.EMPTY_COUNTDOWN -> state
                        // Another capture (e.g. the 10-minute one) found people: not empty after all.
                        (state.personCount ?: 0) > 0 -> state.copy(phase = EnergyPhase.OCCUPIED, countdownEndsAt = null)
                        now >= (state.countdownEndsAt ?: now) -> state.copy(
                            phase = EnergyPhase.EMPTY,
                            countdownEndsAt = null,
                            banner = EnergyBanner(now, EnergyBanner.Kind.ROOM_EMPTY, now),
                        )
                        else -> state
                    }
                }
            }
            delay(POLL_MS)
        }
    }

    private suspend fun runCheck(check: EnergyCheck) {
        val now = System.currentTimeMillis()
        val leaving = check == EnergyCheck.EXIT
        holder.update {
            it.copy(
                phase = if (leaving) EnergyPhase.LEAVING else EnergyPhase.ENTERING,
                countdownEndsAt = null,
                banner = EnergyBanner(now, if (leaving) EnergyBanner.Kind.NO_MOTION else EnergyBanner.Kind.MOTION, now),
            )
        }
        val count = captureAndCount()
        val done = System.currentTimeMillis()
        holder.update {
            when {
                count == null -> it.copy(phase = EnergyPhase.IDLE) // capture failed / YOLO not ready
                // The checked photo stays in the camera box until it is tapped.
                count == 0 -> it.copy(
                    phase = EnergyPhase.EMPTY_COUNTDOWN,
                    countdownEndsAt = done + EMPTY_CONFIRM_MS,
                    photoPinned = true,
                )
                else -> it.copy(phase = EnergyPhase.OCCUPIED, photoPinned = true)
            }
        }
    }

    /** Take one occupancy photo and wait for YOLO. Null when the capture or YOLO was not available. */
    private suspend fun captureAndCount(): Int? {
        val before = perception.state.value.analyzedAtMillis ?: 0L
        holder.update { it.copy(capturing = true, photoPinned = false) }
        requestCapture()
        val result = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
            perception.state.first { (it.analyzedAtMillis ?: 0L) > before }
        }
        holder.update { it.copy(capturing = false) }
        return result?.takeIf { it.analysisOk }?.personCount
    }

    companion object {
        private const val POLL_MS = 200L
        const val EMPTY_CONFIRM_MS = 10_000L
        private const val CAPTURE_TIMEOUT_MS = 15_000L
    }
}
