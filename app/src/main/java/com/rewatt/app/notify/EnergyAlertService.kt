package com.rewatt.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rewatt.app.R

/**
 * Hosts the energy-waste prompt as a **foreground-service** notification with two layers:
 *
 * 1. **Full-screen intent** — immediately launches [EnergyAlertActivity] to demand user action.
 *    If the phone is unlocked this shows as a high-priority heads-up; if the phone is locked
 *    (or heads-up display is off), it opens the full-screen activity on top of the lock screen.
 *
 * 2. **Ongoing notification** — a persistent, non-swipeable notification in the drawer acts as
 *    a fallback. Even if the user manages to swipe it away (some OEMs allow this), the
 *    [ACTION_RESHOW] delete-intent immediately re-posts it.
 *
 * The only ways to clear both are the **Done** or **Snooze 5 min** actions, which stop this
 * service (removing the notification and finishing the activity).
 */
class EnergyAlertService : Service() {
    private var screenWakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESHOW -> {
                // The notification was swiped — re-post it immediately.
                repostNotification()
            }
            else -> {
                createChannel()
                wakeScreen()
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    foregroundType(),
                )
            }
        }
        return START_STICKY
    }

    /**
     * Force the screen on so the full-screen alert is visible even when the
     * device is asleep. Uses ACQUIRE_CAUSES_WAKEUP to turn the screen on
     * immediately. The wake lock is held until the service is stopped
     * (Done / Snooze pressed).
     */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        if (screenWakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        screenWakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE,
            WAKELOCK_TAG,
        ).apply {
            // Safety timeout: release after 5 minutes in case the service isn't stopped.
            acquire(5 * 60 * 1000L)
        }
    }

    /**
     * Re-post the foreground notification after it was dismissed. This keeps the notification
     * persistently visible until an action button is pressed.
     */
    private fun repostNotification() {
        createChannel()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
        // Also re-attach as the foreground notification so the service stays alive.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            foregroundType(),
        )
    }

    private fun buildNotification(): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            FULL_SCREEN_REQUEST_CODE,
            Intent(this, EnergyAlertActivity::class.java)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // When the user taps the notification body, open the full-screen alert activity.
        val contentIntent = PendingIntent.getActivity(
            this,
            CONTENT_REQUEST_CODE,
            Intent(this, EnergyAlertActivity::class.java)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // If the notification is somehow swiped, re-start the service to re-post it.
        val deleteIntent = PendingIntent.getService(
            this,
            DELETE_REQUEST_CODE,
            Intent(this, EnergyAlertService::class.java)
                .setAction(ACTION_RESHOW)
                .setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rewatt)
            .setContentTitle(getString(R.string.energy_alert_title))
            .setContentText(getString(R.string.energy_alert_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                0,
                getString(R.string.energy_alert_done),
                actionIntent(EnergyAlertNotifier.ACTION_DONE, 0),
            )
            .addAction(
                0,
                getString(R.string.energy_alert_snooze),
                actionIntent(EnergyAlertNotifier.ACTION_SNOOZE, 1),
            )
            .build()

        // Extra safeguard: FLAG_NO_CLEAR prevents the system from clearing the notification.
        notification.flags = notification.flags or
            Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_NO_CLEAR

        return notification
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(this, EnergyActionReceiver::class.java).setAction(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.energy_alert_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        if (screenWakeLock?.isHeld == true) {
            runCatching { screenWakeLock?.release() }
        }
        screenWakeLock = null
        super.onDestroy()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    companion object {
        const val CHANNEL_ID = "rewatt_energy_alert"
        private const val NOTIFICATION_ID = 91
        private const val FULL_SCREEN_REQUEST_CODE = 92
        private const val CONTENT_REQUEST_CODE = 93
        private const val DELETE_REQUEST_CODE = 94
        private const val ACTION_RESHOW = "com.rewatt.app.action.ENERGY_RESHOW"
        private const val WAKELOCK_TAG = "rewatt:energy_alert_screen"

        fun startIntent(context: Context): Intent = Intent(context, EnergyAlertService::class.java)
    }
}
