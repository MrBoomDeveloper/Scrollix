package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DialogFolderEmoji(
    val customEmojiId: Long,
    val offset: Int,
    val length: Int,
    val fallback: String,
    val needsRepainting: Boolean = false,
    val format: DialogFolderEmojiFormat = DialogFolderEmojiFormat.Unknown,
    val thumbnail: ByteArray? = null,
    val data: ByteArray? = null
)

@Serializable
enum class DialogFolderEmojiFormat {
    Tgs,
    Webp,
    Webm,
    Unknown
}
