package com.filo.app.nowplaying

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

private const val TAG = "NotificationAccess"

/**
 * Whether Filo is allowed to see what is playing on this phone.
 *
 * This is "notification access", which is granted from a system settings screen rather than a
 * runtime dialog: there is no permission request to launch and no result to receive back, so
 * everything here is a check plus a way to open the right screen.
 */
object NotificationAccess {

    fun component(context: Context): ComponentName =
        ComponentName(context.applicationContext, NowPlayingListenerService::class.java)

    fun isGranted(context: Context): Boolean {
        val component = component(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val granted = runCatching { manager?.isNotificationListenerAccessGranted(component) }.getOrNull()
            if (granted != null) return granted
        }
        // API 26 only, and a fallback if the call above is unavailable on an OEM build.
        // Match the whole component, not just the package: matching the package would say
        // yes for any listener this app ever ships.
        return runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                .orEmpty()
                .split(':')
                .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
                .any { it == component }
        }.getOrDefault(false)
    }

    /**
     * Opens the toggle. On Android 11+ this lands directly on Filo's own row; otherwise it
     * opens the full list and the user finds Filo in it.
     */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val direct = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component(context).flattenToString(),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(direct); true }.getOrDefault(false)) return
        }
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.w(TAG, "could not open notification access settings", it) }
    }

    /** The app info screen, where a sideloaded app's restricted settings are unlocked. */
    fun openAppInfo(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Android 13 and up greys this toggle out for apps that were not installed by an app
     * store, which is exactly how Filo arrives. The way out is the three dot menu on the
     * app info screen: "Allow restricted settings". Worth saying out loud, because otherwise
     * it looks like the app is broken.
     */
    val needsRestrictedSettingsUnlock: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
