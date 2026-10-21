package com.rewatt.app.safety

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rewatt.app.ReWattApplication

/** "Dismiss" action on the fire notification — works without any service running. */
class FireDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as ReWattApplication).appGraph.fireMonitor.dismiss()
    }
}
