package com.mrboomdev.scrollix.data.model

enum class MessageMediaKind {
    Photo,
    File,
    Sticker,
    Video,
    VideoNote,
    VoiceNote,
    Audio,
    Animation
}

data class CustomEmojiSpan(
    val start: Int,
    val end: Int,
    val emojiId: Long
)

enum class MessageStickerFormat {
    Webp,
    Tgs,
    Webm
}

enum class MessageSendState {
    Sending,
    Sent,
    Failed
}

data class MessageMedia(
    val kind: MessageMediaKind,
    val previewFileId: Int? = null,
    val fileId: Int? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val minithumbnail: ByteArray? = null,
    val durationSeconds: Int = 0,
    val waveform: ByteArray? = null,
    val stickerFileId: Int? = null,
    val stickerFormat: MessageStickerFormat = MessageStickerFormat.Webp
)

data class MessageReactionInfo(
    val emoji: String,
    val count: Int,
    val isChosen: Boolean = false,
    val reactorIds: List<Long> = emptyList(),
    val reactorAvatars: List<Avatar> = emptyList()
)

data class MessageReply(
    val messageId: Long,
    val senderName: String? = null,
    val text: String = "",
    val media: MessageMedia? = null
)

data class ChatMessage(
    val providerId: String,
    val accountId: String,
    val providerDialogId: String,
    val messageId: Long,
    val senderName: String? = null,
    val senderAvatar: Avatar? = null,
    val senderId: Long,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val media: MessageMedia? = null,
    val customEmojis: List<CustomEmojiSpan> = emptyList(),
    val replyTo: MessageReply? = null,
    val reactions: List<MessageReactionInfo> = emptyList(),
    val viewCount: Int = 0,
    val shareCount: Int = 0,
    val sendState: MessageSendState = MessageSendState.Sent,
    val isRead: Boolean = false
)
