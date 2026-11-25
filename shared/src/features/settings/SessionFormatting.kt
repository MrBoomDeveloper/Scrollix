package com.mrboomdev.scrollix.features.settings

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Formats a byte count with binary units, trimmed to one decimal. */
fun formatDataSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"

    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0

    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }

    val rounded = (value * 10).toLong() / 10.0
    val text = if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }

    return "$text ${units[index]}"
}

/** A locale-independent date for counters and old sessions. */
fun formatDataDate(
    timestampMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    if (timestampMillis <= 0) return "Unknown"

    return Instant.fromEpochMilliseconds(timestampMillis)
        .toLocalDateTime(timeZone)
        .date
        .toString()
}

/**
 * "Just now", "5 min ago", "2 h ago", "3 d ago", then an absolute date. The
 * caller passes [nowMillis] so the result is deterministic in tests.
 */
fun sessionTimestampLabel(
    nowMillis: Long,
    timestampMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    if (timestampMillis <= 0) return "Unknown"

    val elapsed = nowMillis - timestampMillis

    return when {
        elapsed < 60_000L -> "Just now"
        elapsed < 3_600_000L -> "${elapsed / 60_000L} min ago"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L} h ago"
        elapsed < 7 * 86_400_000L -> "${elapsed / 86_400_000L} d ago"
        else -> formatDataDate(timestampMillis, timeZone)
    }
}
