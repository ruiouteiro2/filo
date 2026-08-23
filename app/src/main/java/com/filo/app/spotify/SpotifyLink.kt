package com.filo.app.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

private const val TAG = "SpotifyLink"

/**
 * Opening a track in the other person's own Spotify.
 *
 * This is deliberately a plain ACTION_VIEW deep link rather than the Web API's
 * PUT /me/player/play. The Web API route is Premium only, needs an extra consent scope, and
 * needs an already-active Spotify Connect device, so it fails for exactly the people most
 * likely to tap. A deep link needs no token, no scope and no Premium: it hands the track to
 * the Spotify app and lets it do what it is for.
 */
object SpotifyLink {

    const val PACKAGE = "com.spotify.music"

    /** Requires the <queries> entry in the manifest to return true on Android 11+. */
    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrElse { error ->
        // Absent is a normal answer; anything else is worth knowing about rather than
        // silently reporting "not installed".
        if (error !is PackageManager.NameNotFoundException) {
            Log.w(TAG, "could not query the Spotify package", error)
        }
        false
    }

    /**
     * Opens the track. Tries the Spotify app first, then the web player, so this still does
     * something sensible on a phone without Spotify installed.
     */
    fun openTrack(context: Context, trackId: String): Boolean {
        if (trackId.isBlank()) return false

        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:track:$trackId")).apply {
            setPackage(PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Tells Spotify who sent them, which is what its own share links do.
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://" + context.packageName))
        }
        if (launch(context, appIntent)) return true

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://open.spotify.com/track/$trackId"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, webIntent)
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.i(TAG, "no handler for ${intent.data}", e)
        false
    }
}
