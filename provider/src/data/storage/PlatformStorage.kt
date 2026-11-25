package com.mrboomdev.scrollix.data.storage

/** One file below a storage directory, with the metadata eviction needs. */
data class StorageFileEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedAt: Long
)

/** Platform file storage used by providers and the app shell. */
interface PlatformStorage {
    val rootDirectory: String

    fun readText(relativePath: String): String?
    fun writeText(relativePath: String, text: String)

    /**
     * Appends to a text file, creating it (and its directory) when needed.
     * The default is read-then-write so simple test storages work; platforms
     * override it with a real append.
     */
    fun appendText(relativePath: String, text: String) {
        writeText(relativePath, readText(relativePath).orEmpty() + text)
    }
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

    /**
     * Files below [relativePath] with their size and modification time. The
     * default keeps storages that cannot expose metadata working; platforms
     * override it so the media cache can evict the oldest entries first.
     */
    fun listEntries(relativePath: String): List<StorageFileEntry> {
        return listFiles(relativePath).map { path ->
            StorageFileEntry(
                relativePath = path,
                sizeBytes = 0L,
                lastModifiedAt = 0L
            )
        }
    }
}
