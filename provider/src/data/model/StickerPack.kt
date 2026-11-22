package com.mrboomdev.scrollix.data.model

enum class StickerPackKind {
    Sticker,
    CustomEmoji
}

/**
 * A sticker pack installed on the account. Items are loaded lazily.
 * [thumbnailFileId] points to the pack's featured sticker preview, so the
 * picker can show it without loading the whole set.
 */
data class StickerPack(
    val id: Long,
    val title: String,
    val isInstalled: Boolean = true,
    val thumbnailFileId: Int? = null
)
