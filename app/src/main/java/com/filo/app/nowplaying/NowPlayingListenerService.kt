package com.filo.app.nowplaying

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.filo.app.FiloApp
import com.filo.app.data.FiloRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "NowPlayingListener"

/**
 * Reads what is playing straight off this phone and publishes it for the other one.
 *
 * Notification access is only the key: this service never reads a single notification. It
 * exists because MediaSessionManager.getActiveSessions refuses to hand over media sessions
 * unless the caller has an enabled notification listener, and the OS will only bind one that
 * is declared like this. onNotificationPosted is deliberately not implemented, and the
 * manifest asks the system not to deliver those callbacks at all, so the promise that Filo
 * cannot see your messages is structural rather than a pinky swear.
 *
 * The upside over asking Spotify's own API: no Spotify developer app, no account linking on
 * either phone, nobody has to keep paying for Premium, it works with the screen off, and it
 * is pushed the instant the track changes instead of polled.
 */
class NowPlayingListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionManager: MediaSessionManager? = null
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var publishJob: Job? = null
    private var lastPublished: LocalNowPlaying? = null

    private val repository: FiloRepository by lazy {
        (applicationContext as? FiloApp)?.repository ?: FiloRepository(applicationContext)
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachTo(controllers.orEmpty())
        schedulePublish()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "connected")
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = manager
        val component = ComponentName(this, NowPlayingListenerService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, component)
            attachTo(manager.getActiveSessions(component))
            schedulePublish()
        }.onFailure { Log.w(TAG, "could not read media sessions", it) }
    }

    override fun onListenerDisconnected() {
        detachAll()
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        sessionManager = null
        super.onListenerDisconnected()
    }

    // Notifications themselves are of no interest. Left empty on purpose.
    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    override fun onDestroy() {
        detachAll()
        scope.cancel()
        super.onDestroy()
    }

    private fun attachTo(controllers: List<MediaController>) {
        val spotify = controllers.filter { it.packageName in NowPlayingReader.SPOTIFY_PACKAGES }

        // Drop callbacks for sessions that have gone away.
        controllerCallbacks.keys.toList()
            .filterNot { it in spotify }
            .forEach { stale ->
                controllerCallbacks.remove(stale)?.let { runCatching { stale.unregisterCallback(it) } }
            }

        spotify.filterNot { it in controllerCallbacks }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = schedulePublish()
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = schedulePublish()
                override fun onSessionDestroyed() = schedulePublish()
            }
            runCatching { controller.registerCallback(callback) }
                .onSuccess { controllerCallbacks[controller] = callback }
        }
    }

    private fun detachAll() {
        controllerCallbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        controllerCallbacks.clear()
        publishJob?.cancel()
    }

    /**
     * Trailing debounce. Spotify emits several metadata pushes per track as fields land, so
     * publishing on the first one writes a half filled row and then corrects it. Waiting for
     * the dust to settle costs a couple of seconds and writes once.
     */
    private fun schedulePublish() {
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(DEBOUNCE_MS)
            val current = currentlyPlaying()
            if (current == lastPublished) return@launch
            lastPublished = current
            if (current == null) {
                repository.clearNowPlaying()
            } else {
                repository.publishLocalNowPlaying(
                    trackId = current.trackId,
                    title = current.title,
                    artist = current.artist,
                    artUrl = current.artUrl,
                    isPlaying = current.isPlaying,
                )
            }
        }
    }

    private fun currentlyPlaying(): LocalNowPlaying? {
        val manager = sessionManager ?: return null
        val component = ComponentName(this, NowPlayingListenerService::class.java)
        val controllers = runCatching { manager.getActiveSessions(component) }.getOrNull().orEmpty()
        // Prefer whatever is actually playing over something merely paused.
        return controllers.asSequence()
            .mapNotNull { NowPlayingReader.read(it) }
            .sortedByDescending { it.isPlaying }
            .firstOrNull()
            ?.takeIf { it.isPlaying }
    }

    private companion object {
        const val DEBOUNCE_MS = 2_500L
    }
}
