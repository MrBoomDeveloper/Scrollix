package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.dto.*

internal data class RichTextStyleState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val monospace: Boolean = false,
    val spoiler: Boolean = false,
    val url: String? = null
)

internal data class ChatEntry(
    val chatId: Long,
    val type: ChatType,
    val title: String,
    val photo: ChatPhotoInfo?,
    val background: ChatBackground? = null,
    val emojiStatus: EmojiStatus? = null,
    val lastMessage: Message?,
    val positions: List<ChatPosition>,
    val permissions: ChatPermissions,
    val unreadCount: Int,
    val unreadMentionCount: Int = 0,
    val unreadReactionCount: Int = 0,
    val lastReadOutboxMessageId: Long,
    val isMarkedAsUnread: Boolean,
    val typingText: String? = null,
    /** Text draft stored on the server, shown by the draft center. */
    val draftText: String = "",
    /** The chat is shown as a list of forum topics (`viewAsTopics`). */
    val viewAsTopics: Boolean = false
) {
    companion object {
        fun from(chat: Chat): ChatEntry {
            return ChatEntry(
                chatId = chat.id,
                type = chat.type,
                title = chat.title,
                photo = chat.photo,
                background = chat.background,
                emojiStatus = chat.emojiStatus,
                lastMessage = chat.lastMessage,
                positions = chat.positions.filter { position ->
                    position.order != 0L
                },
                permissions = chat.permissions,
                unreadCount = chat.unreadCount,
                unreadMentionCount = chat.unreadMentionCount,
                unreadReactionCount = chat.unreadReactionCount,
                lastReadOutboxMessageId = chat.lastReadOutboxMessageId,
                isMarkedAsUnread = chat.isMarkedAsUnread,
                draftText = (chat.draftMessage?.content as? DraftMessageContentText)
                    ?.text
                    ?.text
                    .orEmpty(),
                viewAsTopics = chat.viewAsTopics
            )
        }
    }
}
