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
    val partnerAsleep: Boolean? = null,
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
    /** Files under the app's own storage, written by the worker. */
    val ringImage: String? = null,
    val photoImage: String? = null,
    val locale: String = "en",
    val clock24h: Boolean = true,
    val updatedAt: Long = 0L,
) {
    fun countdownLabel(): String? = if (locale == "it") countdownLabelIt else countdownLabelEn
}

object WidgetSnapshotStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        ringImage: String?,
        photoImage: String?,
    ): WidgetSnapshot {
        val partner = couple.partner
        val zone = PgTime.zone(partner?.timezone)
        val asleep = partner?.let {
            SleepMath.isAsleep(
                Instant.now().atZone(zone).toLocalTime(),
                PgTime.localTime(it.sleepStart),
                PgTime.localTime(it.sleepEnd),
            )
        }
        val primary = couple.primaryCountdown
        return WidgetSnapshot(
            paired = couple.me != null,
            partnerName = partner?.displayName,
            partnerTimezone = partner?.timezone,
            partnerAsleep = asleep,
            partnerMoodEmoji = partner?.moodEmoji?.takeIf { it.isNotBlank() },
            partnerMoodText = partner?.moodText?.takeIf { it.isNotBlank() },
            partnerNote = partner?.noteText?.takeIf { it.isNotBlank() },
            partnerBattery = partner?.batteryLevel,
            partnerCharging = partner?.batteryCharging == true,
            weatherCode = weather?.code,
            weatherTemp = weather?.temperatureC,
            distanceKm = (distance as? DistanceState.Known)?.km,
            distanceKnown = distance is DistanceState.Known,
            countdownLabelEn = primary?.labelEn,
            countdownLabelIt = primary?.labelIt,
            countdownDate = primary?.date,
            countdownEmoji = primary?.emoji?.takeIf { it.isNotBlank() },
            ringImage = ringImage,
            photoImage = photoImage,
            locale = locale,
            clock24h = clock24h,
            updatedAt = System.currentTimeMillis(),
        )
    }
}
