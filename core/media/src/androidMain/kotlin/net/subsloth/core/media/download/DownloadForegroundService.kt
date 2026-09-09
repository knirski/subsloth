package net.subsloth.core.media.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service that keeps the download-transfer pipeline alive in
 * the background and surfaces progress in a notification.
 *
 * It holds no transfer logic of its own — [DownloadTransferCoordinator]
 * (owned by `AppContainer`) drives the transfers and calls the static
 * [start]/[updateProgress]/[stop] helpers. START_NOT_STICKY: the
 * coordinator, not the service, is the source of truth; when the
 * container's scope dies the notification dies with it, and the next
 * transfer restarts the service.
 */
class DownloadForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(this, activeCount = 1)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "downloads"
        const val CHANNEL_ID_SILENT = "downloads_silent"
        const val CHANNEL_NAME = "Downloads"
        private const val NOTIFICATION_ID = 1001

        /** Starts the foreground service (process-wide, idempotent). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, DownloadForegroundService::class.java))
        }

        /** Posts (or replaces) the download-progress notification. */
        fun updateProgress(context: Context, activeCount: Int, progressPercent: Int) {
            createChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(context, activeCount, progressPercent))
        }

        /** Removes the notification and stops the service. */
        fun stop(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }

        private fun createChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Download progress notifications"
            }
            manager.createNotificationChannel(channel)
            val silentChannel = NotificationChannel(
                CHANNEL_ID_SILENT,
                "$CHANNEL_NAME (Silent)",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Silent download progress notifications"
            }
            manager.createNotificationChannel(silentChannel)
        }

        @Suppress("Deprecation")
        private fun buildNotification(context: Context, activeCount: Int, progressPercent: Int = 0): Notification {
            val channelId = if (progressPercent == 0) CHANNEL_ID_SILENT else CHANNEL_ID
            return Notification.Builder(context, channelId)
                .setContentTitle("Downloading $activeCount file${if (activeCount != 1) "s" else ""}")
                .setContentText(
                    if (progressPercent > 0) "$progressPercent% complete" else "Preparing download",
                )
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progressPercent, progressPercent == 0)
                .setOngoing(true)
                .build()
        }
    }
}
