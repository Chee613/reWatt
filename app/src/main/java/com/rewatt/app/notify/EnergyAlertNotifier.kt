package com.rewatt.app.notify

import android.content.Context
import androidx.core.content.ContextCompat
import com.rewatt.app.data.UserRole

/**
 * Decides when the sticky "turn off the lights/AC" prompt should be shown and drives
 * [EnergyAlertService] to display it as a non-swipeable foreground-service notification.
 *
 * **Every capture** that detects an empty room with the lights/AC still on will trigger the
 * alert. **Done** dismisses the current alert; if the next capture still detects wasteful
 * conditions, the alert fires again immediately. **Snooze** dismisses the current alert *and*
 * suppresses all new alerts for [SNOOZE_DURATION_MILLIS] (5 minutes), even if the room stays
 * wasteful, so the operator gets time to actually walk over and switch things off.
 */
object EnergyAlertNotifier {
    const val ACTION_DONE = "com.rewatt.app.action.ENERGY_DONE"
    const val ACTION_SNOOZE = "com.rewatt.app.action.ENERGY_SNOOZE"

    /** How long Snooze suppresses new alerts. */
    const val SNOOZE_DURATION_MILLIS = 5 * 60 * 1000L

    private const val PREFS_NAME = "rewatt_energy_alert"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_millis"

    /**
     * Evaluate the latest capture: an empty room with the lights still on is wasteful.
     * Shows the alert every time wasteful conditions are detected, and clears it when
     * conditions are no longer wasteful — unless a Snooze window is still active.
     */
    fun onPerception(context: Context, personCount: Int, lightsOn: Boolean?) {
        // Sensor-mode edge devices must stay silent. Read the locally-cached APP_MODE
        // directly from SharedPreferences (safe on a background thread, no Firestore).
        val mode = context
            .getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
            .getString("APP_MODE", null)
        if (mode == UserRole.Sensor.name) {
            stop(context) // clear any stale alert if a device just switched to Sensor
            return
        }

        val wasteful = personCount == 0 && lightsOn == true
        if (!wasteful) {
            stop(context)
            return
        }
        // Suppress while the operator's Snooze window is still active.
        if (isSnoozed(context)) {
            stop(context)
            return
        }
        show(context)
    }

    fun onDone(context: Context) {
        stop(context)
    }

    fun onSnooze(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_DURATION_MILLIS)
            .apply()
        stop(context)
    }

    /** True while a Snooze window started by [onSnooze] is still in effect. */
    private fun isSnoozed(context: Context): Boolean {
        val snoozeUntil = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SNOOZE_UNTIL, 0L)
        return System.currentTimeMillis() < snoozeUntil
    }

    /** Start the foreground service whose notification cannot be swiped away. */
    private fun show(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(context, EnergyAlertService.startIntent(context))
        }
    }

    /** Stop the service, which removes its foreground notification, and close the full-screen activity. */
    private fun stop(context: Context) {
        EnergyAlertActivity.finishIfRunning()
        runCatching { context.stopService(EnergyAlertService.startIntent(context)) }
    }
}
