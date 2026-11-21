package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
data class CustomEmojiSpan(
    val start: Int,
    val end: Int,
    val emojiId: Long
)

@Serializable
enum class MessageStickerFormat {
    Webp,
    Tgs,
    Webm
}

@Serializable
enum class MessageSendState {
    Sending,
    Sent,
    Failed
}

@Serializable
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
    val stickerFormat: MessageStickerFormat = MessageStickerFormat.Webp,
    val stickerSetId: Long? = null,
    /** The TDLib sticker id, which is also the custom emoji entity id. */
    val stickerId: Long? = null,
    /** The emoji a custom emoji sticker stands for. */
    val stickerEmoji: String? = null
)

data class MessageReactionOption(
    val emoji: String,
    val customEmojiId: Long? = null
) {
    val key: String
        get() = customEmojiId?.let { "custom:$it" } ?: "emoji:$emoji"
}

@Serializable
data class MessageReactionInfo(
    val emoji: String,
    val count: Int,
    val isChosen: Boolean = false,
    val reactorIds: List<Long> = emptyList(),
    val reactorAvatars: List<Avatar> = emptyList(),
    val customEmojiId: Long? = null
)

@Serializable
data class MessageReply(
    val messageId: Long,
    val senderName: String? = null,
    val text: String = "",
    val media: MessageMedia? = null
)

@Serializable
data class MessagePollOptionInfo(
    val id: String,
    val text: String,
    val voterCount: Int = 0,
    val votePercentage: Int = 0,
    val isChosen: Boolean = false,
    val isBeingChosen: Boolean = false,
    val media: MessageMedia? = null,
    val customEmojis: List<CustomEmojiSpan> = emptyList()
)

@Serializable
data class MessagePollInfo(
    val id: Long,
    val question: String,
    val options: List<MessagePollOptionInfo> = emptyList(),
    val totalVoterCount: Int = 0,
    val isClosed: Boolean = false,
    val isAnonymous: Boolean = true,
    val allowsMultipleAnswers: Boolean = false,
    val canSeeResults: Boolean = true,
    val isQuiz: Boolean = false,
    val correctOptionIds: List<Int> = emptyList(),
    val explanation: String? = null
)

@Serializable
enum class MessageRichTextStyle {
    Title,
    Header,
    Subheader,
    Paragraph,
    Preformatted,
    Quote,
    Footer
}

/** Styling of a piece of rich text, with offsets relative to the block text. */
@Serializable
data class RichTextSpan(
    val start: Int,
    val end: Int,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderlined: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isMonospace: Boolean = false,
    val isSpoiler: Boolean = false,
    val url: String? = null
)

@Serializable
sealed interface MessageRichBlock {
    @Serializable
    data class Text(
        val text: String,
        val style: MessageRichTextStyle = MessageRichTextStyle.Paragraph,
        val spans: List<RichTextSpan> = emptyList(),
        val bullet: String? = null
    ) : MessageRichBlock

    @Serializable
    data class Media(
        val media: MessageMedia,
        val caption: String = ""
    ) : MessageRichBlock

    @Serializable
    object Divider : MessageRichBlock
}

data class StickerSetInfo(
    val id: Long,
    val title: String,
    val name: String = "",
    val isInstalled: Boolean = false,
    val stickers: List<MessageMedia> = emptyList()
)

@Serializable
data class ChatMessage(
    val providerId: String,
    val accountId: String,
    val providerDialogId: String,
    val messageId: Long,
    val senderName: String? = null,
    val senderAvatar: Avatar? = null,
    val senderEmojiStatusId: Long? = null,
    val senderId: Long,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val media: MessageMedia? = null,
    val poll: MessagePollInfo? = null,
    val customEmojis: List<CustomEmojiSpan> = emptyList(),
    val richBlocks: List<MessageRichBlock> = emptyList(),
    val replyTo: MessageReply? = null,
    val reactions: List<MessageReactionInfo> = emptyList(),
    val viewCount: Int = 0,
    val shareCount: Int = 0,
    val sendState: MessageSendState = MessageSendState.Sent,
    val isRead: Boolean = false
)
