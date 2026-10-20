package com.rewatt.app.security

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rewatt.app.MainActivity
import com.rewatt.app.R
import com.rewatt.app.core.LocaleManager
import com.rewatt.app.perception.CameraCaptureService
import java.util.Locale

/** Full-screen "Intruder alert" notification (wakes the screen over the lock screen) + a resume hint. */
class IntrusionNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val text = localizedContext(appContext)
        manager.createNotificationChannel(
            NotificationChannel(INTRUSION_CHANNEL, text.getString(R.string.cctv_channel_intrusion), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // the siren is played separately on the alarm stream
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, text.getString(R.string.cctv_channel_status), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** True when Android lets this app show full-screen alerts (Android 14+ makes it a user toggle). */
    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

    fun showIntrusion(eventId: String, roomId: String, photoName: String?, score: Float?) {
        ensureChannels()
        val text = localizedContext(appContext)
        val alertIntent = IntrusionAlertActivity.intent(appContext, eventId, roomId, photoName, score)
        val fullScreen = PendingIntent.getActivity(
            appContext,
            REQUEST_ALERT,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getService(
            appContext,
            REQUEST_DISMISS,
            CameraCaptureService.dismissAlarmIntent(appContext),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, INTRUSION_CHANNEL)
            .setSmallIcon(R.drawable.ic_rewatt)
            .setContentTitle(text.getString(R.string.cctv_alert_title))
            .setContentText(text.getString(R.string.cctv_alert_text, roomId))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, text.getString(R.string.cctv_dismiss), dismiss)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(INTRUSION_NOTIFICATION_ID, notification) }

        // If the app is in the foreground Android allows a direct launch; otherwise the full-screen
        // intent above wakes the screen. Either way the red alert appears.
        runCatching { appContext.startActivity(alertIntent) }
    }

    fun cancelIntrusion() {
        manager.cancel(INTRUSION_NOTIFICATION_ID)
    }

    /** Android blocked the camera/microphone from the background (e.g. after a restart). */
    fun showResumeHint() {
        ensureChannels()
        val text = localizedContext(appContext)
        val open = PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_rewatt)
            .setContentTitle(text.getString(R.string.cctv_resume_title))
            .setContentText(text.getString(R.string.cctv_resume_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(RESUME_NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val INTRUSION_CHANNEL = "rewatt_intrusion"
        private const val STATUS_CHANNEL = "rewatt_cctv_status"
        private const val INTRUSION_NOTIFICATION_ID = 91
        private const val RESUME_NOTIFICATION_ID = 92
        private const val REQUEST_ALERT = 90
        private const val REQUEST_DISMISS = 93
        private const val REQUEST_OPEN = 94
    }
}

/** Services don't get AppCompat's per-app locale on older Android, so wrap the context explicitly. */
internal fun localizedContext(context: Context, languageTag: String = LocaleManager.currentTag()): Context {
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale(languageTag)) }
    return context.createConfigurationContext(config)
}

internal fun localizedString(context: Context, @StringRes id: Int, languageTag: String): String =
    localizedContext(context, languageTag).getString(id)
