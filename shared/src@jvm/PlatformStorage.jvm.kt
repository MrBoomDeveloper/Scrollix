package com.mrboomdev.scrollix.data.storage

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import com.mrboomdev.scrollix.data.AppMode

private class JvmPlatformStorage : PlatformStorage {
    // A test-server build stores everything in .scrollix-test next to the
    // regular profile instead of sharing it.
    override val rootDirectory: String = File(
        System.getProperty("user.home"),
        ".${AppMode.storageProfileName}"
    ).absolutePath

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

    override fun appendText(relativePath: String, text: String) {
        val file = file(relativePath)
        file.parentFile?.mkdirs()
        file.appendText(text)
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

    override fun listEntries(relativePath: String): List<StorageFileEntry> {
        val directory = file(relativePath)
        if (!directory.isDirectory) return emptyList()

        return directory.walkTopDown()
            .filter { it.isFile }
            .map { entry ->
                StorageFileEntry(
                    relativePath = entry.relativeTo(directory).path.replace(File.separatorChar, '/'),
                    sizeBytes = entry.length(),
                    lastModifiedAt = entry.lastModified()
                )
            }
            .toList()
    }
}

actual fun createPlatformStorage(): PlatformStorage = JvmPlatformStorage()

actual fun readFileBytes(path: String): ByteArray? {
    return runCatching {
        File(path).takeIf { it.isFile }?.readBytes()
    }.getOrNull()
}

private fun downloadsDirectory(): File {
    return File(System.getProperty("user.home"), "Downloads/Scrollix").apply { mkdirs() }
}

actual fun deviceDownloadsDirectory(): String = downloadsDirectory().absolutePath

actual suspend fun listDeviceDownloads(): List<DeviceDownloadedFile> {
    return withContext(Dispatchers.IO) {
        downloadsDirectory().listFiles()
            ?.filter(File::isFile)
            ?.sortedByDescending(File::lastModified)
            ?.map { file ->
                DeviceDownloadedFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    modifiedAt = file.lastModified()
                )
            }
            .orEmpty()
    }
}

actual suspend fun saveFileToDevice(fileName: String, bytes: ByteArray): String? {
    val safeName = fileName.ifBlank { "file" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")

    // Let the user pick both the directory and the file name. Cancelling the
    // dialog is a normal outcome, not a failure. The picker starts in the
    // Downloads/Scrollix folder which the Downloads tab lists.
    val target = runCatching {
        FileKit.openFileSaver(
            suggestedName = safeName,
            defaultExtension = safeName.substringAfterLast('.', "")
                .takeIf { extension -> extension.isNotBlank() },
            directory = PlatformFile(downloadsDirectory())
        )
    }.getOrNull() ?: return null

    return runCatching {
        val file = File(target.path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        file.absolutePath
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
