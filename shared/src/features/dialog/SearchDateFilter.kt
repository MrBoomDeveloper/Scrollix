package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.storage.currentTimeMillis

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Date window offered next to the media chips. It narrows the results the page
 * already returned instead of starting another provider query, so changing it
 * is instant and works for every provider.
 */
internal enum class SearchDateFilter(val label: String, val window: Duration) {
    AnyTime("Any time", Duration.ZERO),
    LastWeek("Last 7 days", 7.days),
    LastMonth("Last 30 days", 30.days),
    LastYear("Last year", 365.days)
}

/** Oldest timestamp [filter] includes, in millis; 0 means no lower bound. */
internal fun SearchDateFilter.minTimestamp(nowMillis: Long): Long {
    if (window == Duration.ZERO) return 0L

    return nowMillis - window.inWholeMilliseconds
}

/** Keeps the messages inside [filter]'s window, preserving their order. */
internal fun filterMessagesByDate(
    messages: List<ChatMessage>,
    filter: SearchDateFilter,
    nowMillis: Long
): List<ChatMessage> {
    val from = filter.minTimestamp(nowMillis)
    if (from <= 0L) return messages

    return messages.filter { message -> message.timestamp >= from }
}

/** Applies the media chips and the date window to one provider page. */
internal fun filteredSearchResults(
    raw: List<ChatMessage>,
    mediaFilter: DialogSearchFilter,
    dateFilter: SearchDateFilter,
    nowMillis: Long
): List<ChatMessage> = filterMessagesByDate(
    messages = filterSearchMessages(raw, mediaFilter),
    filter = dateFilter,
    nowMillis = nowMillis
)

/** Re-filters the loaded in-chat page without asking the provider again. */
internal fun DialogScreenViewModel.applySearchFilters() {
    searchResults = filteredSearchResults(
        raw = rawSearchResults,
        mediaFilter = searchFilter,
        dateFilter = searchDateFilter,
        nowMillis = currentTimeMillis()
    )

    recomputeDerived()
}

internal fun DialogScreenViewModel.setSearchDateFilter(filter: SearchDateFilter) {
    if (searchDateFilter == filter) return

    searchDateFilter = filter
    applySearchFilters()
}
