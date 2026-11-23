package com.mrboomdev.scrollix.data.max.network

/**
 * The MAX file hosts are picky about the client: a custom user agent made
 * uploads fail while the web client's agent works, the same one the socket
 * uses.
 */
internal const val MaxUploadUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"

/**
 * Uploads bytes as a `multipart/form-data` file field to the URL returned by
 * `FILE_UPLOAD` (87) or `PHOTO_UPLOAD` (80). Returns the response body for a
 * 2xx response and null otherwise.
 */
internal expect suspend fun uploadMaxFile(
    url: String,
    fileName: String,
    bytes: ByteArray,
    contentType: String = "application/octet-stream"
): String?

/**
 * Uploads raw bytes (audio and video endpoints take the body directly with a
 * `Content-Range`, not multipart). Returns the response body or null on a
 * non-2xx response.
 */
internal expect suspend fun uploadMaxRaw(
    url: String,
    bytes: ByteArray,
    contentType: String = "application/octet-stream"
): String?
