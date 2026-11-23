package com.mrboomdev.scrollix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

// The desktop build reports the same TDLib parameters an Android 16 (API 36)
// phone sends, so the session shows up as a regular mobile device instead of
// a JVM desktop entry.
actual fun tdlibDeviceModel(): String = "Desktop"

actual fun tdlibSystemVersion(): String = "1.0.0"

actual fun openDialer(phoneNumber: String) = Unit

actual fun showDesktopNotification(
    title: String,
    body: String,
    accountId: String?,
    providerDialogId: String?
) {
    // Linux desktops implement the freedesktop notification spec through
    // notify-send. Other systems have no implementation here yet.
    if (!System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)) {
        return
    }

    runCatching {
        ProcessBuilder(
            "notify-send",
            "--app-name=Scrollix",
            "--expire-time=5000",
            title,
            body
        ).start()
    }
}

// The desktop window is part of the process that posts the notifications, so
// the open chat stays silent exactly like before. Background chat
// notifications are a mobile concern.
actual fun isAppInForeground(): Boolean = true

actual suspend fun currentLocation(): Pair<Double, Double>? {
    // Linux has no desktop geolocation service. The public IP gives a city
    // level approximation, which is enough for a location picker and does not
    // require any permissions.
    val text = fetchUrlBytes("https://ipapi.co/json/")?.decodeToString() ?: return null
    val latitude = Regex("\"latitude\"\\s*:\\s*(-?[0-9.]+)")
        .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val longitude = Regex("\"longitude\"\\s*:\\s*(-?[0-9.]+)")
        .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

    if (latitude == null || longitude == null) return null
    return latitude to longitude
}

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
