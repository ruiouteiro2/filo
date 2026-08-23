package com.filo.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Couple(
    val id: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("since_date") val sinceDate: String? = null,
)

@Serializable
data class Member(
    val id: String,
    @SerialName("couple_id") val coupleId: String? = null,
    @SerialName("display_name") val displayName: String,
    val locale: String = "en",
    val timezone: String = "UTC",
    @SerialName("photo_url") val photoUrl: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("location_updated_at") val locationUpdatedAt: String? = null,
    val city: String? = null,
    @SerialName("battery_level") val batteryLevel: Int? = null,
    @SerialName("battery_charging") val batteryCharging: Boolean? = null,
    @SerialName("battery_updated_at") val batteryUpdatedAt: String? = null,
    @SerialName("sleep_start") val sleepStart: String? = null,
    @SerialName("sleep_end") val sleepEnd: String? = null,
    @SerialName("mood_emoji") val moodEmoji: String? = null,
    @SerialName("mood_text") val moodText: String? = null,
    @SerialName("mood_updated_at") val moodUpdatedAt: String? = null,
    @SerialName("note_text") val noteText: String? = null,
    @SerialName("note_updated_at") val noteUpdatedAt: String? = null,
    @SerialName("daily_photo_url") val dailyPhotoUrl: String? = null,
    @SerialName("daily_photo_at") val dailyPhotoAt: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("location_accuracy_m") val locationAccuracyM: Double? = null,
    @SerialName("location_is_live") val locationIsLive: Boolean = false,
    @SerialName("spotify_track_id") val spotifyTrackId: String? = null,
    @SerialName("spotify_track_name") val spotifyTrackName: String? = null,
    @SerialName("spotify_artist") val spotifyArtist: String? = null,
    @SerialName("spotify_art_url") val spotifyArtUrl: String? = null,
    @SerialName("spotify_is_playing") val spotifyIsPlaying: Boolean? = null,
    @SerialName("spotify_updated_at") val spotifyUpdatedAt: String? = null,
) {
    /** Something is worth showing only if it is actually playing and recent. */
    /**
     * Live means the row says playing AND it was written recently. The phone that is
     * playing beats once a minute; if that stopped, the app died mid-track and the row is
     * a fossil - showing it as "listening now" is a lie the other person can see through.
     */
    val isNowPlayingLive: Boolean
        get() {
            if (spotifyIsPlaying != true) return false
            // PgTime, never Instant.parse: Postgres hands back "+00:00" and the bare
            // parser only accepts "Z", so this silently read as "never live" on the phones.
            val at = com.filo.app.core.time.PgTime.instant(spotifyUpdatedAt) ?: return false
            return at.isAfter(java.time.Instant.now().minusSeconds(NOW_PLAYING_STALE_SECONDS))
        }

    val hasNowPlaying: Boolean
        get() = isNowPlayingLive && !spotifyTrackName.isNullOrBlank()
}

@Serializable
data class Countdown(
    val id: String,
    @SerialName("couple_id") val coupleId: String? = null,
    @SerialName("label_en") val labelEn: String,
    @SerialName("label_it") val labelIt: String,
    val date: String,
    val emoji: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun label(locale: String): String = if (locale == "it") labelIt else labelEn
}

@Serializable
data class BucketItem(
    val id: String,
    @SerialName("couple_id") val coupleId: String? = null,
    val text: String,
    val done: Boolean = false,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** What create_couple and join_couple hand back. */
@Serializable
data class PairResult(
    @SerialName("couple_id") val coupleId: String,
    @SerialName("invite_code") val inviteCode: String,
)

/** Everything the home screen and the widgets need about the pair, in one shape. */
data class CoupleSnapshot(
    val me: Member? = null,
    val partner: Member? = null,
    val couple: Couple? = null,
    val countdowns: List<Countdown> = emptyList(),
    val bucket: List<BucketItem> = emptyList(),
) {
    val primaryCountdown: Countdown?
        get() = countdowns.firstOrNull { it.isPrimary } ?: countdowns.minByOrNull { it.date }
}

/** Three missed heartbeats: generous enough for a doze, short enough to be honest. */
private const val NOW_PLAYING_STALE_SECONDS = 200L
