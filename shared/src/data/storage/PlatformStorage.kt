package com.mrboomdev.scrollix.data.storage

interface PlatformStorage {
    val rootDirectory: String

    fun readText(relativePath: String): String?
    fun writeText(relativePath: String, text: String)
    fun delete(relativePath: String)
    fun ensureDirectory(relativePath: String): String
    fun absolutePath(relativePath: String): String
    fun exists(relativePath: String): Boolean
}

expect fun createPlatformStorage(): PlatformStorage
expect fun readFileBytes(path: String): ByteArray?
expect fun randomId(): String
expect fun currentTimeMillis(): Long
expect fun formatTime(timestamp: Long): String
