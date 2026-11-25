package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.MessageMedia
import dev.g000sha256.tdl.dto.InputFileLocal
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.model.StickerSetInfo
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal suspend fun TelegramSession.loadStickerSetImpl(setId: Long): Result<StickerSetInfo> {
    return try {
        when (val response = client.getStickerSet(setId)) {
            is TdlResult.Success -> Result.success(mapStickerSet(response.result))
            is TdlResult.Failure -> Result.failure(
                IllegalStateException("Failed to load the sticker pack")
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadStickerSetByNameImpl(name: String, kind: StickerPackKind): Result<StickerSetInfo> {
    val clean = name.trim()
    if (clean.isBlank()) {
        return Result.failure(IllegalArgumentException("Sticker set name is empty"))
    }

    return try {
        val found = when (val first = client.searchStickerSet(clean, false)) {
            is TdlResult.Success -> first.result
            is TdlResult.Failure -> when (val retried = client.searchStickerSet(clean, true)) {
                is TdlResult.Success -> retried.result
                is TdlResult.Failure -> return Result.failure(
                    IllegalStateException("Sticker pack @$clean was not found")
                )
            }
        }

        // Some TDLib versions return the set metadata without stickers
        // from search; load the full set in that case.
        val full = if (found.stickers.isEmpty() && found.id != 0L) {
            when (val loaded = client.getStickerSet(found.id)) {
                is TdlResult.Success -> loaded.result
                is TdlResult.Failure -> found
            }
        } else {
            found
        }

        Result.success(mapStickerSet(full, kind))
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.setStickerSetInstalledImpl(setId: Long, installed: Boolean): Result<Unit> {
    return try {
        when (client.changeStickerSet(setId, installed, false)) {
            is TdlResult.Success -> Result.success(Unit)

            is TdlResult.Failure -> Result.failure(
                IllegalStateException("Failed to update the sticker pack")
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadStickerPacksImpl(kind: StickerPackKind): Result<List<StickerPack>> {
    return try {
        val type = when (kind) {
            StickerPackKind.Sticker -> StickerTypeRegular()
            StickerPackKind.CustomEmoji -> StickerTypeCustomEmoji()
        }

        when (val response = client.getInstalledStickerSets(stickerType = type)) {
            is TdlResult.Success -> Result.success(
                response.result.sets.map { set ->
                    StickerPack(
                        id = set.id,
                        title = set.title,
                        isInstalled = true,
                        thumbnailFileId = set.thumbnail?.file?.id
                    )
                }
            )

            is TdlResult.Failure -> Result.failure(
                IllegalStateException(response.message)
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadRecentStickersImpl(kind: StickerPackKind): Result<List<MessageMedia>> {
    return try {
        when (val response = client.getRecentStickers(isAttached = false)) {
            is TdlResult.Success -> Result.success(
                response.result.stickers.map(::mapSticker)
            )

            is TdlResult.Failure -> Result.failure(
                IllegalStateException(response.message)
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.removeRecentStickerImpl(sticker: MessageMedia): Result<Unit> {
    val fileId = sticker.stickerFileId ?: sticker.fileId
        ?: return Result.failure(IllegalArgumentException("Sticker has no file"))

    return try {
        when (val response = client.removeRecentSticker(
            isAttached = false,
            sticker = InputFileId(fileId)
        )) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(IllegalStateException(response.message))
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.clearRecentStickersImpl(): Result<Unit> {
    return try {
        when (val response = client.clearRecentStickers(isAttached = false)) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(IllegalStateException(response.message))
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadSavedAnimationsImpl(): Result<List<MessageMedia>> {
    return try {
        when (val response = client.getSavedAnimations()) {
            is TdlResult.Success -> Result.success(
                response.result.animations.map(::mapAnimation)
            )

            is TdlResult.Failure -> Result.failure(
                IllegalStateException(response.message)
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.removeSavedAnimationImpl(animation: MessageMedia): Result<Unit> {
    val fileId = animation.fileId
        ?: return Result.failure(IllegalArgumentException("Animation has no file"))

    return try {
        when (val response = client.removeSavedAnimation(
            animation = InputFileId(fileId)
        )) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(IllegalStateException(response.message))
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.clearSavedAnimationsImpl(): Result<Unit> {
    return try {
        val animations = when (val response = client.getSavedAnimations()) {
            is TdlResult.Success -> response.result.animations.map(::mapAnimation)
            is TdlResult.Failure -> return Result.failure(
                IllegalStateException(response.message)
            )
        }

        for (saved in animations) {
            val fileId = saved.fileId ?: continue

            when (val response = client.removeSavedAnimation(
                animation = InputFileId(fileId)
            )) {
                is TdlResult.Success -> Unit
                is TdlResult.Failure -> return Result.failure(
                    IllegalStateException(response.message)
                )
            }
        }

        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.sendStickerImpl(providerDialogId: String, sticker: MessageMedia): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))
    val fileId = sticker.stickerFileId ?: sticker.fileId
        ?: return Result.failure(IllegalArgumentException("Sticker has no file"))

    return sendContent(
        chatId = chatId,
        content = InputMessageSticker(
            sticker = InputSticker(
                sticker = InputFileId(fileId),
                thumbnail = null,
                width = sticker.width,
                height = sticker.height
            ),
            emoji = ""
        )
    )
}

internal suspend fun TelegramSession.sendStickerBytesImpl(providerDialogId: String, bytes: ByteArray, format: MessageStickerFormat, width: Int, height: Int): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (bytes.isEmpty()) {
        return Result.failure(IllegalArgumentException("Sticker is empty"))
    }

    val extension = when (format) {
        MessageStickerFormat.Tgs -> "tgs"
        MessageStickerFormat.Webm -> "webm"
        MessageStickerFormat.Webp -> "webp"
    }

    val path = "stickers/imported/${bytes.contentHashCode()}-${bytes.size}.$extension"
    runCatching { storage.writeBytes(path, bytes) }
        .getOrElse { return Result.failure(it) }

    val absolutePath = storage.absolutePath(path)

    val sticker = sendContent(
        chatId = chatId,
        content = InputMessageSticker(
            sticker = InputSticker(
                sticker = InputFileLocal(absolutePath),
                thumbnail = null,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1)
            ),
            emoji = ""
        )
    )

    if (sticker.isSuccess) return sticker

    // Not every foreign sticker is a valid Telegram sticker (dimensions,
    // transparency, format). Sending it as a document keeps it usable.
    return sendContent(
        chatId = chatId,
        content = InputMessageDocument(
            document = InputDocument(
                document = InputFileLocal(absolutePath),
                thumbnail = null,
                disableContentTypeDetection = false
            ),
            caption = null
        )
    )
}

internal suspend fun TelegramSession.sendAnimationImpl(providerDialogId: String, animation: MessageMedia): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))
    val fileId = animation.fileId
        ?: return Result.failure(IllegalArgumentException("Animation has no file"))

    return sendContent(
        chatId = chatId,
        content = InputMessageAnimation(
            animation = InputAnimation(
                animation = InputFileId(fileId),
                thumbnail = null,
                duration = animation.durationSeconds,
                width = animation.width,
                height = animation.height,
                addedStickerFileIds = intArrayOf()
            ),
            caption = null,
            showCaptionAboveMedia = false,
            hasSpoiler = false
        )
    )
}

internal suspend fun TelegramSession.sendAnimationBytesImpl(providerDialogId: String, bytes: ByteArray, mimeType: String?, width: Int, height: Int, durationSeconds: Int): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (bytes.isEmpty()) {
        return Result.failure(IllegalArgumentException("Animation is empty"))
    }

    val extension = when {
        mimeType?.contains("gif", ignoreCase = true) == true -> "gif"
        mimeType?.contains("webp", ignoreCase = true) == true -> "webp"
        else -> "mp4"
    }

    val path = "animations/imported/${bytes.contentHashCode()}-${bytes.size}.$extension"
    runCatching { storage.writeBytes(path, bytes) }
        .getOrElse { return Result.failure(it) }

    val absolutePath = storage.absolutePath(path)

    val animation = sendContent(
        chatId = chatId,
        content = InputMessageAnimation(
            animation = InputAnimation(
                animation = InputFileLocal(absolutePath),
                thumbnail = null,
                duration = durationSeconds,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
                addedStickerFileIds = intArrayOf()
            ),
            caption = null,
            showCaptionAboveMedia = false,
            hasSpoiler = false
        )
    )

    if (animation.isSuccess) return animation

    return sendContent(
        chatId = chatId,
        content = InputMessageDocument(
            document = InputDocument(
                document = InputFileLocal(absolutePath),
                thumbnail = null,
                disableContentTypeDetection = false
            ),
            caption = null
        )
    )
}
