package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import kotlinx.coroutines.CancellationException
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageTextFormat

internal suspend fun TelegramSession.editMessageImpl(providerDialogId: String, messageId: Long, text: String, customEmojis: List<CustomEmojiSpan>, formatting: List<MessageTextFormat>): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return Result.failure(IllegalArgumentException("Message is empty"))
    }

    AppLog.protocol(
        "telegram",
        "out editMessage chat=$chatId message=$messageId",
        "text=$trimmed"
    )

    return try {
        when (val result = client.editMessageText(
            chatId = chatId,
            messageId = messageId,
            inputMessageContent = InputMessageText(
                text = FormattedText(
                    text = trimmed,
                    entities = customEmojiEntities(text, customEmojis) +
                        formattingEntities(text, formatting)
                ),
                linkPreviewOptions = null,
                clearDraft = false
            )
        )) {
            is TdlResult.Success -> {
                upsertMessage(result.result)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

/**
 * Replaces the caption of a media message. Telegram edits captions through a
 * separate call, so a photo or file message with only a caption uses this path
 * instead of [editMessageImpl]. An empty [caption] removes the caption, which
 * plain text editing never allows.
 */
internal suspend fun TelegramSession.editMessageCaptionImpl(
    providerDialogId: String,
    messageId: Long,
    caption: String,
    customEmojis: List<CustomEmojiSpan>,
    formatting: List<MessageTextFormat>
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val trimmed = caption.trim()

    AppLog.protocol(
        "telegram",
        "out editMessageCaption chat=$chatId message=$messageId",
        "caption=$trimmed"
    )

    return try {
        when (val result = client.editMessageCaption(
            chatId = chatId,
            messageId = messageId,
            // Keeping the current position matters: passing false would move a
            // deliberately above-text caption below the media.
            showCaptionAboveMedia = currentCaptionAboveMedia(chatId, messageId),
            caption = FormattedText(
                text = trimmed,
                entities = customEmojiEntities(caption, customEmojis) +
                    formattingEntities(caption, formatting)
            )
        )) {
            is TdlResult.Success -> {
                upsertMessage(result.result)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

/**
 * The caption position of the message being edited. TDLib's edit call has no
 * "keep as is" value, so the current one is read back; an unreadable message
 * falls back to the default position below the media.
 */
private suspend fun TelegramSession.currentCaptionAboveMedia(chatId: Long, messageId: Long): Boolean {
    return try {
        when (val result = client.getMessage(chatId, messageId)) {
            is TdlResult.Success -> result.result.content.captionAboveMedia()
            is TdlResult.Failure -> false
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        false
    }
}

private fun MessageContent?.captionAboveMedia(): Boolean = when (this) {
    is MessagePhoto -> showCaptionAboveMedia
    is MessageVideo -> showCaptionAboveMedia
    is MessageAnimation -> showCaptionAboveMedia
    // Audio and document captions always sit below the file, so the flag does
    // not exist for them.
    else -> false
}
