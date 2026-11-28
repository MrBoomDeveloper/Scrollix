package com.mrboomdev.scrollix.features.dialog.composer

import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.features.dialog.message.formatMessageDateTime

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val MinuteMillis = 60_000L

/** One quick scheduling choice offered by the composer picker. */
internal data class SchedulePreset(
    val label: String,
    val scheduling: MessageScheduling
)

/**
 * The quick choices of the scheduling picker. They are computed from the
 * current time so "Tomorrow at 9:00" follows the device time zone.
 */
internal fun schedulePresets(
    nowMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault()
): List<SchedulePreset> {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    val tomorrowMorning = (today + DatePeriod(days = 1))
        .atTime(hour = 9, minute = 0)
        .toInstant(zone)
        .toEpochMilliseconds()

    return listOf(
        SchedulePreset(
            label = "In 30 minutes",
            scheduling = MessageScheduling.At(nowMillis + 30 * MinuteMillis)
        ),
        SchedulePreset(
            label = "In 1 hour",
            scheduling = MessageScheduling.At(nowMillis + 60 * MinuteMillis)
        ),
        SchedulePreset(
            label = "In 3 hours",
            scheduling = MessageScheduling.At(nowMillis + 180 * MinuteMillis)
        ),
        SchedulePreset(
            label = "Tomorrow at 9:00",
            scheduling = MessageScheduling.At(tomorrowMorning)
        ),
        SchedulePreset(
            label = "Send when online",
            scheduling = MessageScheduling.WhenOnline
        )
    )
}

/**
 * Parses the custom picker fields, which stay plain text so the same dialog
 * works on Android and desktop without a platform date dialog.
 */
internal fun parseScheduleDateTime(
    date: String,
    time: String,
    zone: TimeZone = TimeZone.currentSystemDefault()
): Long? {
    val dateMatch = DatePattern.matchEntire(date.trim()) ?: return null
    val timeMatch = TimePattern.matchEntire(time.trim()) ?: return null

    val year = dateMatch.groupValues[1].toIntOrNull() ?: return null
    val month = dateMatch.groupValues[2].toIntOrNull() ?: return null
    val day = dateMatch.groupValues[3].toIntOrNull() ?: return null
    val hour = timeMatch.groupValues[1].toIntOrNull() ?: return null
    val minute = timeMatch.groupValues[2].toIntOrNull() ?: return null

    if (hour !in 0..23 || minute !in 0..59) return null

    val localDate = runCatching { LocalDate(year, month, day) }.getOrNull() ?: return null

    return runCatching {
        localDate.atTime(hour = hour, minute = minute)
            .toInstant(zone)
            .toEpochMilliseconds()
    }.getOrNull()
}

internal fun MessageScheduling.scheduleLabel(): String {
    return when (this) {
        is MessageScheduling.At -> formatMessageDateTime(sendDateMillis)
        MessageScheduling.WhenOnline -> "When online"
    }
}

private val DatePattern = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
private val TimePattern = Regex("""(\d{1,2}):(\d{2})""")
