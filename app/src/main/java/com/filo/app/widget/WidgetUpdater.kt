package com.filo.app.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.filo.app.FiloApp
import com.filo.app.core.prefs.PrefKeys
import com.filo.app.core.prefs.filoDataStore
import com.filo.app.data.FiloRepository
import com.filo.app.work.HeartResetWorker
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** One call that refreshes every widget the couple might have placed. */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        TogetherWidget().updateAll(context)
        CountdownWidget().updateAll(context)
        HeartWidget().updateAll(context)
        PhotoWidget().updateAll(context)
    }
}

/**
 * The heart's local state. The real rate limit lives in the Edge Function; this only stops
 * the widget from firing twice on a fat finger and drives the brief confirmation.
 */
object PingState {

    private const val CONFIRMATION_MS = 8_000L
    private const val MIN_INTERVAL_MS = 60_000L

    suspend fun recentlySent(context: Context): Boolean {
        val last = context.filoDataStore.data.first()[PrefKeys.LastPingAt] ?: return false
        return System.currentTimeMillis() - last < CONFIRMATION_MS
    }

    suspend fun send(context: Context) {
        val app = context.applicationContext
        val last = app.filoDataStore.data.first()[PrefKeys.LastPingAt] ?: 0L
        val now = System.currentTimeMillis()
        if (now - last < MIN_INTERVAL_MS) return

        val repository = (app as? FiloApp)?.repository ?: FiloRepository(app)
        // Confirm only what actually happened: a widget that says "Sent" when the insert
        // failed is worse than one that quietly does nothing.
        if (!repository.sendPing()) return

        app.filoDataStore.edit { it[PrefKeys.LastPingAt] = now }
        HeartWidget().updateAll(app)

        // Put the heart back to its resting state once the confirmation has been seen.
        WorkManager.getInstance(app).enqueueUniqueWork(
            HeartResetWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<HeartResetWorker>()
                .setInitialDelay(CONFIRMATION_MS + 500, TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}
