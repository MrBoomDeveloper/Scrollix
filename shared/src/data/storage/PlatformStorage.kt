package com.mrboomdev.scrollix.data.storage

expect fun createPlatformStorage(): PlatformStorage
expect fun readFileBytes(path: String): ByteArray?
expect fun randomId(): String
expect fun currentTimeMillis(): Long
expect fun formatTime(timestamp: Long): String
/**
 * Asks the user where to save [bytes] and writes them there. Desktop shows a
 * native save dialog (directory and file name); platforms without one fall
 * back to their downloads folder. Returns the saved path, or null when the
 * user cancelled or the write failed.
 */
expect suspend fun saveFileToDevice(fileName: String, bytes: ByteArray): String?
