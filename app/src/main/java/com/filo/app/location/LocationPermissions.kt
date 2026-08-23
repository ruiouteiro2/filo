package com.filo.app.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants

/**
 * The exact permission ladder for always-on location. The order matters and the rules change
 * by API level, so all of that knowledge lives here rather than being spread through the UI.
 */
object LocationPermissions {

    /**
     * On API 31+ asking for FINE without COARSE in the same call is silently ignored: no
     * dialog, no callback, just a line in logcat. They always go together.
     */
    val FOREGROUND = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun hasForegroundLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasPreciseLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Below API 29 there is no separate background permission: a foreground grant already
     * means all the time.
     */
    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasForegroundLocation(context)
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * On API 30+ the system shows no dialog for background location at all: the request drops
     * the user into app settings with no explanation of its own, which is why the app must
     * explain first and then send them there deliberately.
     */
    fun backgroundNeedsSettingsTrip(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun openAppSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * A foreground service does not exempt an app from Doze. Without this, always-on tracking
     * quietly stops overnight, which is exactly when it would be most missed.
     */
    fun isIgnoringBatteryOptimisations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun requestIgnoreBatteryOptimisations(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct); true }.getOrDefault(false)) return

        // A few OEM ROMs ship no activity for the direct request, so fall back to the list.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * The silent killer. If a phone goes a few months without opening Filo, Android hibernates
     * the app, revokes its permissions and force stops it. Tracking then dies with no error
     * anywhere. There is no manifest flag for this; the user has to opt out by hand.
     */
    fun isAutoRevokeExempt(context: Context): Boolean = runCatching {
        // androidx handles the whole matrix: no restrictions at all, the Play-services
        // backport on older devices, API 30 hibernation, and API 31+ full hibernation.
        PackageManagerCompat.getUnusedAppRestrictionsStatus(context).get() ==
            UnusedAppRestrictionsConstants.DISABLED
    }.getOrDefault(true)

    fun requestAutoRevokeExemption(context: Context) {
        runCatching {
            context.startActivity(
                IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { openAppSettings(context) }
    }

    /**
     * The exact words the system will show on this device and in this language, e.g.
     * "Allow all the time". Hardcoding it goes wrong across OEM ROMs and across en/it.
     */
    fun backgroundOptionLabel(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            context.packageManager.backgroundPermissionOptionLabel.toString().takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
