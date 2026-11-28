package com.mrboomdev.scrollix.data.provider

/**
 * Media reading capability of an account session.
 *
 * Image and video surfaces depend on this instead of the whole
 * [AccountSession]: they only need bytes and download progress, and a fake
 * implementation for tests does not have to satisfy the rest of the session.
 * Media is addressed by TDLib's durable remote unique id where possible,
 * because runtime file ids change between launches.
 */
interface MediaSession {
    val accountId: String

    suspend fun loadFile(fileId: Int): Result<ByteArray>

    /**
     * Loads bytes only when the runtime file id still belongs to the durable
     * unique id. A file id restored from cache can be reassigned after a
     * restart, and blindly using it shows a different file's bytes.
     * Implementations that cannot verify the id fall back to [loadFile].
     */
    suspend fun loadFileIfUnique(fileId: Int, uniqueId: String): Result<ByteArray> =
        loadFile(fileId)

    /**
     * Reads message media, preferring the durable cached entry. When a unique
     * id is known, the file id is used only if it still resolves to it.
     */
    suspend fun loadMediaBytes(fileId: Int?, uniqueId: String?): ByteArray? {
        if (uniqueId != null) {
            loadCachedMedia(uniqueId)?.let { return it }
            fileId?.let { id ->
                loadFileIfUnique(id, uniqueId).getOrNull()?.let { return it }
            }

            return null
        }

        return fileId?.let { id -> loadFile(id).getOrNull() }
    }

    /**
     * Reads a file that was cached in a previous run, without asking the
     * provider for it. Media is addressed by its durable remote unique id, so
     * this works even when the runtime file ids of a cached dialog are stale.
     * Returns null when nothing is cached under that id.
     */
    suspend fun loadCachedMedia(remoteUniqueId: String): ByteArray? = null

    /**
     * Fraction of [fileId] already downloaded, 0..1, or null when the provider
     * cannot report it. The UI polls this only while a download it started is
     * still running.
     */
    fun mediaDownloadProgress(fileId: Int): Float? = null

    /** Stops an in-flight download. Unknown or already finished ids are ignored. */
    suspend fun cancelMediaDownload(fileId: Int) = Unit
}
