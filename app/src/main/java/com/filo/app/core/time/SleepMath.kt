package com.filo.app.core.time

import java.time.LocalTime

/**
 * Sleep windows are stored as two local times in the member's own timezone and may cross
 * midnight (23:30 to 07:00). All reasoning about whether someone is asleep goes through here.
 */
object SleepMath {

    const val MINUTES_PER_DAY = 24 * 60

    private fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

    /** Null when we do not know their window yet. */
    fun isAsleep(now: LocalTime, start: LocalTime?, end: LocalTime?): Boolean? {
        if (start == null || end == null) return null
        val s = start.minuteOfDay()
        val e = end.minuteOfDay()
        if (s == e) return false // zero length window means they never declared one
        val n = now.minuteOfDay()
        return if (s < e) n >= s && n < e else n >= s || n < e
    }

    /** First minute of the waking span, and how many minutes it lasts. */
    fun wakingSpan(start: LocalTime?, end: LocalTime?): Pair<Int, Int> {
        if (start == null || end == null) return 0 to MINUTES_PER_DAY
        val s = start.minuteOfDay()
        val e = end.minuteOfDay()
        if (s == e) return 0 to MINUTES_PER_DAY
        // Waking runs from when they wake up (sleep end) until they go to sleep (sleep start).
        val span = Math.floorMod(s - e, MINUTES_PER_DAY)
        return e to span
    }
}
