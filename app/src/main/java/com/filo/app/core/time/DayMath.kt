package com.filo.app.core.time

import android.content.Context
import android.text.format.DateUtils
import com.filo.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.text.NumberFormat
import java.util.Locale

/**
 * Day counts are done on whole dates in the viewer's own timezone with ChronoUnit.DAYS,
 * never with millisecond arithmetic, so a countdown never reads one off because of DST or
 * because someone opened the app at 23:58.
 */
object DayMath {

    fun daysUntil(target: LocalDate, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, target)

    fun daysBetween(start: LocalDate, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(start, today)

    /** Today, Tomorrow, "12 days", or "12 days since" for dates that have passed. */
    fun countdownText(context: Context, target: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = daysUntil(target, today)
        return when {
            days == 0L -> context.getString(R.string.countdown_today)
            days == 1L -> context.getString(R.string.countdown_tomorrow)
            days > 0 -> "${number(days)} ${context.getString(R.string.countdown_days)}"
            else -> context.getString(R.string.countdown_since, number(-days))
        }
    }

    /** Just the numeral, for the big Fraunces display. Today and tomorrow have none. */
    fun countdownNumeral(target: LocalDate, today: LocalDate = LocalDate.now()): String? {
        val days = daysUntil(target, today)
        return if (days == 0L || days == 1L) null else number(kotlin.math.abs(days))
    }

    /**
     * The words that go under the numeral. Null when the numeral is null, because Today and
     * Tomorrow are already whole phrases on their own.
     */
    fun countdownUnit(context: Context, target: LocalDate, today: LocalDate = LocalDate.now()): String? {
        val days = daysUntil(target, today)
        return when {
            days == 0L || days == 1L -> null
            days > 0 -> context.getString(R.string.countdown_days)
            else -> context.getString(R.string.countdown_days_since)
        }
    }

    fun number(value: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    fun formatDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()))

    /** Explicit patterns rather than the locale default, because the clock format is a
     *  per person setting rather than something the locale gets to decide. */
    fun clockPattern(use24: Boolean): String = if (use24) "HH:mm" else "h:mm a"

    fun formatTime(instant: Instant, zone: ZoneId, use24: Boolean): String =
        DateTimeFormatter.ofPattern(clockPattern(use24), Locale.getDefault())
            .withZone(zone)
            .format(instant)

    /**
     * Relative spans go through DateUtils, which is already translated for every locale
     * Android ships and follows the app language once AppCompat has set it.
     */
    fun relative(instant: Instant?): CharSequence? {
        if (instant == null) return null
        return DateUtils.getRelativeTimeSpanString(
            instant.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
    }
}
