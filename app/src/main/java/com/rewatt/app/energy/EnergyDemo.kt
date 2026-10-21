package com.rewatt.app.energy

import android.net.Uri
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnergyDemoState(
    val active: Boolean = false,
    /** Demo temperature; null = show the module's real reading (until the box is tapped). */
    val temperatureC: Float? = null,
    val occupancy: Int = 0,
    /** While the empty countdown runs: when it reaches 0:00. */
    val countdownEndsAt: Long? = null,
    /** Gallery video looping in the camera box instead of the live view (null = live view). */
    val videoUri: Uri? = null,
)

/**
 * ENERGY demo mode — off when the app starts. Long-pressing the ENERGY tab icon switches between
 * demo and normal mode; it stays as set across tabs. While on, no automatic photo is taken and the
 * mmWave is ignored; the presenter drives:
 *  - Temperature box: tap = +1 °C (starting from the real reading), long-press = 23 °C; reaching
 *    29 °C shows the "Suggest lower AC temperature" banner.
 *  - Occupancy number: tap = +1 person, long-press = back to 0 (starts at 0).
 *  - On entering by long-press: "No movement detected" banner → empty countdown shown as 5:00 m → 0:00 m over
 *    [COUNTDOWN_MS] → "Lights, fan and AC OFF" banner → the three appliance buttons turn red
 *    (each turns green again when tapped). Adding a person cancels a running countdown.
 *  - Camera box: long-press = pick a gallery video that loops (muted) in place of the live view;
 *    long-press again = back to the live view.
 *  - Leaving: back to real data and the live view; the appliance buttons go back to green.
 */
class EnergyDemo(private val holder: EnergyStateHolder) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null
    private val mutableState = MutableStateFlow(EnergyDemoState())
    val state: StateFlow<EnergyDemoState> = mutableState.asStateFlow()

    fun toggle() {
        if (mutableState.value.active) exit() else enter()
    }

    private fun enter() {
        val now = System.currentTimeMillis()
        mutableState.value = EnergyDemoState(active = true, countdownEndsAt = now + COUNTDOWN_MS)
        holder.update { it.copy(banner = EnergyBanner(now, EnergyBanner.Kind.NO_MOTION, now)) }
        countdownJob?.cancel()
        countdownJob = scope.launch {
            delay(COUNTDOWN_MS)
            val end = System.currentTimeMillis()
            mutableState.update { it.copy(countdownEndsAt = null) }
            holder.update { it.copy(banner = EnergyBanner(end, EnergyBanner.Kind.APPLIANCES_OFF, end)) }
            holder.setAllAppliances(false)
        }
    }

    private fun exit() {
        countdownJob?.cancel()
        countdownJob = null
        mutableState.value = EnergyDemoState()
        holder.setAllAppliances(true)
    }

    /**
     * Tap on the temperature box: +1 °C from what is shown (the real reading the first time).
     * Reaching [HOT_TEMPERATURE_C] shows the "Suggest lower AC temperature" banner.
     */
    fun addDegree(realTemperatureC: Float?) {
        val before = mutableState.value.let { it.temperatureC ?: realTemperatureC?.let { t -> floor(t) } ?: RESET_TEMPERATURE_C }
        val after = before + 1f
        mutableState.update { it.copy(temperatureC = after) }
        if (before < HOT_TEMPERATURE_C && after >= HOT_TEMPERATURE_C) {
            val now = System.currentTimeMillis()
            holder.update { it.copy(banner = EnergyBanner(now, EnergyBanner.Kind.LOWER_AC_TEMPERATURE, now)) }
        }
    }

    fun resetTemperature() {
        mutableState.update { it.copy(temperatureC = RESET_TEMPERATURE_C) }
    }

    fun addPerson() {
        countdownJob?.cancel()
        countdownJob = null
        mutableState.update { it.copy(occupancy = it.occupancy + 1, countdownEndsAt = null) }
    }

    fun resetPeople() {
        mutableState.update { it.copy(occupancy = 0) }
    }

    /** Loop [uri] in the camera box (null = back to the live view). Only while demo mode is on. */
    fun setVideo(uri: Uri?) {
        mutableState.update { if (it.active) it.copy(videoUri = uri) else it }
    }

    companion object {
        const val COUNTDOWN_MS = 5_000L

        /** The countdown shows 5:00 m (5 minutes) while it really takes [COUNTDOWN_MS]. */
        const val COUNTDOWN_SHOWN_SECONDS = 5 * 60
        const val RESET_TEMPERATURE_C = 23f
        const val HOT_TEMPERATURE_C = 29f
    }
}
