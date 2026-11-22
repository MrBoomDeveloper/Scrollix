package com.mrboomdev.scrollix

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** Opens the system dialer with [phoneNumber]; it never starts a call directly. */
expect fun openDialer(phoneNumber: String)

/**
 * Fetches [url] into memory, with a client User-Agent and a timeout.
 * Returns null on any failure. Used for the single map tile of the location
 * picker, so this is deliberately not a general purpose HTTP client.
 */
expect suspend fun fetchUrlBytes(url: String): ByteArray?
