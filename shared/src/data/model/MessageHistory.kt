package com.mrboomdev.scrollix.data.model

/**
 * Immutable snapshot of the messages currently loaded for a dialog together
 * with the state of its pagination.
 *
 * Messages are always sorted oldest first, so the last item is the newest
 * loaded message and the first item is the oldest loaded one.
 */
data class MessageHistory(
    val messages: List<ChatMessage> = emptyList(),
    val hasMoreOlder: Boolean = true,
    val isLoadingInitial: Boolean = true,
    val isLoadingOlder: Boolean = false
) {
    val newestMessageId: Long?
        get() = messages.lastOrNull()?.messageId

    val oldestMessageId: Long?
        get() = messages.firstOrNull()?.messageId

    fun indexOf(messageId: Long): Int {
        return messages.indexOfFirst { message -> message.messageId == messageId }
    }

    fun contains(messageId: Long): Boolean = indexOf(messageId) >= 0
}
