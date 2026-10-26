package com.mrboomdev.scrollix.data.storage

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private class JvmPlatformStorage : PlatformStorage {
    override val rootDirectory: String =
        File(System.getProperty("user.home"), ".scrollix").absolutePath

    private fun file(relativePath: String): File = File(rootDirectory, relativePath)

    override fun readText(relativePath: String): String? {
        val file = file(relativePath)
        return if (file.isFile) file.readText() else null
    }

    override fun writeText(relativePath: String, text: String) {
        val file = file(relativePath)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            file.writeText(text)
            temporary.delete()
        }
    }

    override fun delete(relativePath: String) {
        file(relativePath).deleteRecursively()
    }

    override fun ensureDirectory(relativePath: String): String {
        return file(relativePath).apply { mkdirs() }.absolutePath
    }

    override fun absolutePath(relativePath: String): String = file(relativePath).absolutePath

    override fun exists(relativePath: String): Boolean = file(relativePath).exists()
}

actual fun createPlatformStorage(): PlatformStorage = JvmPlatformStorage()

actual fun readFileBytes(path: String): ByteArray? {
    return runCatching {
        File(path).takeIf { it.isFile }?.readBytes()
    }.getOrNull()
}

actual fun randomId(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""

    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
    val pattern = if (dateTime.toLocalDate() == LocalDate.now(zone)) "HH:mm" else "MMM d"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(dateTime)
}
