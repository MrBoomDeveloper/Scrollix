package com.mrboomdev.scrollix.data.storage

interface PlatformStorage {
    val rootDirectory: String

    fun readText(relativePath: String): String?
    fun writeText(relativePath: String, text: String)
    fun readBytes(relativePath: String): ByteArray?
    fun writeBytes(relativePath: String, bytes: ByteArray)
    fun delete(relativePath: String)
    fun ensureDirectory(relativePath: String): String
    fun absolutePath(relativePath: String): String
    fun exists(relativePath: String): Boolean

    /** Relative paths of every file below [relativePath]. */
    fun listFiles(relativePath: String): List<String>

    /** Total size in bytes of everything below [relativePath]. */
    fun size(relativePath: String): Long
}

expect fun createPlatformStorage(): PlatformStorage
expect fun readFileBytes(path: String): ByteArray?
expect fun randomId(): String
expect fun currentTimeMillis(): Long
expect fun formatTime(timestamp: Long): String
expect fun saveFileToDevice(fileName: String, bytes: ByteArray): String?
