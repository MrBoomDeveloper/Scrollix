package com.mrboomdev.scrollix.data.max.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal actual suspend fun uploadMaxFile(
    url: String,
    fileName: String,
    bytes: ByteArray,
    contentType: String
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val boundary = "----Scrollix${UUID.randomUUID().toString().replace("-", "")}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 90_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("User-Agent", MaxUploadUserAgent)
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { output ->
                output.write(
                    (
                        "--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
                            "Content-Type: $contentType\r\n\r\n"
                        ).toByteArray()
                )
                output.write(bytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val code = connection.responseCode

            if (code !in 200..299) {
                connection.errorStream?.use { it.readBytes() }
                return@withContext null
            }

            connection.inputStream?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

internal actual suspend fun uploadMaxRaw(
    url: String,
    bytes: ByteArray,
    contentType: String
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 90_000
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Content-Range", "bytes 0-${bytes.size - 1}/${bytes.size}")
            setRequestProperty("User-Agent", MaxUploadUserAgent)
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { output -> output.write(bytes) }

            val code = connection.responseCode

            if (code !in 200..299) {
                connection.errorStream?.use { it.readBytes() }
                return@withContext null
            }

            connection.inputStream?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
