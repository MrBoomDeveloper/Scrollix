package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

/**
 * When a scheduled message should be sent. A message with a scheduling state
 * is not part of the chat history yet; it waits in the chat's schedule queue.
 */
@Serializable
sealed interface MessageScheduling {
    /** Send at [sendDateMillis], which is an absolute UTC timestamp. */
    @Serializable
    data class At(val sendDateMillis: Long) : MessageScheduling

    /** Send as soon as the account reconnects; Telegram keeps this server-side. */
    @Serializable
    object WhenOnline : MessageScheduling
}
