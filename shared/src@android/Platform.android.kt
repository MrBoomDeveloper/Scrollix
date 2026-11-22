package com.mrboomdev.scrollix

import android.content.Intent
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

actual fun openDialer(phoneNumber: String) {
    val intent = Intent(
        Intent.ACTION_DIAL,
        Uri.parse("tel:${Uri.encode(phoneNumber)}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { AndroidAppContext.context.startActivity(intent) }
}

actual fun showDesktopNotification(title: String, body: String) = Unit

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
