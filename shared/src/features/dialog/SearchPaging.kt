package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.storage.currentTimeMillis

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** Page size of the in-chat message search. */
internal const val SearchPageSize = 50

/**
 * Cursor for the page after [messages]: one less than the oldest id we already
 * have, because providers treat the anchor as inclusive. Null when there is
 * nothing to page from or the id cannot go lower.
 */
internal fun searchPageCursor(messages: List<ChatMessage>): Long? {
    val oldest = messages.minOfOrNull { message -> message.messageId } ?: return null
    if (oldest == Long.MIN_VALUE) return null

    return oldest - 1
}

/** Appends an older [page], dropping repeated ids and keeping newest first. */
internal fun appendSearchPage(
    existing: List<ChatMessage>,
    page: List<ChatMessage>
): List<ChatMessage> {
    if (page.isEmpty()) return existing

    val known = existing.mapTo(mutableSetOf()) { message -> message.messageId }

    return (existing + page.filter { message -> known.add(message.messageId) })
        .sortedByDescending { message -> message.messageId }
}

/**
 * Loads the next page of the current in-chat search. The query is captured
 * before the request: if the user typed something else meanwhile, the page is
 * dropped instead of being appended to another query's results.
 */
internal fun DialogScreenViewModel.loadMoreSearchResults() {
    val currentSession = session ?: return
    if (!currentSession.supportsMessageSearchPaging) return
    if (isSearchPending || isLoadingMoreSearch || searchReachedEnd) return

    val query = searchQuery.text.toString().trim()
    if (query.isEmpty()) return

    val cursor = searchPageCursor(rawSearchResults) ?: return

    isLoadingMoreSearch = true

    viewModelScope.launch {
        currentSession.searchMessages(
            providerDialogId = providerDialogId,
            query = query,
            limit = SearchPageSize,
            fromMessageId = cursor
        ).onSuccess { page ->
            if (searchQuery.text.toString().trim() != query) return@onSuccess

            rawSearchResults = appendSearchPage(rawSearchResults, page)
            searchResults = filteredSearchResults(
                raw = rawSearchResults,
                mediaFilter = searchFilter,
                dateFilter = searchDateFilter,
                nowMillis = currentTimeMillis()
            )
            searchReachedEnd = page.isEmpty()
            recomputeDerived()
        }.onFailure { failure ->
            if (searchQuery.text.toString().trim() != query) return@onFailure

            error = failure.message ?: "Failed to load more results"
        }

        isLoadingMoreSearch = false
    }
}
