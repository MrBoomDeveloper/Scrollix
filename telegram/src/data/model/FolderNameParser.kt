package com.mrboomdev.scrollix.data.model

import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeCustomEmoji

internal fun parseFolderCustomEmojis(
    text: String,
    entities: List<TextEntity>
): List<DialogFolderEmoji> {
    val parsed = entities.mapNotNull { entity ->
        val type = entity.type as? TextEntityTypeCustomEmoji
            ?: return@mapNotNull null
        val end = entity.offset.toLong() + entity.length

        if (entity.offset < 0 || entity.length <= 0 || end > text.length) {
            return@mapNotNull null
        }

        DialogFolderEmoji(
            customEmojiId = type.customEmojiId,
            offset = entity.offset,
            length = entity.length,
            fallback = text.substring(entity.offset, end.toInt())
        )
    }.sortedBy { it.offset }

    val result = mutableListOf<DialogFolderEmoji>()
    var cursor = 0

    for (emoji in parsed) {
        if (emoji.offset < cursor) continue
        result += emoji
        cursor = emoji.offset + emoji.length
    }

    return result
}
