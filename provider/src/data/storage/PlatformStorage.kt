package com.mrboomdev.scrollix.data.storage

/** Platform file storage used by providers and the app shell. */
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
