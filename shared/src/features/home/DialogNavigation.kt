package com.mrboomdev.scrollix.features.home

import com.mrboomdev.scrollix.data.model.AccountDialogEntry


/**
 * Returns the chat [step] places away from the selected one in the aggregated
 * dialog list (positive is down, negative is up). The walk stops at the ends
 * instead of wrapping, and returns null when the selection would not change,
 * so holding a shortcut cannot cycle through the list unnoticed. With nothing
 * selected, the first press lands on the first (next) or last (previous) chat.
 */
/**
 * The highest chat in the list that still has unread messages. Explicitly
 * marked chats count too, because the mark is a deliberate reminder. Saved
 * Messages never notify, so they are skipped even if a provider reports a
 * counter for them.
 */
internal fun firstUnreadEntry(entries: List<AccountDialogEntry>): AccountDialogEntry? {
    return entries.firstOrNull { entry ->
        !entry.dialog.isSavedMessages &&
            (entry.dialog.unreadCount > 0 || entry.dialog.isMarkedAsUnread)
    }
}

internal fun adjacentDialogEntry(
    entries: List<AccountDialogEntry>,
    currentAccountId: String?,
    currentProviderDialogId: String?,
    step: Int
): AccountDialogEntry? {
    if (entries.isEmpty() || step == 0) return null

    val currentIndex = entries.indexOfFirst { entry ->
        entry.account.id == currentAccountId &&
            entry.dialog.providerDialogId == currentProviderDialogId
    }

    val targetIndex = if (currentIndex == -1) {
        if (step > 0) 0 else entries.lastIndex
    } else {
        (currentIndex + step).coerceIn(0, entries.lastIndex)
    }

    if (targetIndex == currentIndex) return null

    return entries.getOrNull(targetIndex)
}
