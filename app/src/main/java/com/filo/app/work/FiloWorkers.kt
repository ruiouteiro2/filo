package com.filo.app.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.filo.app.FiloApp
import com.filo.app.core.prefs.FiloPrefs
import com.filo.app.core.time.PgTime
import com.filo.app.data.FiloRepository
import com.filo.app.widget.HeartWidget
import com.filo.app.widget.WidgetImages
import com.filo.app.widget.MUSIC_STALE_MS
import com.filo.app.widget.WidgetSnapshotStore
import com.filo.app.widget.WidgetUpdater
import androidx.glance.appwidget.updateAll
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val TAG = "FiloWorker"

/** Long enough for Glance to finish drawing before the process may be reclaimed. */
private const val GLANCE_SETTLE_MS = 2_500L

/**
 * The real update cadence for the widgets. updatePeriodMillis in the provider XML is what
 * gets them re-rendered after a reboot, but it is unreliable for anything else, so the
 * actual refreshing is done here.
 *
 * Location is deliberately never read from this worker: it only ever happens in the
 * foreground, which keeps the app out of background location permission territory.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val repository = (app as? FiloApp)?.repository ?: FiloRepository(app)
        val prefs = FiloPrefs(app)

        return try {
            if (!prefs.currentPairing().isPaired) return Result.success()
            repository.syncEverything(readLocation = false)
            repository.refreshPhotoUrls()
            writeSnapshot(app, repository, prefs)
            WidgetUpdater.updateAll(app)
            // Glance recomposes in a session of its own. Returning straight away lets the
            // process be reclaimed mid-recomposition, and the widget keeps the old picture
            // even though the snapshot underneath it is already correct.
            kotlinx.coroutines.delay(GLANCE_SETTLE_MS)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "sync worker failed", e)
            Result.retry()
        }
    }

    companion object {
        const val NAME = "filo-sync"
        private const val NOW = "filo-sync-now"

        /**
         * One sync, as soon as the system will allow it. Used by the silent push the other
         * phone sends when something the widgets show has changed.
         */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                // APPEND, not REPLACE: replacing cancelled a sync that was halfway through
                // and threw the cancellation into the repository for nothing.
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }

        /** 15 minutes is the floor WorkManager will honour for periodic work. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/** Puts the heart widget back to its resting state after the confirmation has been seen. */
class HeartResetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        HeartWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        const val NAME = "filo-heart-reset"
    }
}

/**
 * Builds the widget snapshot and renders the bitmaps the widgets need. Shared by the worker
 * and by the app whenever it syncs, so both paths produce identical widget state.
 */
suspend fun writeSnapshot(context: Context, repository: FiloRepository, prefs: FiloPrefs) {
    val couple = repository.snapshot.value
    val partner = couple.partner
    val photoUrls = repository.photoUrls.value
    val locale = prefs.currentPairing().locale
    val clock24h = prefs.currentClock24h()
    val previous = WidgetSnapshotStore.read(context)

    // A sync that failed leaves the repository holding an empty couple. Writing that would
    // replace a good snapshot with a hollow one, and the widgets would go from showing a
    // life to showing a clock and nothing else - which is exactly what used to happen.
    val paired = prefs.currentPairing().isPaired
    if (paired && partner == null && previous.partnerName != null) {
        Log.i(TAG, "skipping widget write: nothing loaded, keeping the last good snapshot")
        return
    }

    // Likewise for the bitmaps: a render that failed (offline, expired URL) keeps the file
    // that is already on disk rather than blanking the face and the photo of the day.
    val avatar = WidgetImages.renderAvatar(context, photoUrls[partner?.photoUrl])
        ?: previous.partnerAvatar?.takeIf { java.io.File(it).exists() }
    val photo = WidgetImages.cachePhoto(context, photoUrls[partner?.dailyPhotoUrl])
        ?: previous.photoImage?.takeIf { java.io.File(it).exists() }

    // A widget showing "playing" is only right until the heartbeat window closes. Book the
    // repaint that will quietly turn it into "last played" so nobody has to open the app
    // for the home screen to stop lying.
    // Only while the claim is still live: booking this for a row that has already gone
    // stale just wakes the phone again five seconds later, forever.
    if (partner?.isNowPlayingLive == true) {
        MusicExpiryWorker.schedule(context, partner.spotifyUpdatedAt)
    }

    WidgetSnapshotStore.write(
        context,
        WidgetSnapshotStore.build(
            couple = couple,
            weather = repository.weather.value,
            distance = repository.distanceState(),
            locale = locale,
            clock24h = clock24h,
            avatarImage = avatar,
            photoImage = photo,
            fallbackWeatherCode = previous.weatherCode,
            fallbackWeatherTemp = previous.weatherTemp,
            fallbackWeatherIsDay = previous.weatherIsDay,
        ),
    )
}

/**
 * Repaints the widgets once the partner's now-playing has gone stale. Without it a widget
 * would keep the last "playing" line on screen until something else happened to refresh it,
 * which on a quiet evening can be half an hour.
 */
class MusicExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val repository = (app as? FiloApp)?.repository ?: FiloRepository(app)
        val prefs = FiloPrefs(app)
        return runCatching {
            // Re-read rather than trust the alarm: they may well have started something new.
            repository.refresh()
            writeSnapshot(app, repository, prefs)
            WidgetUpdater.updateAll(app)
            kotlinx.coroutines.delay(GLANCE_SETTLE_MS)
            Result.success()
        }.getOrElse {
            Log.w(TAG, "music expiry repaint failed", it)
            Result.success()
        }
    }

    companion object {
        const val NAME = "filo-music-expiry"

        fun schedule(context: Context, updatedAt: String?) {
            val at = PgTime.instant(updatedAt)?.toEpochMilli() ?: System.currentTimeMillis()
            val due = at + MUSIC_STALE_MS - System.currentTimeMillis()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MusicExpiryWorker>()
                    .setInitialDelay(due.coerceAtLeast(5_000L), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }
    }
}
