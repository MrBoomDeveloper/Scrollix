package com.mrboomdev.scrollix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import com.mrboomdev.scrollix.data.outbox.OutboxManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val UploadChannelId = "uploads"
private const val UploadChannelName = "Uploads"
private const val UploadNotificationId = 0x5C002

/** Broadcast action of the notification's "Pause all" button. */
private const val ActionPauseAllUploads = "com.mrboomdev.scrollix.uploads.PAUSE_ALL"

/**
 * Anchors the process in the foreground while files are uploading, so the
 * system does not reclaim a send that the user started. The queue itself lives
 * in the app process; this service only keeps it alive and shows the aggregate
 * progress of every active file send. It stops as soon as the queue drains.
 */
class UploadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createUploadChannel(this)
        promoteToForeground(progressNotification(files = 0, fraction = null))

        scope.launch {
            OutboxManager.uploadSummary.collect { summary ->
                if (!summary.active) {
                    stopSelf()
                    return@collect
                }

                updateNotification(
                    progressNotification(files = summary.files, fraction = summary.progress)
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(notification: Notification) {
        startForeground(
            UploadNotificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun updateNotification(notification: Notification) {
        getSystemService(NotificationManager::class.java)
            ?.notify(UploadNotificationId, notification)
    }

    private fun progressNotification(files: Int, fraction: Float?): Notification {
        val title = when {
            files <= 0 -> "Uploading"
            files == 1 -> "Uploading 1 file"
            else -> "Uploading $files files"
        }

        val builder = Notification.Builder(this, UploadChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentIntent(launchIntent())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (fraction != null) {
            val percent = (fraction * 100).toInt().coerceIn(0, 100)
            builder.setContentText("$percent%")
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                "Pause all",
                pauseIntent()
            ).build()
        )

        return builder.build()
    }

    private fun pauseIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        0,
        Intent(this, UploadActionReceiver::class.java).setAction(ActionPauseAllUploads),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
            ?: return null

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Starts only when the outbox actually has bytes left to send. */
        fun startIfNeeded(context: Context) {
            if (!OutboxManager.hasActiveUploads()) return

            // A start from onStop can be refused by the system; the uploads
            // continue in the process and are retried either way.
            runCatching {
                context.startForegroundService(Intent(context, UploadService::class.java))
            }
        }

        /** The UI shows the progress itself, so the anchor is not needed. */
        fun stopIfRunning(context: Context) {
            runCatching { context.stopService(Intent(context, UploadService::class.java)) }
        }
    }
}

/**
 * Notification action: pauses every queued file send without discarding it.
 * Cancelling a message is destructive and stays in the app, where it is
 * confirmed.
 */
class UploadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ActionPauseAllUploads) {
            OutboxManager.pauseAllUploads()
        }
    }
}

private fun createUploadChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(UploadChannelId) != null) return

    manager.createNotificationChannel(
        NotificationChannel(
            UploadChannelId,
            UploadChannelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progress of files that are being sent"
            setShowBadge(false)
        }
    )
}
