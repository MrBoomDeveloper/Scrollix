package com.mrboomdev.scrollix.data.vk.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}

internal actual suspend fun vkHttpRequest(
    url: String,
    method: String,
    contentType: String?,
    body: ByteArray?,
    timeoutSeconds: Int
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val mediaType = contentType?.toMediaTypeOrNull()
        val requestBody = when {
            body != null -> body.toRequestBody(mediaType)
            method == "POST" -> ByteArray(0).toRequestBody(mediaType)
            else -> null
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Scrollix/1.0")
            .header("Accept", "application/json")
            .apply { if (requestBody != null) method(method, requestBody) else get() }
            .build()

        client.newBuilder()
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            }
    }.getOrNull()
}

internal actual suspend fun vkReadFileBytes(path: String): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching { File(path).readBytes() }.getOrNull()
    }
