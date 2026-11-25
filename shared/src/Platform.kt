package com.mrboomdev.scrollix

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * Whether the app runs as a desktop window. Desktop chats autofocus the
 * composer so typing starts immediately; mobile keeps the keyboard hidden
 * until the field is tapped.
 */
expect fun isDesktopPlatform(): Boolean

/**
 * TDLib `device_model`: the hardware model on Android (`Build.MODEL`) and
 * `Desktop` elsewhere, matching what the official Telegram clients report.
 */
expect fun tdlibDeviceModel(): String

/**
 * TDLib `system_version`: `SDK <api level>` on Android and the OS name/version
 * on the desktop, the same shape the official Telegram clients send.
 */
expect fun tdlibSystemVersion(): String

/** Opens the system dialer with [phoneNumber]; it never starts a call directly. */
expect fun openDialer(phoneNumber: String)

/**
 * Shows an OS-level notification for a new message. It is a no-op on
 * platforms without a notification service.
 *
 * [accountId] and [providerDialogId] identify the chat so a tap on the
 * notification can open it again.
 */
expect fun showDesktopNotification(
    title: String,
    body: String,
    accountId: String? = null,
    providerDialogId: String? = null
)

/** Whether a visible application surface is in front of the user. */
expect fun isAppInForeground(): Boolean

/**
 * Best-effort current position for the location picker. Desktop Linux has no
 * system geolocation API, so its implementation derives an approximate point
 * from the public IP address. Returns null when no position is available.
 */
expect suspend fun currentLocation(): Pair<Double, Double>?

/**
 * Fetches [url] into memory, with a client User-Agent and a timeout.
 * Returns null on any failure. Used for the single map tile of the location
 * picker, so this is deliberately not a general purpose HTTP client.
 */
expect suspend fun fetchUrlBytes(url: String): ByteArray?
