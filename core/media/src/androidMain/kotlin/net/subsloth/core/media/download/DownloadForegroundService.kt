package net.subsloth.core.media.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class DownloadForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(activeCount = 1)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateProgress(activeCount: Int, progressPercent: Int) {
        val notification = buildNotification(activeCount, progressPercent)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Download progress notifications"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    @Suppress("Deprecation")
    private fun buildNotification(activeCount: Int, progressPercent: Int = 0): Notification {
        val channelId = if (progressPercent == 0) CHANNEL_ID_SILENT else CHANNEL_ID
        return Notification.Builder(this, channelId)
            .setContentTitle("Downloading $activeCount file${if (activeCount != 1) "s" else ""}")
            .setContentText(
                if (progressPercent > 0) "$progressPercent% complete" else "Preparing download",
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val CHANNEL_ID_SILENT = "downloads_silent"
        const val CHANNEL_NAME = "Downloads"
        private const val NOTIFICATION_ID = 1001
    }
}
