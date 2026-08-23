package com.filo.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.filo.app.core.battery.BatteryReader
import com.filo.app.core.geo.DistanceState
import com.filo.app.core.geo.Haversine
import com.filo.app.core.geo.LocationPolicy
import com.filo.app.core.geo.LocationReader
import com.filo.app.core.image.ImageTools
import com.filo.app.core.prefs.FiloPrefs
import com.filo.app.core.time.PgTime
import com.filo.app.data.model.BucketItem
import com.filo.app.data.model.Couple
import com.filo.app.data.model.CoupleSnapshot
import com.filo.app.data.model.Countdown
import com.filo.app.data.model.Member
import com.filo.app.data.model.PairResult
import com.filo.app.data.net.FiloSupabase
import com.filo.app.spotify.SpotifyApi
import com.filo.app.spotify.SpotifyAuth
import com.filo.app.data.weather.Weather
import com.filo.app.data.weather.WeatherRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val TAG = "FiloRepository"

/** What went wrong while pairing, in terms the UI can put a sentence to. */
enum class PairError { NoSuchCode, CoupleFull, Offline, Unknown }

class PairException(val error: PairError) : Exception(error.name)

/**
 * Everything that talks to Supabase. Screens observe [snapshot] and call the mutators; the
 * realtime subscription refetches on any change, which for two people and a handful of rows
 * is far simpler than merging deltas by hand and cannot drift out of sync.
 */
class FiloRepository(private val context: Context) {

    private val client: SupabaseClient = FiloSupabase.get(context)
    private val prefs = FiloPrefs(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val weatherRepo = WeatherRepository(context)
    private val locationReader = LocationReader(context)

    private val _snapshot = MutableStateFlow(CoupleSnapshot())
    val snapshot: StateFlow<CoupleSnapshot> = _snapshot.asStateFlow()

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var realtimeJob: Job? = null
    private var liveLocationJob: Job? = null

    // ------------------------------------------------------------------ auth

    /**
     * The SDK loads the stored session on a background coroutine, so asking for the current
     * user before that finishes reports "signed out" and would mint a second anonymous user,
     * silently orphaning the pairing. Everything that reads auth state waits on this first.
     */
    private suspend fun awaitAuthReady() {
        runCatching { client.auth.awaitInitialization() }
            .onFailure { Log.w(TAG, "auth initialisation failed", it) }
    }

    /** Signs in anonymously the first time and reuses that identity forever after. */
    suspend fun ensureSignedIn(): String? = withContext(Dispatchers.IO) {
        awaitAuthReady()
        runCatching {
            client.auth.currentSessionOrNull() ?: run {
                client.auth.signInAnonymously()
                client.auth.currentSessionOrNull()
            }
            client.auth.currentUserOrNull()?.id
        }.onFailure {
            Log.w(TAG, "anonymous sign in failed", it)
            _online.value = false
        }.getOrNull()
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    // --------------------------------------------------------------- pairing

    suspend fun createCouple(displayName: String, locale: String): PairResult =
        pairingRpc(
            "create_couple",
            buildJsonObject {
                put("p_display_name", displayName)
                put("p_locale", locale)
                put("p_timezone", ZoneId.systemDefault().id)
            },
            displayName,
            locale,
        )

    suspend fun joinCouple(code: String, displayName: String, locale: String): PairResult =
        pairingRpc(
            "join_couple",
            buildJsonObject {
                put("p_code", normaliseCode(code))
                put("p_display_name", displayName)
                put("p_locale", locale)
                put("p_timezone", ZoneId.systemDefault().id)
            },
            displayName,
            locale,
        )

    private suspend fun pairingRpc(
        function: String,
        params: kotlinx.serialization.json.JsonObject,
        displayName: String,
        locale: String,
    ): PairResult = withContext(Dispatchers.IO) {
        val uid = ensureSignedIn() ?: throw PairException(PairError.Offline)
        val result = try {
            client.postgrest.rpc(function, params).decodeList<PairResult>().firstOrNull()
                ?: throw PairException(PairError.Unknown)
        } catch (e: PairException) {
            throw e
        } catch (e: Exception) {
            throw PairException(classifyPairFailure(e))
        }
        prefs.savePairing(result.coupleId, uid, result.inviteCode, displayName, locale)
        refresh()
        startRealtime()
        result
    }

    private fun classifyPairFailure(e: Exception): PairError {
        val message = (e.message ?: "").lowercase()
        return when {
            "no_such_code" in message -> PairError.NoSuchCode
            "couple_full" in message -> PairError.CoupleFull
            "unable to resolve host" in message || "failed to connect" in message ||
                "timeout" in message || "network" in message -> PairError.Offline
            else -> {
                Log.w(TAG, "unclassified pairing failure", e)
                PairError.Unknown
            }
        }
    }

    /** Uppercase, strips anything that is not in the code alphabet. */
    fun normaliseCode(raw: String): String =
        raw.uppercase().filter { it in CODE_ALPHABET }.take(6)

    // ------------------------------------------------------------------ read

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        awaitAuthReady()
        val uid = currentUserId() ?: run {
            Log.w(TAG, "refresh skipped: no session")
            return@withContext false
        }
        runCatching {
            val members = client.from("members").select().decodeList<Member>()
            val me = members.firstOrNull { it.id == uid }
            val partner = members.firstOrNull { it.id != uid }
            val couple = client.from("couples").select().decodeList<Couple>().firstOrNull()
            val countdowns = client.from("countdowns").select {
                order("date", Order.ASCENDING)
            }.decodeList<Countdown>()
            val bucket = client.from("bucket_items").select {
                order("created_at", Order.ASCENDING)
            }.decodeList<BucketItem>()
            _snapshot.value = CoupleSnapshot(me, partner, couple, countdowns, bucket)
            _online.value = true
            if (_photoUrls.value.isEmpty()) scope.launch { refreshPhotoUrls() }
        }.onFailure {
            Log.w(TAG, "refresh failed", it)
            _online.value = false
        }.isSuccess
    }

    // -------------------------------------------------------------- realtime

    fun startRealtime() {
        if (realtimeJob?.isActive == true) return
        realtimeJob = scope.launch {
            runCatching {
                val channel = client.channel("filo-changes")
                val members = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "members" }
                val countdowns = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "countdowns" }
                val bucket = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "bucket_items" }
                channel.subscribe()
                merge(members, countdowns, bucket).collect {
                    // Any change at all just refetches. Two people, a handful of rows.
                    refresh()
                    onRemoteChange?.invoke()
                }
            }.onFailure { Log.w(TAG, "realtime stopped", it) }
        }
    }

    fun stopRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
    }

    /** Set by the app so a realtime change can push a fresh snapshot to the widgets. */
    var onRemoteChange: (() -> Unit)? = null

    // ----------------------------------------------------------- my own row

    private suspend fun updateMe(build: io.github.jan.supabase.postgrest.query.PostgrestUpdate.() -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            awaitAuthReady()
            val uid = currentUserId() ?: return@withContext false
            runCatching {
                client.from("members").update(build) { filter { eq("id", uid) } }
                _online.value = true
            }.onFailure {
                Log.w(TAG, "update failed", it)
                _online.value = false
            }.isSuccess
        }

    suspend fun setMood(emoji: String?, text: String?): Boolean = updateMe {
        set("mood_emoji", emoji ?: "")
        set("mood_text", (text ?: "").take(MOOD_MAX))
        set("mood_updated_at", Instant.now().toString())
    }

    suspend fun setNote(text: String): Boolean = updateMe {
        set("note_text", text.take(NOTE_MAX))
        set("note_updated_at", Instant.now().toString())
    }

    suspend fun setSleepWindow(start: LocalTime, end: LocalTime): Boolean = updateMe {
        set("sleep_start", start.toString())
        set("sleep_end", end.toString())
    }

    suspend fun setDisplayName(name: String): Boolean {
        prefs.setDisplayName(name)
        return updateMe { set("display_name", name) }
    }

    suspend fun setLocale(locale: String): Boolean {
        prefs.setLocale(locale)
        return updateMe { set("locale", locale) }
    }

    /** Written on every launch so it self corrects when someone travels. */
    suspend fun syncTimezoneAndPresence(): Boolean = updateMe {
        set("timezone", ZoneId.systemDefault().id)
        set("last_seen_at", Instant.now().toString())
    }

    suspend fun setLocation(lat: Double, lon: Double, city: String?): Boolean = updateMe {
        set("lat", lat)
        set("lon", lon)
        set("location_updated_at", Instant.now().toString())
        if (city != null) set("city", city)
    }

    /** A single fix from the always-on service. Accuracy is kept so the UI can be honest. */
    suspend fun recordLiveLocation(lat: Double, lon: Double, accuracyMetres: Double): Boolean {
        val existing = _snapshot.value.me
        val needsCity = existing?.city.isNullOrBlank() ||
            (existing?.lat != null && existing.lon != null &&
                Haversine.km(existing.lat, existing.lon, lat, lon) > 25.0)
        val city = if (needsCity) locationReader.cityName(lat, lon) else null
        val ok = updateMe {
            set("lat", lat)
            set("lon", lon)
            set("location_accuracy_m", accuracyMetres)
            set("location_is_live", true)
            set("location_updated_at", Instant.now().toString())
            if (city != null) set("city", city)
        }
        if (ok) refresh()
        return ok
    }

    suspend fun setLocationLive(live: Boolean): Boolean = updateMe { set("location_is_live", live) }

    suspend fun setBattery(level: Int, charging: Boolean): Boolean = updateMe {
        set("battery_level", level)
        set("battery_charging", charging)
        set("battery_updated_at", Instant.now().toString())
    }

    suspend fun setFcmToken(token: String): Boolean = updateMe { set("fcm_token", token) }

    /**
     * Publishes what this phone is playing so the other one can see it. Only the track's
     * public identity travels; the Spotify token never leaves this device.
     */
    suspend fun publishNowPlaying(): Boolean = withContext(Dispatchers.IO) {
        if (!SpotifyAuth.isConfigured) return@withContext false
        val playing = SpotifyApi.currentlyPlaying(context) ?: return@withContext false
        updateMe {
            set("spotify_track_id", playing.trackId ?: "")
            set("spotify_track_name", playing.trackName)
            set("spotify_artist", playing.artist)
            set("spotify_art_url", playing.artUrl ?: "")
            set("spotify_is_playing", playing.isPlaying)
            set("spotify_updated_at", Instant.now().toString())
        }
    }

    suspend fun setDailyPhoto(url: String): Boolean = updateMe {
        set("daily_photo_url", url)
        set("daily_photo_at", Instant.now().toString())
    }

    /** Unlinking must also retract what was already shared. */
    suspend fun clearNowPlaying(): Boolean = updateMe {
        set("spotify_track_id", "")
        set("spotify_track_name", "")
        set("spotify_artist", "")
        set("spotify_art_url", "")
        set("spotify_is_playing", false)
        set("spotify_updated_at", Instant.now().toString())
    }

    suspend fun setAvatar(url: String): Boolean = updateMe { set("photo_url", url) }

    // ------------------------------------------------------------- the couple

    suspend fun setSinceDate(date: LocalDate): Boolean = withContext(Dispatchers.IO) {
        val coupleId = _snapshot.value.couple?.id ?: return@withContext false
        runCatching {
            client.from("couples").update({ set("since_date", date.toString()) }) {
                filter { eq("id", coupleId) }
            }
            refresh()
        }.isSuccess
    }

    // ------------------------------------------------------------ countdowns

    suspend fun addCountdown(labelEn: String, labelIt: String, date: LocalDate, emoji: String?, primary: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val coupleId = prefs.currentPairing().coupleId ?: return@withContext false
            runCatching {
                if (primary) clearPrimaryFlag()
                client.from("countdowns").insert(
                    buildJsonObject {
                        put("couple_id", coupleId)
                        put("label_en", labelEn)
                        put("label_it", labelIt.ifBlank { labelEn })
                        put("date", date.toString())
                        put("emoji", emoji ?: "")
                        put("is_primary", primary)
                    },
                )
                refresh()
            }.onFailure { Log.w(TAG, "addCountdown failed", it) }.isSuccess
        }

    suspend fun updateCountdown(id: String, labelEn: String, labelIt: String, date: LocalDate, emoji: String?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                client.from("countdowns").update({
                    set("label_en", labelEn)
                    set("label_it", labelIt.ifBlank { labelEn })
                    set("date", date.toString())
                    set("emoji", emoji ?: "")
                }) { filter { eq("id", id) } }
                refresh()
            }.isSuccess
        }

    suspend fun setPrimaryCountdown(id: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            clearPrimaryFlag()
            client.from("countdowns").update({ set("is_primary", true) }) { filter { eq("id", id) } }
            refresh()
        }.isSuccess
    }

    private suspend fun clearPrimaryFlag() {
        val coupleId = prefs.currentPairing().coupleId ?: return
        client.from("countdowns").update({ set("is_primary", false) }) {
            filter { eq("couple_id", coupleId) }
        }
    }

    suspend fun deleteCountdown(id: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.from("countdowns").delete { filter { eq("id", id) } }
            refresh()
        }.isSuccess
    }

    // ----------------------------------------------------------- bucket list

    suspend fun addBucketItem(text: String): Boolean = withContext(Dispatchers.IO) {
        val pairing = prefs.currentPairing()
        val coupleId = pairing.coupleId ?: return@withContext false
        val uid = currentUserId() ?: return@withContext false
        runCatching {
            client.from("bucket_items").insert(
                buildJsonObject {
                    put("couple_id", coupleId)
                    put("text", text.trim())
                    put("created_by", uid)
                },
            )
            refresh()
        }.onFailure { Log.w(TAG, "addBucketItem failed", it) }.isSuccess
    }

    suspend fun setBucketDone(id: String, done: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.from("bucket_items").update({
                set("done", done)
                set("done_at", if (done) Instant.now().toString() else "")
            }) { filter { eq("id", id) } }
            refresh()
        }.isSuccess
    }

    suspend fun deleteBucketItem(id: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.from("bucket_items").delete { filter { eq("id", id) } }
            refresh()
        }.isSuccess
    }

    // --------------------------------------------------------------- photos

    /**
     * The bucket is private, so nothing is ever a public URL. We store the object path on the
     * row and mint short lived signed URLs for display.
     */
    private val _photoUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val photoUrls: StateFlow<Map<String, String>> = _photoUrls.asStateFlow()

    suspend fun uploadAvatar(uri: Uri): Boolean = uploadPhoto(uri, "avatar") { path -> setAvatar(path) }

    suspend fun uploadDailyPhoto(uri: Uri): Boolean = uploadPhoto(uri, "daily") { path -> setDailyPhoto(path) }

    private suspend fun uploadPhoto(
        uri: Uri,
        kind: String,
        persist: suspend (String) -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        awaitAuthReady()
        val coupleId = prefs.currentPairing().coupleId ?: return@withContext false
        val uid = currentUserId() ?: return@withContext false
        val bytes = ImageTools.prepareForUpload(context, uri) ?: return@withContext false
        // A stable name per person per kind, so old uploads do not pile up in the bucket.
        val path = "$coupleId/$kind-$uid.jpg"
        runCatching {
            client.storage.from(PHOTO_BUCKET).upload(path, bytes) { upsert = true }
            persist(path)
            refreshPhotoUrls()
            refresh()
        }.onFailure { Log.w(TAG, "photo upload failed", it) }.isSuccess
    }

    suspend fun refreshPhotoUrls() = withContext(Dispatchers.IO) {
        awaitAuthReady()
        val snapshot = _snapshot.value
        val paths = listOfNotNull(
            snapshot.me?.photoUrl,
            snapshot.partner?.photoUrl,
            snapshot.me?.dailyPhotoUrl,
            snapshot.partner?.dailyPhotoUrl,
        ).filter { it.isNotBlank() }.distinct()

        if (paths.isEmpty()) {
            _photoUrls.value = emptyMap()
            return@withContext
        }
        val resolved = paths.mapNotNull { path ->
            runCatching {
                path to client.storage.from(PHOTO_BUCKET).createSignedUrl(path, SIGNED_URL_TTL)
            }.onFailure { Log.w(TAG, "signing $path failed", it) }.getOrNull()
        }.toMap()
        _photoUrls.value = resolved
    }

    // ----------------------------------------------------------------- pings

    /**
     * Inserts the row and lets the database trigger do the notifying. The UI throttle here
     * is only courtesy; the real rate limit lives in the Edge Function.
     */
    suspend fun sendPing(): Boolean = withContext(Dispatchers.IO) {
        awaitAuthReady()
        val coupleId = prefs.currentPairing().coupleId ?: return@withContext false
        val uid = currentUserId() ?: run {
            Log.w(TAG, "ping skipped: no session")
            return@withContext false
        }
        runCatching {
            client.from("pings").insert(
                buildJsonObject {
                    put("couple_id", coupleId)
                    put("from_member", uid)
                },
            )
        }.onFailure { Log.w(TAG, "ping failed", it) }.isSuccess
    }

    // ------------------------------------------------------------------ sync

    /**
     * The one sync routine, called when the app comes to the foreground and again from the
     * periodic worker. Location is only ever read with [readLocation] true, which only the
     * foreground path passes: nothing here ever polls location in the background.
     */
    suspend fun syncEverything(readLocation: Boolean): Boolean = withContext(Dispatchers.IO) {
        awaitAuthReady()
        if (currentUserId() == null && ensureSignedIn() == null) return@withContext false

        syncTimezoneAndPresence()

        BatteryReader.read(context)?.let { setBattery(it.level, it.charging) }

        // Pull first so the screen fills in immediately; a location fix can take seconds and
        // must never be the thing the rest of the sync is queued behind.
        val refreshed = refresh()

        // Cheap and independent, so a Spotify hiccup never holds up the rest of the sync.
        runCatching { publishNowPlaying() }

        if (readLocation && locationReader.hasPermission()) {
            locationReader.currentLocation()?.let { (lat, lon) ->
                // Only reverse geocode when we have not named this spot before, so a routine
                // sync never costs a geocoder round trip.
                val existing = _snapshot.value.me
                val moved = existing?.lat == null || existing.lon == null ||
                    Haversine.km(existing.lat, existing.lon, lat, lon) > 25.0
                val city = if (existing?.city.isNullOrBlank() || moved) {
                    locationReader.cityName(lat, lon)
                } else {
                    null
                }
                setLocation(lat, lon, city)
                refresh()
            }
        }


        // Weather is for their coordinates, not ours.
        val partner = _snapshot.value.partner
        if (partner?.lat != null && partner.lon != null) {
            _weather.value = weatherRepo.get(partner.lat, partner.lon)
        }
        refreshed
    }

    /**
     * Live location, foreground only. Writes are throttled: a new row every minute, or
     * immediately if they have moved more than a kilometre, so a walk across town does not
     * turn into hundreds of updates.
     */
    fun startLiveLocation() {
        if (liveLocationJob?.isActive == true) return
        if (!locationReader.hasPermission()) return
        liveLocationJob = scope.launch {
            var lastWriteAt = 0L
            var lastLat: Double? = null
            var lastLon: Double? = null
            locationReader.liveUpdates().collect { (lat, lon) ->
                val now = System.currentTimeMillis()
                val movedFar = lastLat == null || lastLon == null ||
                    Haversine.km(lastLat!!, lastLon!!, lat, lon) > 1.0
                if (!movedFar && now - lastWriteAt < LIVE_WRITE_INTERVAL_MS) return@collect
                lastWriteAt = now
                lastLat = lat
                lastLon = lon
                val existing = _snapshot.value.me
                val needsCity = existing?.city.isNullOrBlank() ||
                    (existing?.lat != null && existing.lon != null &&
                        Haversine.km(existing.lat, existing.lon, lat, lon) > 25.0)
                setLocation(lat, lon, if (needsCity) locationReader.cityName(lat, lon) else null)
                refresh()
            }
        }
    }

    fun stopLiveLocation() {
        liveLocationJob?.cancel()
        liveLocationJob = null
    }

    suspend fun loadCachedWeather() {
        _weather.value = weatherRepo.cached()
    }

    /** Distance is computed on device from the two stored coordinates. */
    fun distanceState(): DistanceState {
        val me = _snapshot.value.me
        val partner = _snapshot.value.partner
        if (!locationReader.hasPermission() && me?.lat == null) return DistanceState.PermissionDenied
        if (me?.lat == null || me.lon == null || partner?.lat == null || partner.lon == null) {
            return DistanceState.Missing
        }
        val mineStale = LocationPolicy.isStale(PgTime.instant(me.locationUpdatedAt))
        val theirsStale = LocationPolicy.isStale(PgTime.instant(partner.locationUpdatedAt))
        if (mineStale || theirsStale) return DistanceState.Stale
        return DistanceState.Known(
            km = LocationPolicy.roundKm(Haversine.km(me.lat, me.lon, partner.lat, partner.lon)),
            myCity = me.city,
            theirCity = partner.city,
        )
    }

    companion object {
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val PHOTO_BUCKET = "couple-photos"
        const val NOTE_MAX = 140
        const val MOOD_MAX = 40
        const val LIVE_WRITE_INTERVAL_MS = 60_000L
        val SIGNED_URL_TTL: kotlin.time.Duration = kotlin.time.Duration.parse("7d")
    }
}
