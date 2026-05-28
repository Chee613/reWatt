package com.rewatt.app.perception

import java.util.Calendar

/**
 * Milliseconds until the next wall-clock boundary that is a multiple of [intervalMinutes]
 * past the hour — e.g. for 10 this returns the time until the next :00 / :10 / :20 mark.
 *
 * Uses the device's local time zone (via [Calendar]) rather than epoch modulo, so it stays
 * correct for time zones with :30 / :45 offsets.
 */
internal fun millisUntilNextBoundary(
    intervalMinutes: Int,
    calendar: Calendar = Calendar.getInstance(),
): Long {
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val millis = calendar.get(Calendar.MILLISECOND)
    val minutesToNext = intervalMinutes - (minute % intervalMinutes)
    val delay = minutesToNext * 60_000L - second * 1_000L - millis
    return delay.coerceAtLeast(0L)
}
