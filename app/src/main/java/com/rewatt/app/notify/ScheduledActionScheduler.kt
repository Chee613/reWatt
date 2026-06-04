package com.rewatt.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rewatt.app.data.UserRole
import com.rewatt.app.ui.dashboard.buildAllHallsSessionNotifications
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

/**
 * Arms one exact AlarmManager alarm per **future Today's-Schedule row**, computed from the very same
 * [buildAllHallsSessionNotifications] that renders the Action-tab timeline — so each notification fires
 * at exactly the time shown in the row's purple box, even when the app is closed. Re-armed daily (00:05)
 * and on boot. Admin phones only.
 *
 * The per-hall minute schedule (needed to rebuild the timeline in the background) is cached here by the
 * dashboard; when absent the timeline falls back to the hardcoded class schedule (fsktm demo), so the
 * scheduled times still match what's on screen.
 */
object ScheduledActionScheduler {
    const val ACTION_FIRE = "com.rewatt.app.action.SCHEDULED_FIRE"
    const val ACTION_REARM = "com.rewatt.app.action.SCHEDULED_REARM"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_TITLE = "event_title"
    const val EXTRA_REASON = "event_reason"

    private const val PREFS = "rewatt_scheduled_actions"
    private const val KEY_CACHE = "schedule_cache"
    private const val KEY_ARMED_COUNT = "armed_count"
    private const val FIRE_CODE_BASE = 6000
    private const val REARM_CODE = 5999

    /** Dashboard entry point: cache the latest per-hall schedule, then (re)arm today's alarms. */
    fun updateSchedules(context: Context, grids: Map<String, Array<IntArray>>) {
        persist(context, grids)
        rearm(context)
    }

    /** (Re)arm exact alarms for every future timeline row today + the daily re-arm. Admin only. */
    fun rearm(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(AlarmManager::class.java) ?: return
        cancelArmedFires(appContext, am)

        val mode = appContext
            .getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE)
            .getString("APP_MODE", null)
        if (mode != UserRole.Admin.name) {
            runCatching { am.cancel(rearmIntent(appContext)) }
            return
        }

        val now = System.currentTimeMillis()
        val events = buildAllHallsSessionNotifications(0, now, load(appContext))
        var armed = 0
        for (event in events) {
            if (event.timeLabel == "--") continue
            val parts = event.timeLabel.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: continue
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: continue
            val triggerAt = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (triggerAt <= now) continue
            setExact(am, triggerAt, firePendingIntent(appContext, FIRE_CODE_BASE + armed, event.id, event.title, event.reason))
            armed++
        }
        prefs(appContext).edit().putInt(KEY_ARMED_COUNT, armed).apply()

        // Re-arm at 00:05 tomorrow so the next day's rows get scheduled even with the app closed.
        val rearmAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        setExact(am, rearmAt, rearmIntent(appContext))
    }

    private fun setExact(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
            .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
    }

    private fun cancelArmedFires(context: Context, am: AlarmManager) {
        val count = prefs(context).getInt(KEY_ARMED_COUNT, 0)
        for (i in 0 until count) {
            // Matching is by request code + action/component; extras are ignored, so a bare intent cancels.
            am.cancel(firePendingIntent(context, FIRE_CODE_BASE + i, "", "", ""))
        }
    }

    private fun firePendingIntent(
        context: Context,
        code: Int,
        eventId: String,
        title: String,
        reason: String,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, ScheduledActionReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setPackage(context.packageName)
            .putExtra(EXTRA_EVENT_ID, eventId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_REASON, reason),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun rearmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REARM_CODE,
        Intent(context, ScheduledActionReceiver::class.java)
            .setAction(ACTION_REARM)
            .setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ── Background-readable schedule cache ─────────────────────────────────────
    private fun persist(context: Context, grids: Map<String, Array<IntArray>>) {
        if (grids.isEmpty() || grids.values.none { g -> g.any { row -> row.any { it > 0 } } }) return
        val root = JSONObject()
        for ((hall, grid) in grids) {
            val days = JSONArray()
            for (day in grid) {
                val arr = JSONArray()
                for (v in day) arr.put(v)
                days.put(arr)
            }
            root.put(hall, days)
        }
        prefs(context).edit().putString(KEY_CACHE, root.toString()).apply()
    }

    private fun load(context: Context): Map<String, Array<IntArray>> {
        val raw = prefs(context).getString(KEY_CACHE, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val map = mutableMapOf<String, Array<IntArray>>()
            for (hall in root.keys()) {
                val days = root.getJSONArray(hall)
                map[hall] = Array(days.length()) { d ->
                    val a = days.getJSONArray(d)
                    IntArray(a.length()) { a.getInt(it) }
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
