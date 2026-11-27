package com.mrboomdev.scrollix

import com.mrboomdev.scrollix.data.storage.AndroidAppContext

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun isDesktopPlatform(): Boolean = false

actual fun tdlibDeviceModel(): String = Build.MODEL ?: "Android"

actual fun tdlibSystemVersion(): String = "SDK ${Build.VERSION.SDK_INT}"

/**
 * Set by the Android activity so the picker can raise the system permission
 * prompt. Null while no activity is attached.
 */
var requestLocationPermission: (suspend () -> Boolean)? = null

private fun hasLocationPermission(): Boolean {
    val context = AndroidAppContext.context

    return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}

actual fun isLocationSupported(): Boolean {
    val context = AndroidAppContext.context

    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
        return false
    }

    // A device without any provider (rare, but possible on stripped builds)
    // cannot be asked to turn location on, so the entry point is hidden.
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return manager.allProviders.isNotEmpty()
}

actual fun isLocationEnabled(): Boolean {
    val manager = AndroidAppContext.context
        .getSystemService(LocationManager::class.java)
        ?: return false

    return manager.isLocationEnabled
}

actual fun openMapApplication(
    latitude: Double,
    longitude: Double,
    label: String
): Boolean {
    val point = "$latitude,$longitude"
    val uri = Uri.parse("geo:$point?q=$point(${Uri.encode(label)})")

    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // No map application is a normal case (a bare emulator), not an error:
    // the caller opens Google Maps in the browser instead.
    return runCatching { AndroidAppContext.context.startActivity(intent) }.isSuccess
}

actual fun openLocationSettings() {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { AndroidAppContext.context.startActivity(intent) }
}

/**
 * Reads the last known fix, then waits briefly for a fresh one. The picker
 * calls this once when it opens, so no location updates stay registered.
 */
actual suspend fun currentLocation(): LocationFix {
    if (!hasLocationPermission()) {
        val granted = withContext(Dispatchers.Main) {
            requestLocationPermission?.invoke() ?: false
        }

        if (!granted) return LocationFix.PermissionDenied
    }

    if (!isLocationEnabled()) return LocationFix.Disabled

    val context = AndroidAppContext.context
    val manager = context.getSystemService(LocationManager::class.java)
        ?: return LocationFix.Unavailable

    // GPS needs the fine permission; with only the approximate grant the
    // network provider is the one that may be read.
    val fineGranted = context.checkSelfPermission(
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val providers = if (fineGranted) {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    } else {
        listOf(LocationManager.NETWORK_PROVIDER)
    }

    val provider = providers.firstOrNull { candidate ->
        manager.isProviderEnabled(candidate)
    } ?: return LocationFix.Disabled

    val cached = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    if (cached != null) return LocationFix.Position(cached.latitude, cached.longitude)

    // A provider can still refuse the read (a revoked grant, a disabled
    // radio), so the picker gets "no position" instead of a crash.
    val fresh = runCatching {
        withTimeoutOrNull(8_000L) {
            awaitSingleLocation(manager, provider)
        }
    }.getOrNull()

    return fresh?.let { location ->
        LocationFix.Position(location.latitude, location.longitude)
    } ?: LocationFix.Unavailable
}

private suspend fun awaitSingleLocation(
    manager: LocationManager,
    provider: String
): Location = suspendCancellableCoroutine { continuation ->
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            runCatching { manager.removeUpdates(this) }

            if (continuation.isActive) {
                continuation.resume(location)
            }
        }

        override fun onProviderDisabled(provider: String) = Unit
    }

    continuation.invokeOnCancellation {
        runCatching { manager.removeUpdates(listener) }
    }

    runCatching {
        manager.requestLocationUpdates(
            provider,
            0L,
            0f,
            AndroidAppContext.context.mainExecutor,
            listener
        )
    }.onFailure { failure ->
        if (continuation.isActive) {
            continuation.resumeWithException(failure)
        }
    }
}

actual fun openDialer(phoneNumber: String) {
    val intent = Intent(
        Intent.ACTION_DIAL,
        Uri.parse("tel:${Uri.encode(phoneNumber)}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { AndroidAppContext.context.startActivity(intent) }
}

private const val MessagesChannelId = "messages"
private const val MessagesChannelName = "Messages"
private const val NotificationGroupPrefix = "scrollix.account."

/** Tracks whether a visible activity is in front, so background messages only notify. */
@Volatile
private var appInForeground = false

fun setAppNotificationsForeground(foreground: Boolean) {
    appInForeground = foreground

    // Tapping a chat dismisses its own notification, which can leave a
    // summary describing a stack that is no longer there.
    if (foreground) {
        runCatching { cancelStaleNotificationSummaries() }
    }
}

private fun isGroupSummary(notification: Notification): Boolean {
    return notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
}

/**
 * Posts the silent summary that collapses the unread chats of one account into
 * a single stack entry. One chat needs no summary, so an existing one is
 * removed instead of duplicating its only child.
 */
private fun updateAccountSummary(
    context: Context,
    manager: NotificationManager,
    groupKey: String
) {
    val summaryId = groupKey.hashCode()
    val children = manager.activeNotifications.count { status ->
        status.notification.group == groupKey && !isGroupSummary(status.notification)
    }

    if (children < 2) {
        manager.cancel(summaryId)
        return
    }

    val contentIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        ?.let { intent ->
            PendingIntent.getActivity(
                context,
                summaryId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    val summary = Notification.Builder(context, MessagesChannelId)
        .setSmallIcon(android.R.drawable.sym_action_chat)
        .setContentTitle("New messages")
        .setContentText("$children new messages")
        .setGroup(groupKey)
        .setGroupSummary(true)
        // The children alert; the summary only holds them together.
        .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
        .setAutoCancel(true)
        .apply { contentIntent?.let(::setContentIntent) }
        .build()

    manager.notify(summaryId, summary)
}

private fun cancelStaleNotificationSummaries() {
    val manager = AndroidAppContext.context
        .getSystemService(NotificationManager::class.java) ?: return
    val active = manager.activeNotifications

    for (status in active) {
        val group = status.notification.group ?: continue
        if (!isGroupSummary(status.notification)) continue

        val children = active.count { child ->
            child.notification.group == group && !isGroupSummary(child.notification)
        }

        if (children < 2) {
            manager.cancel(status.id)
        }
    }
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

    val groupKey = accountId?.let { id -> NotificationGroupPrefix + id }

    val builder = Notification.Builder(context, MessagesChannelId)
        .setSmallIcon(android.R.drawable.sym_action_chat)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setWhen(System.currentTimeMillis())
        .setShowWhen(true)

    if (groupKey != null) {
        // One group per account, so chats of different accounts never stack
        // into a single undifferentiated pile.
        builder
            .setGroup(groupKey)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
    }

    contentIntent?.let(builder::setContentIntent)

    runCatching {
        manager.notify(notificationId, builder.build())

        if (groupKey != null) {
            updateAccountSummary(context, manager, groupKey)
        }
    }
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
