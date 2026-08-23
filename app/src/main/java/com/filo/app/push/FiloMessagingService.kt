package com.filo.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.filo.app.BuildConfig
import com.filo.app.FiloApp
import com.filo.app.MainActivity
import com.filo.app.R
import com.filo.app.data.FiloRepository
import com.filo.app.work.SyncWorker
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "FiloPush"
const val PING_CHANNEL_ID = "filo_ping"

/**
 * Push does exactly one thing: tell you they are thinking of you. There is no reply action
 * and no other kind of notification in the app.
 *
 * The body arrives already translated, because the recipient's language lives on the
 * recipient's row and the Edge Function reads it.
 */
class FiloMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            val repository = (applicationContext as? FiloApp)?.repository
                ?: FiloRepository(applicationContext)
            repository.setFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // A silent nudge from the other phone: something the widgets show has changed.
        // Nothing is displayed; we just sync and repaint, which is what makes the widgets
        // feel live instead of "whatever the half-hour worker last saw".
        if (message.data["type"] == "sync") {
            // Handed to WorkManager rather than a coroutine on this service: the process can
            // be killed the moment this method returns, and a half finished sync is how a
            // widget ends up showing yesterday. The worker survives that and retries.
            SyncWorker.runNow(applicationContext)
            return
        }

        val notification = message.notification
        val title = notification?.title ?: getString(R.string.ping_notification_title)
        val body = notification?.body ?: return

        PushSetup.ensureChannel(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val built = NotificationCompat.Builder(this, PING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_heart)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(0xFFD41E2F.toInt())
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching { NotificationManagerCompat.from(this).notify(1, built) }
            .onFailure { Log.w(TAG, "could not post notification", it) }
    }
}

object PushSetup {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(PING_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                PING_CHANNEL_ID,
                context.getString(R.string.ping_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.ping_channel_description)
            },
        )
    }

    /**
     * Registers this phone for push. Does nothing at all when no google-services.json was
     * present at build time, so the app still runs without a Firebase project.
     */
    fun registerToken(context: Context, onToken: suspend (String) -> Unit, scope: CoroutineScope) {
        if (!BuildConfig.PUSH_CONFIGURED) {
            Log.i(TAG, "push not configured in this build, skipping token registration")
            return
        }
        ensureChannel(context)
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "token fetch failed", task.exception)
                    return@addOnCompleteListener
                }
                task.result?.let { token -> scope.launch { onToken(token) } }
            }
        }.onFailure { Log.w(TAG, "firebase unavailable", it) }
    }
}
