package com.rewatt.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the action buttons on the sticky energy-waste prompt:
 * - **Done**: the operator switched off the lights/AC — dismiss the current alert.
 * - **Snooze**: dismiss the current alert and suppress new ones for 5 minutes.
 */
class EnergyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            EnergyAlertNotifier.ACTION_DONE -> EnergyAlertNotifier.onDone(context)
            EnergyAlertNotifier.ACTION_SNOOZE -> EnergyAlertNotifier.onSnooze(context)
        }
    }
}
