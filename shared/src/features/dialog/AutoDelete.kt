package com.mrboomdev.scrollix.features.dialog

/**
 * Formats the time left before an auto-deleting message disappears. Seconds
 * are rounded up, so a message with any time left never reads "0s". Returns
 * null once the deadline passed, letting the caller drop the countdown row
 * instead of leaving a stale number behind.
 */
internal fun formatAutoDeleteRemaining(remainingMs: Long): String? {
    if (remainingMs <= 0) return null

    val seconds = (remainingMs + 999) / 1000

    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}:${twoDigits(seconds % 60)}"
        seconds < 86_400 -> "${seconds / 3600}:${twoDigits((seconds % 3600) / 60)}:" +
            twoDigits(seconds % 60)
        else -> "${seconds / 86_400}d"
    }
}

private fun twoDigits(value: Long): String = value.toString().padStart(2, '0')
