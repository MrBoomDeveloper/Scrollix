package com.mrboomdev.scrollix.features.search

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.features.dialog.SearchDateFilter
import com.mrboomdev.scrollix.features.dialog.minTimestamp

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

internal const val DayMillis = 24L * 60 * 60 * 1000

/**
 * Date narrowing of the global message search: either one of the presets or a
 * custom range typed as `YYYY-MM-DD` bounds. The two are exclusive, so picking
 * a preset clears the typed fields and typing a bound resets the preset.
 */
internal data class MessageDateRange(
    val preset: SearchDateFilter = SearchDateFilter.AnyTime,
    /** Custom lower bound; empty leaves the range open on that side. */
    val fromDate: String = "",
    /** Custom upper bound; empty leaves the range open on that side. */
    val toDate: String = ""
) {
    val isCustom: Boolean
        get() = fromDate.isNotBlank() || toDate.isNotBlank()

    val isActive: Boolean
        get() = preset != SearchDateFilter.AnyTime || isCustom
}

/**
 * Start of [text]'s day, or its last millisecond when [endOfDay] is set, so a
 * custom range includes both of its boundary days. Null for a blank or
 * unparsable field.
 */
internal fun parseDateBoundMillis(
    text: String,
    endOfDay: Boolean,
    zone: TimeZone = TimeZone.currentSystemDefault()
): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val date = runCatching { LocalDate.parse(trimmed) }.getOrNull() ?: return null
    val start = date.atStartOfDayIn(zone).toEpochMilliseconds()

    return if (endOfDay) start + DayMillis - 1 else start
}

/** True when a non-blank field cannot be read as a date. */
internal fun isInvalidDateBound(
    text: String,
    endOfDay: Boolean,
    zone: TimeZone = TimeZone.currentSystemDefault()
): Boolean = text.isNotBlank() && parseDateBoundMillis(text, endOfDay, zone) == null

/**
 * Epoch-millisecond bounds of [range]; 0 on a side means "no bound". A custom
 * range wins over the preset and a blank or unparsable bound leaves that side
 * open. The same bounds are sent to the provider and applied to the page.
 */
internal fun MessageDateRange.boundsMillis(
    nowMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault()
): Pair<Long, Long> {
    val from = if (isCustom) {
        parseDateBoundMillis(fromDate, endOfDay = false, zone) ?: 0L
    } else {
        preset.minTimestamp(nowMillis)
    }

    val to = if (isCustom) {
        parseDateBoundMillis(toDate, endOfDay = true, zone) ?: 0L
    } else {
        0L
    }

    return from to to
}

/**
 * Keeps the messages inside [range], preserving their order. A custom range
 * wins over the preset; a blank or unparsable bound leaves that side open.
 */
internal fun filterMessagesByRange(
    messages: List<ChatMessage>,
    range: MessageDateRange,
    nowMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault()
): List<ChatMessage> {
    val (from, to) = range.boundsMillis(nowMillis, zone)
    val upper = if (to <= 0L) Long.MAX_VALUE else to

    if (from <= 0L && upper == Long.MAX_VALUE) return messages

    return messages.filter { message -> message.timestamp in from..upper }
}
