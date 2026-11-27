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

/** Result of a one-shot location lookup for the location picker. */
sealed interface LocationFix {
    data class Position(val latitude: Double, val longitude: Double) : LocationFix

    /** The user has not granted location access. */
    data object PermissionDenied : LocationFix

    /** Location services are switched off on the device. */
    data object Disabled : LocationFix

    /** No position could be obtained. */
    data object Unavailable : LocationFix
}

/**
 * Whether this device can produce a location at all: it has a location
 * provider, or a network lookup can stand in for one. When false, the location
 * entry point is hidden instead of opening a picker that can never work.
 */
expect fun isLocationSupported(): Boolean

/** Whether system location services are switched on right now. */
expect fun isLocationEnabled(): Boolean

/** Opens the system screen where location services can be turned on. */
expect fun openLocationSettings()

/**
 * Best-effort current position for the location picker. Android asks for the
 * runtime permission on first use and reads the GPS/network provider. Desktop
 * Linux has no cross-desktop geolocation service, so it derives an approximate
 * point from the public IP address.
 */
expect suspend fun currentLocation(): LocationFix

/**
 * Hands a point to the device's map application (a `geo:` intent on Android).
 * Returns false when nothing accepts it, so the caller can fall back to a
 * browser map instead.
 */
expect fun openMapApplication(latitude: Double, longitude: Double, label: String): Boolean

/**
 * Fetches [url] into memory, with a client User-Agent and a timeout.
 * Returns null on any failure. Used for the single map tile of the location
 * picker, so this is deliberately not a general purpose HTTP client.
 */
expect suspend fun fetchUrlBytes(url: String): ByteArray?
