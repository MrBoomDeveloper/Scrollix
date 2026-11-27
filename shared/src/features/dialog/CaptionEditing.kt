package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageMediaKind

/**
 * Media kinds that carry an editable caption. Stickers, voice and video notes
 * have their own text baked in, and polls are edited elsewhere, so none of them
 * count as caption media.
 */
private val CaptionMediaKinds = setOf(
    MessageMediaKind.Photo,
    MessageMediaKind.Video,
    MessageMediaKind.File,
    MessageMediaKind.Audio,
    MessageMediaKind.Animation
)

/**
 * True when the message's text is a media caption. Telegram edits captions
 * through a different call than plain text, so the composer and the outbox have
 * to keep the two apart.
 */
internal val ChatMessage.editsCaption: Boolean
    get() {
        val media = allMedia
        return media.isNotEmpty() && media.all { item -> item.kind in CaptionMediaKinds }
    }

/**
 * Whether the message menu offers Edit. Only the account's own messages can be
 * changed, and a caption needs the provider's caption call; the text of a
 * plain message must at least have something to save.
 */
internal fun ChatMessage.canBeEdited(supportsCaptionEditing: Boolean): Boolean {
    if (!isOutgoing || isService) return false

    return if (editsCaption) supportsCaptionEditing else text.isNotBlank()
}
