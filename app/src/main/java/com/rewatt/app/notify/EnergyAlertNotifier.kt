package com.rewatt.app.notify

import android.content.Context
import androidx.core.content.ContextCompat
import com.rewatt.app.data.UserRole

/**
 * Decides when the sticky "turn off the lights/AC" prompt should be shown and drives
 * [EnergyAlertService] to display it as a non-swipeable foreground-service notification.
 *
 * **Every capture** that detects an empty room with the lights/AC still on will trigger the
 * alert. The **Done** and **Snooze** buttons only dismiss the current alert; if the next
 * capture still detects wasteful conditions, the alert will fire again immediately.
 */
object EnergyAlertNotifier {
    const val ACTION_DONE = "com.rewatt.app.action.ENERGY_DONE"
    const val ACTION_SNOOZE = "com.rewatt.app.action.ENERGY_SNOOZE"

    /**
     * Evaluate the latest capture: an empty room with the lights still on is wasteful.
     * Shows the alert every time wasteful conditions are detected, and clears it when
     * conditions are no longer wasteful.
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
        // Always show the alert when wasteful — no suppression.
        show(context)
    }

    fun onDone(context: Context) {
        stop(context)
    }

    fun onSnooze(context: Context) {
        stop(context)
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
