package com.mrboomdev.scrollix.data.model

/**
 * One device signed in to an account, as reported by the server. Telegram calls
 * these "active sessions"; other providers can map their own device lists.
 */
data class ActiveSessionInfo(
    val id: Long,
    val isCurrent: Boolean,
    val isPasswordPending: Boolean = false,
    val applicationName: String = "",
    val deviceModel: String = "",
    val platform: String = "",
    val systemVersion: String = "",
    val location: String = "",
    val ipAddress: String = "",
    val lastActiveAtMillis: Long = 0L,
    val logInAtMillis: Long = 0L,
    val isOfficialApplication: Boolean = false
) {
    /** Hardware label, e.g. "Pixel 9 · Android · 37". */
    val deviceLabel: String
        get() = listOf(deviceModel, platform, systemVersion)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
            .ifBlank { "Unknown device" }

    /** Best available place label, preferring the server's location text. */
    val locationLabel: String
        get() = location.ifBlank { ipAddress }
}
