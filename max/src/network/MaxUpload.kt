package com.mrboomdev.scrollix.data.max.network

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
