package net.subsloth.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service for media playback.
 *
 * Declares `mediaPlayback` foreground-service type so the app can continue
 * audio/video playback in the background on Android 14+. This service does
 * NOT start from [Intent.ACTION_BOOT_COMPLETED] — it is started only when
 * the player transitions to background or TV mode.
 */
class PlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    private fun buildNotification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(NOTIFICATION_TITLE)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setCategory(Notification.CATEGORY_TRANSPORT)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .build()

    private companion object {
        private const val CHANNEL_ID = "playback"
        private const val CHANNEL_NAME = "Media Playback"
        private const val CHANNEL_DESCRIPTION = "Shows when media is playing in the background"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_TITLE = "Playing media"
    }
}
