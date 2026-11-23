package com.mrboomdev.scrollix.data.storage

expect fun createPlatformStorage(): PlatformStorage
expect fun readFileBytes(path: String): ByteArray?
expect fun randomId(): String
expect fun currentTimeMillis(): Long
expect fun formatTime(timestamp: Long): String

/** A file saved by Scrollix into the platform Downloads/Scrollix directory. */
data class DeviceDownloadedFile(
    val name: String,
    val path: String,
    val size: Long,
    val modifiedAt: Long
)

/** Absolute path of the Downloads/Scrollix directory used by the Downloads tab. */
expect fun deviceDownloadsDirectory(): String

/** Files currently present in Downloads/Scrollix, newest first. */
expect suspend fun listDeviceDownloads(): List<DeviceDownloadedFile>

/**
 * Asks the user where to save [bytes] and writes them there. Desktop shows a
 * native save dialog (directory and file name); platforms without one fall
 * back to their downloads folder. Returns the saved path, or null when the
 * user cancelled or the write failed.
 */
expect suspend fun saveFileToDevice(fileName: String, bytes: ByteArray): String?
