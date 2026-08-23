package com.filo.app.core.time

import android.content.Context
import com.filo.app.R
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Whether someone is awake or asleep, and until when.
 *
 * This exists because a picture of a day is not an answer. "What are these rings exactly" is
 * what happens when a graphic is asked to carry the whole meaning, so the state is computed
 * here as a sentence first and only then decorated.
 */
sealed interface DayState {
    /** Awake now; asleep at [until]. */
    data class Awake(val until: LocalTime?, val remaining: Duration?) : DayState

    /** Asleep now; awake at [until]. */
    data class Asleep(val until: LocalTime, val remaining: Duration) : DayState

    /** They have not told us their hours. */
    data object Unknown : DayState
}

object DayStates {

    fun of(now: ZonedDateTime, sleepStart: LocalTime?, sleepEnd: LocalTime?): DayState {
        val localNow = now.toLocalTime()
        return when (SleepMath.isAsleep(localNow, sleepStart, sleepEnd)) {
            null -> DayState.Unknown
            true -> DayState.Asleep(sleepEnd!!, untilNext(localNow, sleepEnd))
            false -> DayState.Awake(sleepStart, sleepStart?.let { untilNext(localNow, it) })
        }
    }

    /** Always forwards in time, wrapping past midnight. */
    private fun untilNext(from: LocalTime, to: LocalTime): Duration {
        val minutes = Math.floorMod(
            (to.hour * 60 + to.minute) - (from.hour * 60 + from.minute),
            SleepMath.MINUTES_PER_DAY,
        )
        return Duration.ofMinutes(minutes.toLong())
    }

    /**
     * The one line that has to survive a one second glance, e.g. "Asleep until 07:15" or
     * "Awake, to bed in 6h".
     */
    fun sentence(context: Context, state: DayState, use24h: Boolean): String = when (state) {
        is DayState.Asleep ->
            context.getString(R.string.day_state_asleep_until, formatClock(state.until, use24h))

        is DayState.Awake -> when {
            state.remaining == null -> context.getString(R.string.state_awake)
            state.remaining < Duration.ofHours(3) ->
                context.getString(R.string.day_state_awake_until, formatClock(state.until!!, use24h))
            else -> context.getString(R.string.state_awake)
        }

        DayState.Unknown -> context.getString(R.string.day_state_unknown)
    }

    /** Short label for a badge or pill: two words at most. */
    fun pill(context: Context, state: DayState): String = when (state) {
        is DayState.Asleep -> context.getString(R.string.state_asleep)
        is DayState.Awake -> context.getString(R.string.state_awake)
        DayState.Unknown -> context.getString(R.string.day_state_unknown_short)
    }

    fun isAwake(state: DayState): Boolean = state is DayState.Awake

    private fun formatClock(time: LocalTime, use24h: Boolean): String =
        if (use24h) {
            "%02d:%02d".format(time.hour, time.minute)
        } else {
            val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
            val suffix = if (time.hour < 12) "AM" else "PM"
            "%d:%02d %s".format(hour12, time.minute, suffix)
        }
}
