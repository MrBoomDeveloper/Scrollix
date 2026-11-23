package com.mrboomdev.scrollix.data.vk.network

/**
 * Executes one HTTP request and returns the response body of a 2xx answer.
 *
 * VK's API, the upload servers and the Long Poll endpoint all speak plain
 * HTTP, so the provider keeps a single platform hook instead of one per
 * endpoint. [body] is already encoded by the caller (form or multipart), which
 * keeps the encoding logic testable in common code.
 *
 * Returns null for any transport failure or non-2xx status.
 */
internal expect suspend fun vkHttpRequest(
    url: String,
    method: String = "GET",
    contentType: String? = null,
    body: ByteArray? = null,
    timeoutSeconds: Int = 30
): String?

/** Reads a local attachment into memory for upload. Null when unreadable. */
internal expect suspend fun vkReadFileBytes(path: String): ByteArray?
