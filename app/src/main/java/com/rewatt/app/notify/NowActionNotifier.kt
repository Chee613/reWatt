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

/**
 * Posts the per-hall "NOW action" recommendation (turn off AC/lights, pre-cool, raise setpoint) as a
 * dismissible notification on **Admin** phones with a **Done** button. Every admin device computes the
 * action locally from the shared Firestore schedule + the sensor's relayed live state, then calls
 * [update] — so the same notification appears on all admin phones without a separate publish path.
 *
 * **Done** marks the current recommendation as acknowledged: its [signature] is stored locally (so the
 * same action is never re-shown until it changes) and an acknowledgement is logged to Firestore so the
 * system tracks whether each recommended action was actually carried out.
 *
 * Unlike [EnergyAlertNotifier] this is NOT full-screen: NOW actions happen at every break, so they use
 * an ordinary heads-up notification. Sensor phones stay silent.
 */
object NowActionNotifier {
    const val ACTION_DONE = "com.rewatt.app.action.NOW_DONE"
    const val ACTION_DEMO_DISMISS = "com.rewatt.app.action.NOW_DEMO_DISMISS"
    const val EXTRA_SIGNATURE = "now_action_signature"

    private const val CHANNEL_ID = "rewatt_now_action"
    private const val NOTIFICATION_ID = 96
    private const val DEMO_NOTIFICATION_ID = 95
    private const val DONE_REQUEST_CODE = 97
    private const val CONTENT_REQUEST_CODE = 98
    private const val DEMO_DONE_REQUEST_CODE = 99
    private const val DEMO_SNOOZE_REQUEST_CODE = 100
    private const val PREFS = "rewatt_now_action"
    private const val KEY_ACK_SIGNATURE = "acked_signature"
    private const val KEY_ACK_AT = "acked_at_millis"

    /**
     * Demo (fsktm): post a usual-style action notification with Done + Snooze for [hallLabel], reading
     * "Turn off all systems / No more class today". Both buttons dismiss it. Admin phones only.
     */
    fun showDemoTurnOff(context: Context, hallLabel: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val mode = context
            .getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
            .getString("APP_MODE", null)
        if (mode != UserRole.Admin.name) return
        ensureChannel(context, nm)

        val contentIntent = PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun dismissIntent(code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, NowActionReceiver::class.java)
                .setAction(ACTION_DEMO_DISMISS)
                .setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rewatt)
            .setContentTitle("$hallLabel — Turn off all systems")
            .setContentText("No more class today")
            .setStyle(NotificationCompat.BigTextStyle().bigText("No more class today"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.energy_alert_done), dismissIntent(DEMO_DONE_REQUEST_CODE))
            .addAction(0, context.getString(R.string.energy_alert_snooze), dismissIntent(DEMO_SNOOZE_REQUEST_CODE))
            .build()
        notification.flags = notification.flags or
            android.app.Notification.FLAG_ONGOING_EVENT or
            android.app.Notification.FLAG_NO_CLEAR
        nm.notify(DEMO_NOTIFICATION_ID, notification)
    }

    /** Done/Snooze pressed on the demo notification → clear it. */
    fun onDemoDismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(DEMO_NOTIFICATION_ID)
    }

    /**
     * [lines] is one short string per hall with an actionable recommendation (e.g.
     * "DK1 — Turn off AC & lights"). Empty list clears the notification. A recommendation already
     * acknowledged via Done is suppressed until its set of actions changes.
     */
    fun update(context: Context, lines: List<String>) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val mode = context
            .getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
            .getString("APP_MODE", null)
        // Only Admin phones surface NOW-action notifications; clear otherwise (Sensor / unset).
        if (mode != UserRole.Admin.name || lines.isEmpty()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        val signature = lines.joinToString("|")
        // Already acted on (Done pressed) and unchanged → stay cleared, don't nag again.
        if (prefs(context).getString(KEY_ACK_SIGNATURE, null) == signature) return

        ensureChannel(context, nm)
        val contentIntent = PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            DONE_REQUEST_CODE,
            Intent(context, NowActionReceiver::class.java)
                .setAction(ACTION_DONE)
                .setPackage(context.packageName)
                .putExtra(EXTRA_SIGNATURE, signature),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = lines.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rewatt)
            .setContentTitle(context.getString(R.string.now_action_notif_title))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(contentIntent)
            // Stay pinned in the shade (non-swipeable) until the user taps Done.
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.energy_alert_done), donePendingIntent)
            .build()
        // Extra guard so the system / user swipe can't clear it before the action is acknowledged.
        notification.flags = notification.flags or
            android.app.Notification.FLAG_ONGOING_EVENT or
            android.app.Notification.FLAG_NO_CLEAR
        nm.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Done pressed: record that this recommendation was carried out (locally + Firestore) and clear
     * the notification. The stored [signature] suppresses re-notifying the same action.
     */
    fun onDone(context: Context, signature: String) {
        prefs(context).edit()
            .putString(KEY_ACK_SIGNATURE, signature)
            .putLong(KEY_ACK_AT, System.currentTimeMillis())
            .apply()
        runCatching {
            (context.applicationContext as ReWattApplication)
                .appGraph.telemetryRepository.recordNowActionDone(signature)
        }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** True if [signature] is the recommendation the user already marked Done. */
    fun isAcknowledged(context: Context, signature: String): Boolean =
        prefs(context).getString(KEY_ACK_SIGNATURE, null) == signature

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.now_action_notif_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }
}
