package com.mrboomdev.scrollix.data.storage

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object AndroidAppContext {
    lateinit var context: Context
}

private class AndroidPlatformStorage : PlatformStorage {
    private val root = File(AndroidAppContext.context.noBackupFilesDir, "scrollix")

    override val rootDirectory: String = root.absolutePath

    private fun file(relativePath: String): File = File(root, relativePath)

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

    override fun readBytes(relativePath: String): ByteArray? {
        val file = file(relativePath)
        return if (file.isFile) file.readBytes() else null
    }

    override fun writeBytes(relativePath: String, bytes: ByteArray) {
        val file = file(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override fun delete(relativePath: String) {
        file(relativePath).deleteRecursively()
    }

    override fun ensureDirectory(relativePath: String): String {
        return file(relativePath).apply { mkdirs() }.absolutePath
    }

    override fun absolutePath(relativePath: String): String = file(relativePath).absolutePath

    override fun exists(relativePath: String): Boolean = file(relativePath).exists()

    override fun listFiles(relativePath: String): List<String> {
        val directory = file(relativePath)
        if (!directory.isDirectory) return emptyList()

        return directory.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(directory).path }
            .toList()
    }

    override fun size(relativePath: String): Long {
        val directory = file(relativePath)
        if (!directory.exists()) return 0

        return directory.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}

actual fun createPlatformStorage(): PlatformStorage = AndroidPlatformStorage()

actual fun readFileBytes(path: String): ByteArray? {
    return runCatching {
        File(path).takeIf { it.isFile }?.readBytes()
    }.getOrNull()
}

actual suspend fun saveFileToDevice(fileName: String, bytes: ByteArray): String? {
    return runCatching {
        val safeName = fileName.ifBlank { "file" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val directory = File(
            AndroidAppContext.context.getExternalFilesDir(null),
            "Downloads"
        ).apply { mkdirs() }

        var target = File(directory, safeName)
        var index = 1
        val dot = safeName.lastIndexOf('.')

        while (target.exists()) {
            val candidate = if (dot > 0) {
                "${safeName.substring(0, dot)} ($index)${safeName.substring(dot)}"
            } else {
                "$safeName ($index)"
            }

            target = File(directory, candidate)
            index++
        }

        target.writeBytes(bytes)
        target.absolutePath
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
