package com.rewatt.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Drives the exact-time Today's-Schedule notifications:
 * - [ScheduledActionScheduler.ACTION_FIRE] → show the fired event ([ScheduledActionNotifier]).
 * - [ScheduledActionNotifier.ACTION_DONE] → acknowledge + clear it.
 * - [ScheduledActionScheduler.ACTION_REARM] / BOOT_COMPLETED → (re)arm the day's alarms.
 */
class ScheduledActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ScheduledActionScheduler.ACTION_FIRE -> ScheduledActionNotifier.show(
                context = context,
                eventId = intent.getStringExtra(ScheduledActionScheduler.EXTRA_EVENT_ID).orEmpty(),
                title = intent.getStringExtra(ScheduledActionScheduler.EXTRA_TITLE).orEmpty(),
                reason = intent.getStringExtra(ScheduledActionScheduler.EXTRA_REASON).orEmpty(),
            )

            ScheduledActionNotifier.ACTION_DONE -> ScheduledActionNotifier.onDone(
                context = context,
                eventId = intent.getStringExtra(ScheduledActionNotifier.EXTRA_EVENT_ID).orEmpty(),
                signature = intent.getStringExtra(ScheduledActionNotifier.EXTRA_SIGNATURE).orEmpty(),
            )

            ScheduledActionScheduler.ACTION_REARM,
            Intent.ACTION_BOOT_COMPLETED,
            -> ScheduledActionScheduler.rearm(context)
        }
    }
}
