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

/**
 * Hands an already saved [path] to whatever application handles [mimeType]
 * (guessed from the extension when null). Returns false when nothing accepts
 * the file, so the caller can say so instead of doing nothing.
 */
expect fun openFileWith(path: String, mimeType: String? = null): Boolean

/**
 * Sends a saved [path] through the system share sheet. Desktop has no share
 * sheet, so it reports false and the action stays hidden there.
 */
expect fun shareFile(path: String, mimeType: String? = null): Boolean

/** Bytes held by temporary files Scrollix created (recordings, cached streams). */
expect suspend fun temporaryFilesBytes(): Long

/** Deletes those temporary files; returns how many bytes were freed. */
expect suspend fun clearTemporaryFiles(): Long
