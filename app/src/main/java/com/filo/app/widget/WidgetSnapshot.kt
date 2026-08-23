package com.filo.app.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.filo.app.core.geo.DistanceState
import com.filo.app.core.prefs.PrefKeys
import com.filo.app.core.prefs.filoDataStore
import com.filo.app.core.time.PgTime
import com.filo.app.core.time.SleepMath
import com.filo.app.data.model.CoupleSnapshot
import com.filo.app.data.weather.Weather
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The single JSON blob the widgets render from. Widgets never touch the network and never
 * touch Supabase: the app and the worker write this, the widgets read it and stop.
 */
@Serializable
data class WidgetSnapshot(
    val paired: Boolean = false,
    val partnerName: String? = null,
    val partnerTimezone: String? = null,
    /**
     * The window itself, not a verdict about it. Falling asleep changes no database row, so
     * a stored "awake" had nothing to correct it and could stand all night.
     */
    val partnerSleepStart: String? = null,
    val partnerSleepEnd: String? = null,
    val partnerMoodEmoji: String? = null,
    val partnerMoodText: String? = null,
    val partnerNote: String? = null,
    val partnerBattery: Int? = null,
    val partnerCharging: Boolean = false,
    val weatherCode: Int? = null,
    val weatherTemp: Int? = null,
    val distanceKm: Long? = null,
    val distanceKnown: Boolean = false,
    val countdownLabelEn: String? = null,
    val countdownLabelIt: String? = null,
    val countdownDate: String? = null,
    val countdownEmoji: String? = null,
    val partnerTrack: String? = null,
    val partnerArtist: String? = null,
    val partnerTrackId: String? = null,
    /**
     * What the row claimed and when it was written. The verdict is deliberately NOT stored:
     * "is playing" decays with time, and a boolean frozen at write time is exactly how the
     * widget ended up insisting someone was still listening long after they had stopped.
     */
    val partnerMusicPlaying: Boolean = false,
    val partnerMusicAt: Long = 0L,
    /** Files under the app's own storage, written by the worker. */
    val partnerAvatar: String? = null,
    val photoImage: String? = null,
    val locale: String = "en",
    val clock24h: Boolean = true,
    val updatedAt: Long = 0L,
) {
    fun countdownLabel(): String? = if (locale == "it") countdownLabelIt else countdownLabelEn

    /**
     * Judged now, at render time, exactly as the app judges it. The phone that is playing
     * writes a heartbeat every minute; silence for longer than this means the music stopped
     * with nobody left to say so.
     */
    /** Asked now, the way the app asks it, rather than remembered from a past sync. */
    fun partnerAsleep(): Boolean? = SleepMath.isAsleep(
        Instant.now().atZone(PgTime.zone(partnerTimezone)).toLocalTime(),
        PgTime.localTime(partnerSleepStart),
        PgTime.localTime(partnerSleepEnd),
    )

    fun partnerMusicLive(now: Long = System.currentTimeMillis()): Boolean =
        partnerMusicPlaying && partnerMusicAt > 0L && now - partnerMusicAt < MUSIC_STALE_MS
}

object WidgetSnapshotStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * The snapshot as a Flow, for the widgets themselves. A Glance session lives on between
     * updates and recomposes with whatever values the composition holds - a one-shot read
     * frozen at session start is exactly why the widgets kept old data while the app moved
     * on. Collected in composition, every DataStore write recomposes the widget by itself.
     */
    fun flow(context: Context): kotlinx.coroutines.flow.Flow<WidgetSnapshot> =
        context.filoDataStore.data.map { prefs ->
            prefs[PrefKeys.WidgetSnapshot]?.let { raw ->
                runCatching { json.decodeFromString(WidgetSnapshot.serializer(), raw) }.getOrNull()
            } ?: WidgetSnapshot()
        }

    suspend fun read(context: Context): WidgetSnapshot {
        val raw = context.filoDataStore.data.first()[PrefKeys.WidgetSnapshot] ?: return WidgetSnapshot()
        return runCatching { json.decodeFromString(WidgetSnapshot.serializer(), raw) }
            .getOrDefault(WidgetSnapshot())
    }

    suspend fun write(context: Context, snapshot: WidgetSnapshot) {
        context.filoDataStore.edit {
            it[PrefKeys.WidgetSnapshot] = json.encodeToString(WidgetSnapshot.serializer(), snapshot)
        }
    }

    /** Folds everything the app knows into the shape the widgets want. */
    fun build(
        couple: CoupleSnapshot,
        weather: Weather?,
        distance: DistanceState,
        locale: String,
        clock24h: Boolean,
        avatarImage: String?,
        photoImage: String?,
        /** Last known weather, used when this round's fetch came back empty. */
        fallbackWeatherCode: Int? = null,
        fallbackWeatherTemp: Int? = null,
    ): WidgetSnapshot {
        val partner = couple.partner
        val primary = couple.primaryCountdown
        return WidgetSnapshot(
            paired = couple.me != null,
            partnerName = partner?.displayName,
            partnerTimezone = partner?.timezone,
            partnerSleepStart = partner?.sleepStart,
            partnerSleepEnd = partner?.sleepEnd,
            partnerMoodEmoji = partner?.moodEmoji?.takeIf { it.isNotBlank() },
            partnerMoodText = partner?.moodText?.takeIf { it.isNotBlank() },
            partnerNote = partner?.noteText?.takeIf { it.isNotBlank() },
            partnerBattery = partner?.batteryLevel,
            partnerCharging = partner?.batteryCharging == true,
            weatherCode = weather?.code ?: fallbackWeatherCode,
            weatherTemp = weather?.temperatureC ?: fallbackWeatherTemp,
            distanceKm = (distance as? DistanceState.Known)?.km,
            distanceKnown = distance is DistanceState.Known,
            countdownLabelEn = primary?.labelEn,
            countdownLabelIt = primary?.labelIt,
            countdownDate = primary?.date,
            countdownEmoji = primary?.emoji?.takeIf { it.isNotBlank() },
            partnerTrack = partner?.spotifyTrackName?.takeIf { it.isNotBlank() },
            partnerArtist = partner?.spotifyArtist?.takeIf { it.isNotBlank() },
            partnerTrackId = partner?.spotifyTrackId?.takeIf { it.isNotBlank() },
            partnerMusicPlaying = partner?.spotifyIsPlaying == true,
            partnerMusicAt = PgTime.instant(partner?.spotifyUpdatedAt)?.toEpochMilli() ?: 0L,
            partnerAvatar = avatarImage,
            photoImage = photoImage,
            locale = locale,
            clock24h = clock24h,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

/** Three missed heartbeats, the same window the app uses. */
const val MUSIC_STALE_MS = 200_000L
