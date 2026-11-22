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

/** Loaded custom emoji bytes together with their recolor metadata. */
data class CustomEmojiAsset(
    val data: ByteArray,
    val needsRepainting: Boolean = false
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
    /**
     * Durable content id of the preview. Runtime file ids are only valid for
     * one run, so cached messages must load media by these ids instead of
     * trusting a file id that may now point at a completely different file.
     */
    val previewUniqueId: String? = null,
    val fileId: Int? = null,
    val fileUniqueId: String? = null,
    val stickerUniqueId: String? = null,
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
    val stickerEmoji: String? = null,
    /** Whether the sticker should be drawn in the theme's primary color. */
    val needsRepainting: Boolean = false
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
data class MessageDraft(
    val text: String = "",
    val customEmojis: List<CustomEmojiSpan> = emptyList()
)

@Serializable
data class MessageLinkPreview(
    val url: String,
    val displayUrl: String = "",
    val siteName: String = "",
    val title: String = "",
    val description: String = "",
    val showAboveText: Boolean = false
)

/** A point shared in a chat, optionally as an ongoing live location. */
@Serializable
data class MessageLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val isLive: Boolean = false,
    val expiresInSeconds: Int = 0
)

/** Kinds of media that a chat can expose through the shared media tabs. */
enum class SharedMediaKind {
    Photo,
    Video,
    Animation,
    Voice,
    Link,
    File
}

@Serializable
data class MessageReply(
    val messageId: Long,
    val senderName: String? = null,
    val text: String = "",
    val media: MessageMedia? = null
)

/** A local attachment waiting in the composer before it is sent. */
data class OutgoingAttachment(
    val path: String,
    /** True for image picker selections, false for document picker selections. */
    val asPhoto: Boolean
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
    /** Title/name of the chat or user this message was forwarded from. */
    val forwardedFromName: String? = null,
    val forwardedFromAvatar: Avatar? = null,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    /**
     * True for Telegram service messages (wallpaper changed, gift, pin…).
     * Their text is informational, so it cannot be copied or edited.
     */
    val isService: Boolean = false,
    val media: MessageMedia? = null,
    /** Non-zero when this message is one item of a media album. */
    val mediaAlbumId: Long? = null,
    /** All attachments of a grouped album; empty for a single-media message. */
    val mediaItems: List<MessageMedia> = emptyList(),
    /** TDLib message ids merged into this row; empty unless this is an album. */
    val messageIds: List<Long> = emptyList(),
    val poll: MessagePollInfo? = null,
    val customEmojis: List<CustomEmojiSpan> = emptyList(),
    val richBlocks: List<MessageRichBlock> = emptyList(),
    val linkPreview: MessageLinkPreview? = null,
    val location: MessageLocationInfo? = null,
    val replyTo: MessageReply? = null,
    val reactions: List<MessageReactionInfo> = emptyList(),
    val viewCount: Int = 0,
    val shareCount: Int = 0,
    val sendState: MessageSendState = MessageSendState.Sent,
    val isRead: Boolean = false
) {
    /** Every attachment in display order, including a single-media message. */
    val allMedia: List<MessageMedia>
        get() = mediaItems.ifEmpty { listOfNotNull(media) }

    /** Every TDLib message id represented by this row, albums included. */
    val allMessageIds: List<Long>
        get() = messageIds.ifEmpty { listOf(messageId) }
}
