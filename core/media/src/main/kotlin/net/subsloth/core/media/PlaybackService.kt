package net.subsloth.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger

/**
 * Foreground service for media playback.
 *
 * Declares `mediaPlayback` foreground-service type so the app can continue
 * audio/video playback in the background on Android 14+. This service does
 * NOT start from [Intent.ACTION_BOOT_COMPLETED] — it is started only when
 * the player transitions to background or TV mode.
 *
 * Send [ACTION_STOP] to stop the service and remove the notification when
 * playback ends or the player is released.
 */
class PlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        Logger.withTag(TAG).d { "Service created" }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.withTag(TAG).d { "onStartCommand: action=${intent?.action}, startId=$startId" }
        if (intent?.action == ACTION_STOP) {
            Logger.withTag(TAG).d { "Stopping foreground service" }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.withTag(TAG).d { "Service destroyed" }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(NOTIFICATION_TITLE)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setCategory(Notification.CATEGORY_TRANSPORT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    internal companion object {
        internal const val ACTION_STOP = "net.subsloth.core.media.action.STOP"
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "playback"
        private const val CHANNEL_NAME = "Media Playback"
        private const val CHANNEL_DESCRIPTION = "Shows when media is playing in the background"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_TITLE = "Playing media"
    }
}
