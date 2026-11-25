package com.mrboomdev.scrollix.data.provider
import com.mrboomdev.scrollix.data.model.StickerPackKind


/**
 * What a Telegram deep link points at. Sticker link targets carry the set
 * name because the numeric id is only known after the provider searches for
 * it; the dialog target carries the message id when the link contains one, so
 * shared code does not have to re-parse the URL.
 */
sealed interface TelegramLinkTarget {
    data class Dialog(
        val providerDialogId: String,
        val messageId: Long? = null
    ) : TelegramLinkTarget

    data class StickerSet(
        val name: String,
        val kind: StickerPackKind,
        val expectCustomEmoji: Boolean = kind == StickerPackKind.CustomEmoji
    ) : TelegramLinkTarget
}
