package com.rewatt.app.notify

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rewatt.app.R
import com.rewatt.app.data.UserRole

/**
 * Drives the full-screen "lights still on, no people in the room" alert via [EnergyAlertService].
 *
 * The alert is **not** triggered by raw sensor detection anymore. It **escalates** from the NOW-action
 * recommendation: when a *Turn off AC & lights / Turn off all systems* (empty-room) recommendation is
 * left un-Done for too long (see `DashboardViewModel.notifyNowActions`), [onNowEscalation] raises this
 * alert showing the exact action and space. **Done** acknowledges it (tracked via [NowActionNotifier])
 * and clears both the alert and the persistent notification; **Snooze** suppresses it for
 * [SNOOZE_DURATION_MILLIS]. Only Admin phones show it.
 */
object EnergyAlertNotifier {
    const val ACTION_DONE = "com.rewatt.app.action.ENERGY_DONE"
    const val ACTION_SNOOZE = "com.rewatt.app.action.ENERGY_SNOOZE"

    /** How long Snooze suppresses new alerts. */
    const val SNOOZE_DURATION_MILLIS = 5 * 60 * 1000L

    internal const val PREFS_NAME = "rewatt_energy_alert"
    internal const val KEY_ALERT_TITLE = "alert_title"
    internal const val KEY_ALERT_TEXT = "alert_text"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_millis"
    private const val KEY_ESC_SIGNATURE = "escalation_signature"

    @Volatile
    private var showing = false
    @Volatile
    private var shownText: String? = null
    // While a long-press demo alert is up, the per-tick escalation loop must not clear it.
    @Volatile
    private var demoActive = false

    /** Demo (fsktm): raise the full-screen "Lights still on with 0 occupancy" alert for [hallLabel]. */
    fun showDemoLightsAlert(context: Context, hallLabel: String) {
        val mode = context
            .getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
            .getString("APP_MODE", null)
        if (mode != UserRole.Admin.name) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ALERT_TITLE, "Lights still on with 0 occupancy")
            .putString(KEY_ALERT_TEXT, "Space: $hallLabel")
            .putString(KEY_ESC_SIGNATURE, "")   // Done simply dismisses (no NOW-action ack)
            .apply()
        demoActive = true
        showing = true
        shownText = "Space: $hallLabel"
        show(context)
        // Force the full-screen page (with Done/Snooze) to appear even when the app is in the
        // foreground — otherwise the full-screen intent only shows a heads-up while unlocked.
        runCatching {
            context.startActivity(
                Intent(context, EnergyAlertActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    /**
     * Escalation entry point (Admin): [active] true => raise the full-screen alert showing [actions]
     * (one "Space — exact action" line each); false => clear it. [signature] identifies the underlying
     * recommendation so Done can acknowledge it. Sensor phones stay silent; an active Snooze suppresses.
     * Safe to call every tick — it only starts/stops the service on a real transition.
     */
    fun onNowEscalation(context: Context, active: Boolean, actions: List<String>, signature: String) {
        // The demo alert owns the screen until the user dismisses it.
        if (demoActive) return
        val mode = context
            .getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
            .getString("APP_MODE", null)
        val shouldShow = active && actions.isNotEmpty() &&
            mode == UserRole.Admin.name && !isSnoozed(context)

        if (!shouldShow) {
            if (showing) {
                showing = false
                shownText = null
                stop(context)
            }
            return
        }

        val text = actions.joinToString("\n")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ALERT_TITLE, context.getString(R.string.energy_alert_title))
            .putString(KEY_ALERT_TEXT, text)
            .putString(KEY_ESC_SIGNATURE, signature)
            .apply()
        if (!showing || shownText != text) {
            showing = true
            shownText = text
            show(context)
        }
    }

    fun onDone(context: Context) {
        // Acknowledge the escalated NOW action so it's tracked (local + Firestore) and won't re-fire,
        // and clear the persistent NOW-action notification too.
        val signature = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ESC_SIGNATURE, null)
        if (!signature.isNullOrEmpty()) NowActionNotifier.onDone(context, signature)
        demoActive = false
        showing = false
        shownText = null
        stop(context)
    }

    fun onSnooze(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_DURATION_MILLIS)
            .apply()
        demoActive = false
        showing = false
        shownText = null
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
