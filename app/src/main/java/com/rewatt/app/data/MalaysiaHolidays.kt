package com.rewatt.app.data

import android.content.Context
import android.content.SharedPreferences
import com.rewatt.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object MalaysiaHolidays {

    private const val PREFS_NAME = "malaysia_holidays"
    private const val KEY_DATES = "dates"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val CACHE_TTL_MS = 7L * 24 * 3600 * 1000 // 7 days

    // Google public calendar ID for Malaysian public holidays
    private const val CALENDAR_ID = "en.malaysia#holiday@group.v.calendar.google.com"

    // Minimal fallback — used only when network and disk cache both miss
    private val fallback: Set<String> = setOf(
        "20250101", "20250129", "20250130", "20250201", "20250331",
        "20250401", "20250501", "20250512", "20250606", "20250831",
        "20250916", "20251225",
        "20260101", "20260201", "20260217", "20260218", "20260320",
        "20260321", "20260501", "20260527", "20260531", "20260831",
        "20260916", "20261225",
    )

    @Volatile private var cache: Set<String> = fallback

    fun isHoliday(year: Int, month: Int, dayOfMonth: Int): Boolean {
        val key = String.format("%04d%02d%02d", year, month, dayOfMonth)
        return key in cache
    }

    fun isHoliday(cal: Calendar): Boolean =
        isHoliday(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )

    /**
     * Called at the start of ClassScheduleWorker.doWork().
     * Loads the disk cache immediately, then hits the network if the cache is stale (> 7 days old).
     */
    suspend fun refresh(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Always load disk cache first so isHoliday() is immediately accurate
        val stored = prefs.getStringSet(KEY_DATES, null)
        if (stored != null) cache = stored.toHashSet()

        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        val now = System.currentTimeMillis()
        if (stored != null && now - fetchedAt < CACHE_TTL_MS) return // cache still fresh

        // Fetch fresh data from Google Calendar API
        try {
            val fetched = fetchFromGoogleCalendar()
            if (fetched.isNotEmpty()) {
                cache = fetched
                prefs.edit()
                    .putStringSet(KEY_DATES, fetched)
                    .putLong(KEY_FETCHED_AT, now)
                    .apply()
            }
        } catch (_: Exception) {
            // Network failure — keep whatever is in cache (or fallback); don't crash the worker
        }
    }

    private suspend fun fetchFromGoogleCalendar(): Set<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.MAPS_WEATHER_API_KEY
        if (apiKey.isBlank()) return@withContext emptySet()

        val calendarId = URLEncoder.encode(CALENDAR_ID, "UTF-8")
        val timeMin = URLEncoder.encode("2025-01-01T00:00:00Z", "UTF-8")
        val timeMax = URLEncoder.encode("2027-12-31T23:59:59Z", "UTF-8")

        val urlStr = "https://www.googleapis.com/calendar/v3/calendars/$calendarId/events" +
            "?key=${URLEncoder.encode(apiKey, "UTF-8")}" +
            "&timeMin=$timeMin" +
            "&timeMax=$timeMax" +
            "&singleEvents=true" +
            "&fields=${URLEncoder.encode("items(start,summary)", "UTF-8")}"

        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) return@withContext emptySet()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseCalendarResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCalendarResponse(json: String): Set<String> {
        val result = mutableSetOf<String>()
        val items = JSONObject(json).optJSONArray("items") ?: return result
        for (i in 0 until items.length()) {
            // All-day events have start.date ("YYYY-MM-DD"), not start.dateTime
            val date = items.getJSONObject(i)
                .optJSONObject("start")
                ?.optString("date")
                ?: continue
            if (date.length == 10) result.add(date.replace("-", "")) // "YYYYMMDD"
        }
        return result
    }
}
