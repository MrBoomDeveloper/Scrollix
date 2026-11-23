package com.mrboomdev.scrollix.data.model

/** Synthetic message id used by the UI for the undiscovered range row. */
const val UndiscoveredMessagesMessageId: Long = Long.MIN_VALUE + 1

/**
 * Immutable snapshot of the messages currently loaded for a dialog together
 * with the state of its pagination.
 *
 * Messages are always sorted oldest first, so the last item is the newest
 * loaded message and the first item is the oldest loaded one. The list is one
 * contiguous block: it never contains an invisible hole.
 */
data class MessageHistory(
    val messages: List<ChatMessage> = emptyList(),
    val hasMoreOlder: Boolean = true,
    val isLoadingInitial: Boolean = true,
    val isLoadingOlder: Boolean = false,
    /** True when the window is an older block loaded around a jump. */
    val hasMoreNewer: Boolean = false,
    /** `Messages.totalCount` when the provider exposes it. */
    val totalCount: Int? = null,
    /**
     * Far end of the chat, kept as a preview after a jump to the first
     * message. When [hasUnloadedGap] is true the range between [messages] and
     * this block has not been fetched yet.
     */
    val newerMessages: List<ChatMessage> = emptyList(),
    /** True while the middle between the two blocks is undiscovered. */
    val hasUnloadedGap: Boolean = false
) {
    /** True when the UI should render the undiscovered-range placeholder. */
    val showsGap: Boolean
        get() = hasUnloadedGap && newerMessages.isNotEmpty()
    val newestMessageId: Long?
        get() = messages.lastOrNull()?.messageId

    val oldestMessageId: Long?
        get() = messages.firstOrNull()?.messageId

    fun indexOf(messageId: Long): Int {
        return messages.indexOfFirst { message -> message.messageId == messageId }
    }

    fun contains(messageId: Long): Boolean = indexOf(messageId) >= 0
}
