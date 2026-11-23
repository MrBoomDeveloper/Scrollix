package com.mrboomdev.scrollix

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.mrboomdev.scrollix.data.storage.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun tdlibDeviceModel(): String = Build.MODEL ?: "Android"

actual fun tdlibSystemVersion(): String = "SDK ${Build.VERSION.SDK_INT}"

// Android's location APIs require runtime permissions and a location client
// that this build does not ship, so the picker falls back to typed or dragged
// coordinates there.
actual suspend fun currentLocation(): Pair<Double, Double>? = null

actual fun openDialer(phoneNumber: String) {
    val intent = Intent(
        Intent.ACTION_DIAL,
        Uri.parse("tel:${Uri.encode(phoneNumber)}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { AndroidAppContext.context.startActivity(intent) }
}

private const val MessagesChannelId = "messages"
private const val MessagesChannelName = "Messages"

/** Tracks whether a visible activity is in front, so background messages only notify. */
@Volatile
private var appInForeground = false

fun setAppNotificationsForeground(foreground: Boolean) {
    appInForeground = foreground
}

/** Creates the notification channel. Safe to call on every application start. */
fun createNotificationChannels() {
    val context = AndroidAppContext.context
    val manager = context.getSystemService(NotificationManager::class.java) ?: return

    if (manager.getNotificationChannel(MessagesChannelId) != null) return

    val channel = NotificationChannel(
        MessagesChannelId,
        MessagesChannelName,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "New messages from your chats"
    }

    manager.createNotificationChannel(channel)
}

actual fun showDesktopNotification(
    title: String,
    body: String,
    accountId: String?,
    providerDialogId: String?
) {
    // The user is already looking at the app; the chat list itself is the
    // notification in that case.
    if (appInForeground) return

    val context = AndroidAppContext.context

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    createNotificationChannels()

    val notificationId = if (accountId != null && providerDialogId != null) {
        // One notification per chat: a new message replaces the previous one
        // instead of stacking duplicates.
        "$accountId:$providerDialogId".hashCode()
    } else {
        title.hashCode()
    }

    val contentIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            if (accountId != null && providerDialogId != null) {
                putExtra(NotificationExtras.AccountId, accountId)
                putExtra(NotificationExtras.ProviderDialogId, providerDialogId)
            }
        }
        ?.let { intent ->
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    val notification = Notification.Builder(context, MessagesChannelId)
        .setSmallIcon(android.R.drawable.sym_action_chat)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setWhen(System.currentTimeMillis())
        .setShowWhen(true)
        .apply { contentIntent?.let(::setContentIntent) }
        .build()

    runCatching { manager.notify(notificationId, notification) }
}

actual fun isAppInForeground(): Boolean = appInForeground

actual suspend fun fetchUrlBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Scrollix/1.0 (Telegram client)")
        }

        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { stream -> stream.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
