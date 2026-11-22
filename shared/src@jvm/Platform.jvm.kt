package com.mrboomdev.scrollix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun openDialer(phoneNumber: String) = Unit

actual fun showDesktopNotification(title: String, body: String) {
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
