package com.mrboomdev.scrollix.data.model

/**
 * TDLib delivers every item of a media album as its own message. The chat UI
 * shows the whole album as one row, so consecutive messages sharing a
 * [ChatMessage.mediaAlbumId] are merged here.
 *
 * The newest item provides the timestamp and send state, while the first
 * item stays the row identity. A caption may live on any item; the first
 * non-blank one is used.
 */
fun List<ChatMessage>.groupMessageAlbums(): List<ChatMessage> {
    if (none { message -> message.mediaAlbumId != null }) return this

    val result = ArrayList<ChatMessage>(size)
    var album = mutableListOf<ChatMessage>()

    fun flush() {
        if (album.isNotEmpty()) {
            result += if (album.size > 1) mergeAlbum(album) else album.first()
            album = mutableListOf()
        }
    }

    for (message in this) {
        val albumId = message.mediaAlbumId

        if (albumId == null) {
            flush()
            result += message
            continue
        }

        if (album.isNotEmpty() && album.first().mediaAlbumId != albumId) {
            flush()
        }

        album += message
    }

    flush()
    return result
}

private fun mergeAlbum(messages: List<ChatMessage>): ChatMessage {
    val base = messages.first()
    val caption = messages.firstOrNull { message -> message.text.isNotBlank() } ?: base
    val latest = messages.last()
    val media = messages.flatMap { message -> message.allMedia }

    return base.copy(
        messageId = base.messageId,
        messageIds = messages.flatMap { message -> message.allMessageIds }.distinct(),
        media = media.firstOrNull(),
        mediaItems = media,
        text = caption.text,
        customEmojis = caption.customEmojis,
        richBlocks = caption.richBlocks,
        replyTo = messages.firstNotNullOfOrNull { message -> message.replyTo },
        reactions = messages
            .firstOrNull { message -> message.reactions.isNotEmpty() }
            ?.reactions
            .orEmpty(),
        timestamp = latest.timestamp,
        viewCount = messages.maxOf { message -> message.viewCount },
        shareCount = messages.maxOf { message -> message.shareCount },
        sendState = when {
            messages.any { message -> message.sendState == MessageSendState.Failed } ->
                MessageSendState.Failed

            messages.any { message -> message.sendState == MessageSendState.Sending } ->
                MessageSendState.Sending

            else -> MessageSendState.Sent
        },
        isRead = messages.all { message -> message.isRead }
    )
}
