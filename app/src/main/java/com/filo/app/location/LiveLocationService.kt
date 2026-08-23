package com.filo.app.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.filo.app.FiloApp
import com.filo.app.MainActivity
import com.filo.app.R
import com.filo.app.data.FiloRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "LiveLocation"
private const val NOTIFICATION_ID = 4711
const val LIVE_LOCATION_CHANNEL_ID = "filo_live_location"

/**
 * Always-on location sharing.
 *
 * This is a `location` type foreground service with a permanent notification, which is the
 * only way Android will keep giving an app positions once it is no longer on screen. It is
 * started from a visible activity, because API 31+ forbids starting a while-in-use foreground
 * service from the background at all.
 *
 * The user turns this on explicitly and can turn it off from the notification or from
 * Settings. It is off until they do.
 */
class LiveLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val repository: FiloRepository by lazy {
        (applicationContext as? FiloApp)?.repository ?: FiloRepository(applicationContext)
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            scope.launch {
                repository.recordLiveLocation(
                    lat = location.latitude,
                    lon = location.longitude,
                    accuracyMetres = location.accuracy.toDouble(),
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LiveLocationController.setEnabled(applicationContext, false)
            stopSelf()
            return START_NOT_STICKY
        }

        // Check before promoting to foreground, never after. Calling startForeground and
        // then stopping is what produces the "did not stop in time" ANR and, on API 34+, a
        // crash for a location-type service started without the grant behind it.
        if (!LocationPermissions.hasForegroundLocation(this) ||
            !LocationPermissions.hasBackgroundLocation(this)
        ) {
            Log.w(TAG, "location permissions missing, not starting")
            LiveLocationController.setEnabled(applicationContext, false)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel(this)
        startInForeground()
        requestUpdates()
        scope.launch { repository.setLocationLive(true) }
        // START_STICKY so the system brings it back if it is killed for memory.
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification(this, partnerName = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // From API 34 the type must be declared here as well as in the manifest, or
            // startForeground throws.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
            .onFailure { Log.w(TAG, "could not start updates", it) }
    }

    override fun onDestroy() {
        runCatching { client.removeLocationUpdates(callback) }
        scope.launch { repository.setLocationLive(false) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.filo.app.action.STOP_LIVE_LOCATION"

        /** Once a minute is plenty for two people 2000 km apart, and is kind to the battery. */
        const val INTERVAL_MS = 60_000L
        const val FASTEST_MS = 30_000L
        const val MIN_DISTANCE_M = 25f

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(LIVE_LOCATION_CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    LIVE_LOCATION_CHANNEL_ID,
                    context.getString(R.string.tracking_notification_channel),
                    // Low: it must be visible, but it must never make a sound.
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }

        fun buildNotification(context: Context, partnerName: String?): Notification {
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val stop = PendingIntent.getService(
                context,
                1,
                Intent(context, LiveLocationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, LIVE_LOCATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_pin)
                .setContentTitle(context.getString(R.string.tracking_notification_title))
                .setContentText(
                    context.getString(
                        R.string.tracking_notification_text,
                        partnerName ?: context.getString(R.string.app_name),
                    ),
                )
                .setColor(0xFFD41E2F.toInt())
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.tracking_stop), stop)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
