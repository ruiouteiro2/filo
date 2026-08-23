package com.filo.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * PostgREST hands back `time` as 23:30:00, `date` as 2026-09-14 and `timestamptz` as an
 * ISO string with an offset. Everything crossing that boundary is parsed here so no screen
 * ever does string surgery on a date.
 */
object PgTime {

    fun localTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(raw.take(8)) }.getOrNull()
    }

    fun localDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    fun instant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
    }

    /** Always resolve with the stored IANA id. Never store or compare raw UTC offsets. */
    fun zone(id: String?): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("UTC"))

    fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
}
