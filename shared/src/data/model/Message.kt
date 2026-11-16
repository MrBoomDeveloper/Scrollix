package com.mrboomdev.scrollix.data.model

enum class MessageMediaKind {
    Photo,
    File
}

data class MessageMedia(
    val kind: MessageMediaKind,
    val previewFileId: Int? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val minithumbnail: ByteArray? = null
)

data class ChatMessage(
    val providerId: String,
    val accountId: String,
    val providerDialogId: String,
    val messageId: Long,
    val senderName: String? = null,
    val senderId: Long,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val media: MessageMedia? = null
)
