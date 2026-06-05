package com.rewatt.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the **Done** button on the NOW-action notification: marks the recommendation acknowledged
 * (tracked locally + in Firestore) and clears the notification. See [NowActionNotifier].
 */
class NowActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NowActionNotifier.ACTION_DONE -> {
                val signature = intent.getStringExtra(NowActionNotifier.EXTRA_SIGNATURE).orEmpty()
                NowActionNotifier.onDone(context, signature)
            }
            NowActionNotifier.ACTION_DEMO_DISMISS -> NowActionNotifier.onDemoDismiss(context)
        }
    }
}
