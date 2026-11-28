package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.model.StickerSetInfo

/**
 * Sticker pack reading capability of an account session.
 *
 * The emoji picker and the pack dialog need packs, their previews and media
 * bytes, not the rest of [AccountSession]. Sending stickers stays on the
 * session because it belongs to message delivery, not to browsing packs.
 */
interface StickerSession : MediaSession {
    /** Loads a sticker or custom-emoji set by its public name. */
    suspend fun loadStickerSetByName(
        name: String,
        kind: StickerPackKind = StickerPackKind.Sticker
    ): Result<StickerSetInfo> = Result.failure(
        UnsupportedOperationException("Loading sticker sets by name is not supported")
    )

    suspend fun loadStickerSet(setId: Long): Result<StickerSetInfo>
    suspend fun setStickerSetInstalled(setId: Long, installed: Boolean): Result<Unit>
    suspend fun loadStickerPacks(kind: StickerPackKind): Result<List<StickerPack>>
    suspend fun loadRecentStickers(kind: StickerPackKind): Result<List<MessageMedia>>
    suspend fun removeRecentSticker(sticker: MessageMedia): Result<Unit>
    suspend fun clearRecentStickers(): Result<Unit>

    /** Saved GIFs shown by the picker next to recent stickers. */
    suspend fun loadSavedAnimations(): Result<List<MessageMedia>>
    suspend fun removeSavedAnimation(animation: MessageMedia): Result<Unit>
    suspend fun clearSavedAnimations(): Result<Unit>
}
