package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind

/**
 * File name used when a media item is saved to the device. A provider name is
 * kept, a name without an extension gets one from the kind, and nameless media
 * fall back to a kind-based default instead of an extensionless "file".
 */
internal fun savedMediaFileName(media: MessageMedia): String {
    val base = media.fileName?.takeIf { it.isNotBlank() } ?: when (media.kind) {
        MessageMediaKind.Photo -> "photo"
        MessageMediaKind.Video, MessageMediaKind.VideoNote -> "video"
        MessageMediaKind.Animation -> "animation"
        MessageMediaKind.Audio -> "audio"
        MessageMediaKind.Sticker -> "sticker"
        MessageMediaKind.VoiceNote -> "voice"
        MessageMediaKind.File -> "file"
    }

    if (base.contains('.')) return base

    return when (media.kind) {
        MessageMediaKind.Photo -> "$base.jpg"
        MessageMediaKind.Video, MessageMediaKind.VideoNote -> "$base.mp4"
        MessageMediaKind.Animation -> "$base.gif"
        MessageMediaKind.Audio -> "$base.mp3"
        MessageMediaKind.Sticker -> "$base.webp"
        MessageMediaKind.VoiceNote -> "$base.ogg"
        MessageMediaKind.File -> base
    }
}
