package com.rewatt.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rewatt.app.MainActivity
import com.rewatt.app.R
import com.rewatt.app.ReWattApplication
import com.rewatt.app.data.UserRole
import java.util.Calendar

/**
 * Posts a single Today's-Schedule row as a **persistent, non-swipeable notification with a Done button**
 * when its exact-time alarm fires (see [ScheduledActionScheduler]). Title shows the exact action + space
 * (e.g. "DK1 — Turn off AC & lights"); the body is the reason. Done clears it and is tracked the same way
 * as the NOW action (local marker + Firestore `nowActionAcks`). Admin phones only.
 */
object ScheduledActionNotifier {
    const val ACTION_DONE = "com.rewatt.app.action.SCHEDULED_DONE"
    const val EXTRA_EVENT_ID = "scheduled_event_id"
    const val EXTRA_SIGNATURE = "scheduled_signature"

    private const val CHANNEL_ID = "rewatt_scheduled_action"
    private const val PREFS = "rewatt_scheduled_actions"

    /** Show the fired event. [reason] is "SPACE — description"; [title] is the bare action. */
    fun show(context: Context, eventId: String, title: String, reason: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val mode = context
            .getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
            .getString("APP_MODE", null)
        if (mode != UserRole.Admin.name) return
        if (prefs(context).getBoolean(doneKey(eventId), false)) return

        val space = reason.substringBefore(" — ", missingDelimiterValue = "")
        val description = reason.substringAfter(" — ", reason)
        val displayTitle = if (space.isNotEmpty()) "$space — $title" else title
        val signature = displayTitle

        ensureChannel(context, nm)
        val notificationId = eventId.hashCode()
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, ScheduledActionReceiver::class.java)
                .setAction(ACTION_DONE)
                .setPackage(context.packageName)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_SIGNATURE, signature),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rewatt)
            .setContentTitle(displayTitle)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentPendingIntent)
            // Stay pinned until Done, like the NOW-action notification.
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.energy_alert_done), donePendingIntent)
            .build()
        notification.flags = notification.flags or
            android.app.Notification.FLAG_ONGOING_EVENT or
            android.app.Notification.FLAG_NO_CLEAR
        nm.notify(notificationId, notification)
    }

    /** Done: mark this instance done (so a re-arm won't re-show it today), track it, and clear it. */
    fun onDone(context: Context, eventId: String, signature: String) {
        prefs(context).edit().putBoolean(doneKey(eventId), true).apply()
        runCatching {
            (context.applicationContext as ReWattApplication)
                .appGraph.telemetryRepository.recordNowActionDone(signature)
        }
        context.getSystemService(NotificationManager::class.java).cancel(eventId.hashCode())
    }

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.scheduled_action_notif_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /** Done marker keyed by event id + today's date, so the same row fires fresh on later days. */
    private fun doneKey(eventId: String): String {
        val cal = Calendar.getInstance()
        val date = "%04d%02d%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        return "done:$date:$eventId"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
