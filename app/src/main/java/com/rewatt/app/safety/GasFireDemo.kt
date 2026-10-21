package com.rewatt.app.safety

import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.telemetry.GasReading
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Long-press FIRE tab icon demo: simulated SGP41 readings that rise from normal air into the danger zone
 * and go through the REAL fire pipeline (same detection rule, siren, red screen, strobe) — but never
 * MQTT. Works without an SGP41 attached. Timeline (1 sample / s):
 *   0–7 s normal (VOC 100, NOx 1) → VOC +9/s, NOx +0.6/s → amber ≈ 8 s → red ≈ 10 s → alarm ≈ 11 s
 *   → holds high until the alarm is dismissed → falls back to normal → demo ends by itself.
 * Long-press again to stop early.
 */
class GasFireDemo(
    private val telemetry: TelemetryRepository,
    private val fireMonitor: FireMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val mutableActive = MutableStateFlow(false)
    val active: StateFlow<Boolean> = mutableActive.asStateFlow()

    fun toggle() {
        if (mutableActive.value) stop() else start()
    }

    private fun start() {
        mutableActive.value = true
        fireMonitor.resetForDemo()
        telemetry.gasDemoActive = true
        job = scope.launch {
            var voc = BASE_VOC
            var nox = BASE_NOX
            var tick = 0
            var alarmSeen = false
            var settledTicks = 0
            while (isActive) {
                val alarmOn = fireMonitor.alarm.state.value.active
                if (alarmOn) alarmSeen = true
                when {
                    alarmSeen && !alarmOn -> {
                        // Dismissed: air clears, then the demo ends.
                        voc = max(BASE_VOC, voc - 15.0)
                        nox = max(BASE_NOX, nox - 1.0)
                        if (voc <= BASE_VOC && nox <= BASE_NOX && ++settledTicks >= SETTLED_TICKS) break
                    }
                    tick < BASELINE_TICKS -> Unit
                    else -> {
                        voc = min(PEAK_VOC, voc + 9.0)
                        nox = min(PEAK_NOX, nox + 0.6)
                    }
                }
                telemetry.injectGas(
                    GasReading(voc.toInt(), nox.toInt(), System.currentTimeMillis(), simulated = true),
                )
                tick++
                delay(1_000)
            }
            finish()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        finish()
    }

    private fun finish() {
        telemetry.gasDemoActive = false
        telemetry.clearGas()
        mutableActive.value = false
    }

    companion object {
        private const val BASE_VOC = 100.0
        private const val BASE_NOX = 1.0
        private const val PEAK_VOC = 185.0
        private const val PEAK_NOX = 9.0
        private const val BASELINE_TICKS = 7
        private const val SETTLED_TICKS = 3
    }
}
