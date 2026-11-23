package com.mrboomdev.scrollix

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.mrboomdev.scrollix.data.api.ApiManager

private const val ServiceChannelId = "background_connection"
private const val ServiceChannelName = "Background connection"
private const val ServiceNotificationId = 0x5C001
private const val RestartAction = "com.mrboomdev.scrollix.notifications.RESTART_SERVICE"

/**
 * Keeps the signed in sessions and their notification listeners alive while the
 * UI is not on screen. Android may still stop the process under memory
 * pressure, so the service is sticky and boot/reboot receivers start it again.
 */
class ScrollixNotificationService : Service() {
    override fun onCreate() {
        super.onCreate()
        promoteToForeground()
        ApiManager.init()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        ApiManager.init()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        createServiceChannel(this)

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ServiceNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(ServiceNotificationId, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }

        val contentIntent = launchIntent?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return Notification.Builder(this, ServiceChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Scrollix")
            .setContentText("Keeping chats connected for notifications")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .apply { contentIntent?.let(::setContentIntent) }
            .build()
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, ScrollixNotificationService::class.java)
                )
            }
        }

        fun scheduleRestart(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val restartIntent = Intent(context, NotificationServiceBootReceiver::class.java)
                .setAction(RestartAction)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 5_000L,
                    pendingIntent
                )
            }
        }
    }
}

/** Restarts the service after boot, an app update, or a task swipe. */
class NotificationServiceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            RestartAction -> ScrollixNotificationService.start(context)
        }
    }
}

private fun createServiceChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(ServiceChannelId) != null) return

    val channel = NotificationChannel(
        ServiceChannelId,
        ServiceChannelName,
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Keeps Scrollix connected for message notifications"
        setShowBadge(false)
    }

    manager.createNotificationChannel(channel)
}
