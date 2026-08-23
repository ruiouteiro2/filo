package com.filo.app.core.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "Geo"

object Haversine {
    private const val EARTH_RADIUS_KM = 6371.0088

    /** Great circle distance in kilometres. */
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a).coerceIn(0.0, 1.0))
    }
}

/** What the distance card is allowed to say. */
sealed interface DistanceState {
    data class Known(val km: Long, val myCity: String?, val theirCity: String?) : DistanceState
    data object Stale : DistanceState
    data object Missing : DistanceState
    data object PermissionDenied : DistanceState
}

object LocationPolicy {
    /** Older than this and we refuse to show a number rather than show a wrong one. */
    val STALE_AFTER: Duration = Duration.ofDays(7)

    fun isStale(updatedAt: Instant?, now: Instant = Instant.now()): Boolean =
        updatedAt == null || Duration.between(updatedAt, now) > STALE_AFTER

    fun roundKm(value: Double): Long = value.roundToLong()
}

class LocationReader(private val context: Context) {

    private companion object {
        const val FIX_TIMEOUT_MS = 8_000L
        const val LAST_KNOWN_TIMEOUT_MS = 3_000L
        const val LIVE_INTERVAL_MS = 20_000L
        const val LIVE_FASTEST_MS = 10_000L
        const val LIVE_MIN_DISTANCE_M = 50f
    }


    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Read once, while the app is in the foreground. Balanced power accuracy is plenty: at
     * 2000 km the number does not meaningfully change, and this keeps us out of background
     * location permission territory entirely.
     *
     * Hard bounded in time: getCurrentLocation never completes when no fix can be obtained
     * (indoors, aeroplane mode, a cold emulator), and an unbounded await here would stall
     * the whole sync behind it. The cancellation token means we stop the fix as well as
     * stop waiting for it.
     */
    suspend fun currentLocation(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        return withContext(Dispatchers.IO) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancellation = CancellationTokenSource()
            try {
                withTimeoutOrNull(FIX_TIMEOUT_MS) {
                    runCatching {
                        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token).await()
                    }.getOrNull()
                }?.let { return@withContext it.latitude to it.longitude }

                // No fresh fix in time: the last known position is good enough at this range.
                withTimeoutOrNull(LAST_KNOWN_TIMEOUT_MS) {
                    runCatching { client.lastLocation.await() }.getOrNull()
                }?.let { return@withContext it.latitude to it.longitude }

                Log.w(TAG, "no location available within timeout")
                null
            } finally {
                cancellation.cancel()
            }
        }
    }

    /**
     * A stream of positions while the app is open. This is the "live" in live location: it
     * runs only in the foreground and is torn down the moment the screen goes away, so the
     * app still never asks for background location.
     */
    fun liveUpdates(): Flow<Pair<Double, Double>> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LIVE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LIVE_FASTEST_MS)
            .setMinUpdateDistanceMeters(LIVE_MIN_DISTANCE_M)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.latitude to it.longitude) }
            }
        }
        runCatching { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
            .onFailure { Log.w(TAG, "live location unavailable", it); close() }
        awaitClose { runCatching { client.removeLocationUpdates(callback) } }
    }

    /** Reverse geocoded once and then cached on the row; never re-run on every sync. */
    suspend fun cityName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lon, 1)
            } else {
                geocoder.getFromLocation(lat, lon, 1)
            }
            results?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.onFailure { Log.w(TAG, "reverse geocode failed", it) }.getOrNull()
    }
}
