package com.filo.app.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
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
import com.filo.app.widget.WidgetSnapshotStore
import com.filo.app.widget.WidgetUpdater
import androidx.glance.appwidget.updateAll
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val TAG = "FiloWorker"

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
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "sync worker failed", e)
            Result.retry()
        }
    }

    companion object {
        const val NAME = "filo-sync"

        /** 30 minutes is the floor WorkManager will honour for periodic work. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
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
        ),
    )
}
