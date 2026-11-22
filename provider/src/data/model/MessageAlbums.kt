package com.mrboomdev.scrollix.data.model

/**
 * TDLib delivers every item of a media album as its own message. The chat UI
 * shows the whole album as one row, so messages sharing a
 * [ChatMessage.mediaAlbumId] are merged here.
 *
 * Grouping is global within the supplied list, not limited to consecutive
 * runs. A page boundary can otherwise split an album into two rows, and the
 * partial rows would change identity when the missing member finally loads.
 *
 * The newest item provides the timestamp and send state, while the first
 * item stays the row identity. A caption may live on any item; the first
 * non-blank one is used. [ChatMessage.rowId] is derived from the album id, so
 * it stays stable even when the first loaded item changes.
 */
fun List<ChatMessage>.groupMessageAlbums(): List<ChatMessage> {
    if (none { message -> message.mediaAlbumId != null }) return this

    val order = ArrayList<Any>(size)
    val albums = LinkedHashMap<Long, MutableList<ChatMessage>>()

    for (message in this) {
        val albumId = message.mediaAlbumId

        if (albumId == null) {
            order += message
            continue
        }

        val members = albums.getOrPut(albumId) {
            order += AlbumReference(albumId)
            mutableListOf()
        }

        members += message
    }

    return order.map { entry ->
        when (entry) {
            is AlbumReference -> {
                val members = albums[entry.albumId].orEmpty()
                when {
                    members.isEmpty() -> error("Album reference without members")
                    members.size > 1 -> mergeAlbum(members)
                    else -> members.first()
                }
            }

            is ChatMessage -> entry
            else -> error("Unexpected album entry")
        }
    }
}

private data class AlbumReference(val albumId: Long)

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
