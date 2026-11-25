package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage

/**
 * Text placed on the clipboard when several messages are selected. Messages
 * are copied oldest first regardless of the display order, blank and service
 * messages are skipped, an album counts once, and a blank line separates
 * messages so pasted text stays readable.
 */
internal fun selectedMessageText(messages: List<ChatMessage>): String {
    return messages
        .asSequence()
        .filter { message -> !message.isService && message.text.isNotBlank() }
        .distinctBy { message -> message.messageId }
        .sortedBy { message -> message.timestamp }
        .joinToString("\n\n") { message -> message.text }
}
