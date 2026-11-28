package com.mrboomdev.scrollix.features.dialog.search

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.features.dialog.viewmodel.DialogScreenViewModel

/** Narrowing applied to the in-chat search results. */
internal enum class DialogSearchFilter(val label: String) {
    All("All"),
    FromMe("From me"),
    Photos("Photos"),
    Videos("Videos"),
    Files("Files"),
    Links("Links")
}

/**
 * Applies [filter] to the raw matches. The server search stays one query per
 * draft: filters only narrow the page it already returned, so changing a filter
 * never re-queries the provider.
 */
internal fun filterSearchMessages(
    messages: List<ChatMessage>,
    filter: DialogSearchFilter
): List<ChatMessage> {
    return when (filter) {
        DialogSearchFilter.All -> messages
        DialogSearchFilter.FromMe -> messages.filter { message -> message.isOutgoing }
        DialogSearchFilter.Photos -> messages.filter { message ->
            message.media?.kind == MessageMediaKind.Photo
        }
        DialogSearchFilter.Videos -> messages.filter { message ->
            message.media?.kind == MessageMediaKind.Video ||
                message.media?.kind == MessageMediaKind.VideoNote ||
                message.media?.kind == MessageMediaKind.Animation
        }
        DialogSearchFilter.Files -> messages.filter { message ->
            message.media?.kind == MessageMediaKind.File
        }
        DialogSearchFilter.Links -> messages.filter { message ->
            message.linkPreview != null || containsLink(message.text)
        }
    }
}

private fun containsLink(text: String): Boolean {
    val lower = text.lowercase()

    return "http://" in lower || "https://" in lower || "www." in lower
}

/** Narrows the shown results without asking the provider again. */
internal fun DialogScreenViewModel.setSearchFilter(filter: DialogSearchFilter) {
    if (searchFilter == filter) return

    searchFilter = filter
    applySearchFilters()
}
