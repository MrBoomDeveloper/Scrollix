package com.mrboomdev.scrollix.data.media

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

internal actual fun gunzip(bytes: ByteArray): ByteArray? {
    if (bytes.size < 2 || bytes[0] != 0x1F.toByte() || bytes[1] != 0x8B.toByte()) {
        return bytes
    }

    return runCatching {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    }.getOrNull()
}
