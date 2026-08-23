package com.filo.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.filo.app.core.prefs.filoDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "LiveLocationCtl"

/** Whether this phone is sharing continuously, and the one place that starts or stops it. */
object LiveLocationController {

    private val KEY = booleanPreferencesKey("live_location_enabled")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enabledFlow(context: Context): Flow<Boolean> =
        context.applicationContext.filoDataStore.data.map { it[KEY] ?: false }

    suspend fun isEnabled(context: Context): Boolean = enabledFlow(context).first()

    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        scope.launch { app.filoDataStore.edit { it[KEY] = enabled } }
        if (!enabled) stop(app)
    }

    /**
     * Must be called while an activity is visible. API 31+ refuses to let an app start a
     * while-in-use foreground service (location, camera, microphone) from the background, so
     * there is no safe way to do this from a worker or a receiver at an arbitrary moment.
     */
    fun startFromForeground(context: Context) {
        val app = context.applicationContext
        if (!LocationPermissions.hasBackgroundLocation(app)) {
            Log.w(TAG, "refusing to start: no background location grant")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, LiveLocationService::class.java))
        }.onFailure { Log.w(TAG, "could not start live location service", it) }
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        runCatching { app.stopService(Intent(app, LiveLocationService::class.java)) }
    }

    /** Turns sharing on and starts it, in one call from the settings toggle. */
    fun enableAndStart(context: Context) {
        setEnabled(context, true)
        startFromForeground(context)
    }
}

/**
 * Brings tracking back after a reboot or after the APK is replaced, but only if the user had
 * it on and the grant is still there.
 *
 * BOOT_COMPLETED arrives while the device is unlocked, which is what makes starting a
 * location foreground service legal here; the boot case is one of the documented exemptions
 * from the background-start restriction.
 */
class LocationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val app = context.applicationContext
        val pending = goAsync()
        runCatching {
            val enabled = runBlocking { LiveLocationController.isEnabled(app) }
            if (enabled && LocationPermissions.hasBackgroundLocation(app)) {
                ContextCompat.startForegroundService(app, Intent(app, LiveLocationService::class.java))
            }
        }.onFailure { Log.w(TAG, "boot restart failed", it) }
        pending.finish()
    }
}
