package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageVideoQuality
import com.mrboomdev.scrollix.data.model.MessagePollInfo
import com.mrboomdev.scrollix.data.model.MessagePollOptionInfo
import com.mrboomdev.scrollix.data.model.DownloadedFile
import dev.g000sha256.tdl.dto.FormattedText
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal fun TelegramSession.mapPoll(content: MessageContent): MessagePollInfo? {
    val poll = (content as? MessagePoll)?.poll ?: return null
    val quiz = poll.type as? PollTypeQuiz

    return MessagePollInfo(
        id = poll.id,
        question = poll.question.text,
        customEmojis = customEmojiSpans(poll.question),
        options = poll.options.map { option ->
            MessagePollOptionInfo(
                id = option.id,
                text = option.text.text,
                voterCount = option.voterCount,
                votePercentage = option.votePercentage,
                isChosen = option.isChosen,
                isBeingChosen = option.isBeingChosen,
                media = mapPollMedia(option.media),
                customEmojis = customEmojiSpans(option.text)
            )
        },
        totalVoterCount = poll.totalVoterCount,
        isClosed = poll.isClosed,
        isAnonymous = poll.isAnonymous,
        allowsMultipleAnswers = poll.allowsMultipleAnswers,
        allowsRevoting = poll.allowsRevoting,
        canSeeResults = poll.canSeeResults,
        isQuiz = quiz != null,
        correctOptionIds = quiz?.correctOptionIds?.toList().orEmpty(),
        explanation = quiz?.explanation?.text?.takeIf { text -> text.isNotBlank() }
    )
}

internal fun TelegramSession.mapPollMedia(media: PollMedia?): MessageMedia? {
    return when (media) {
        is PollMediaSticker -> {
            val sticker = media.sticker

            MessageMedia(
                kind = MessageMediaKind.Sticker,
                previewFileId = sticker.thumbnail?.file?.id,
                previewUniqueId = sticker.thumbnail?.file?.remote?.uniqueId,
                fileId = sticker.sticker.id,
                fileUniqueId = sticker.sticker.remote.uniqueId,
                stickerFileId = sticker.sticker.id,
                stickerUniqueId = sticker.sticker.remote.uniqueId,
                stickerSetId = sticker.setId,
                width = sticker.width,
                height = sticker.height,
                stickerFormat = when (sticker.format) {
                    is StickerFormatTgs -> MessageStickerFormat.Tgs
                    is StickerFormatWebm -> MessageStickerFormat.Webm
                    else -> MessageStickerFormat.Webp
                }
            )
        }

        is PollMediaPhoto -> {
            val photo = media.photo
            val size = photo.previewSize()

            MessageMedia(
                kind = MessageMediaKind.Photo,
                previewFileId = size?.photo?.id,
                previewUniqueId = size?.photo?.remote?.uniqueId,
                fileId = photo.sizes.maxByOrNull { it.photo.size }?.photo?.id,
                fileUniqueId = photo.sizes.maxByOrNull { it.photo.size }
                    ?.photo?.remote?.uniqueId,
                width = size?.width ?: 0,
                height = size?.height ?: 0,
                minithumbnail = photo.minithumbnail?.data
            )
        }

        is PollMediaAnimation -> {
            val animation = media.animation

            MessageMedia(
                kind = MessageMediaKind.Animation,
                previewFileId = animation.thumbnail?.file?.id,
                previewUniqueId = animation.thumbnail?.file?.remote?.uniqueId,
                fileId = animation.animation.id,
                fileUniqueId = animation.animation.remote.uniqueId,
                width = animation.width,
                height = animation.height,
                minithumbnail = animation.minithumbnail?.data
            )
        }

        else -> null
    }
}

internal fun TelegramSession.mapAudio(audio: Audio): MessageMedia {
    return MessageMedia(
        kind = MessageMediaKind.Audio,
        fileId = audio.audio.id,
        fileUniqueId = audio.audio.remote.uniqueId,
        fileName = audio.fileName.ifBlank {
            listOf(audio.performer, audio.title)
                .filter(String::isNotBlank)
                .joinToString(" - ")
                .ifBlank { "Audio" }
        },
        mimeType = audio.mimeType,
        size = audio.audio.size,
        durationSeconds = audio.duration,
        minithumbnail = audio.albumCoverMinithumbnail?.data
    )
}

internal fun TelegramSession.mapMedia(content: MessageContent): MessageMedia? {
    return when (content) {
        is MessageChatChangePhoto -> {
            val photo = content.photo
            val preview = photo.sizes
                .filter { it.width in 1..800 }
                .maxByOrNull { it.width }
                ?: photo.sizes.minByOrNull { it.width }
            val full = photo.sizes.maxByOrNull { it.width }

            MessageMedia(
                kind = MessageMediaKind.Photo,
                previewFileId = preview?.photo?.id,
                previewUniqueId = preview?.photo?.remote?.uniqueId,
                fileId = full?.photo?.id,
                fileUniqueId = full?.photo?.remote?.uniqueId,
                fileName = "Photo",
                mimeType = "image/jpeg",
                size = full?.photo?.size ?: 0,
                width = preview?.width ?: 0,
                height = preview?.height ?: 0,
                minithumbnail = photo.minithumbnail?.data
            )
        }

        is MessageChatSetBackground -> {
            // The service bubble should show the new wallpaper image
            // instead of only saying that the background changed.
            val document = content.background.background.document
            val preview = document?.thumbnail?.file
            val full = document?.document

            if (preview == null && full == null) {
                null
            } else {
                MessageMedia(
                    kind = MessageMediaKind.Photo,
                    previewFileId = preview?.id ?: full?.id,
                    previewUniqueId = preview?.remote?.uniqueId
                        ?: full?.remote?.uniqueId,
                    fileId = full?.id,
                    fileUniqueId = full?.remote?.uniqueId,
                    fileName = "Wallpaper",
                    mimeType = "image/jpeg",
                    size = full?.size ?: 0L,
                    width = document?.thumbnail?.width ?: 0,
                    height = document?.thumbnail?.height ?: 0
                )
            }
        }

        is MessagePhoto -> {
            val size = content.photo.previewSize()

            MessageMedia(
                kind = MessageMediaKind.Photo,
                previewFileId = size?.photo?.id,
                previewUniqueId = size?.photo?.remote?.uniqueId,
                fileId = content.photo.sizes.maxByOrNull { it.photo.size }?.photo?.id,
                fileUniqueId = content.photo.sizes.maxByOrNull { it.photo.size }
                    ?.photo?.remote?.uniqueId,
                fileName = "Photo",
                mimeType = "image/jpeg",
                size = size?.photo?.size ?: 0,
                width = size?.width ?: 0,
                height = size?.height ?: 0,
                minithumbnail = content.photo.minithumbnail?.data,
                isSpoiler = content.hasSpoiler
            )
        }

        is MessageDocument -> {
            val document = content.document
            val isImage = document.mimeType.startsWith("image/")
            val thumbnail = document.thumbnail

            MessageMedia(
                kind = if (isImage) MessageMediaKind.Photo else MessageMediaKind.File,
                previewFileId = if (isImage) {
                    thumbnail?.file?.id ?: document.document.id
                } else {
                    null
                },
                previewUniqueId = if (isImage) {
                    thumbnail?.file?.remote?.uniqueId
                        ?: document.document.remote.uniqueId
                } else {
                    null
                },
                fileId = document.document.id,
                fileUniqueId = document.document.remote.uniqueId,
                fileName = document.fileName,
                mimeType = document.mimeType,
                size = document.document.size,
                width = thumbnail?.width ?: 0,
                height = thumbnail?.height ?: 0,
                minithumbnail = document.minithumbnail?.data
            )
        }

        is MessageAudio -> mapAudio(content.audio)

        is MessageAnimation -> {
            val animation = content.animation

            MessageMedia(
                kind = MessageMediaKind.Animation,
                previewFileId = animation.thumbnail?.file?.id,
                previewUniqueId = animation.thumbnail?.file?.remote?.uniqueId,
                fileId = animation.animation.id,
                fileUniqueId = animation.animation.remote.uniqueId,
                fileName = animation.fileName.ifBlank { "GIF" },
                mimeType = animation.mimeType,
                size = animation.animation.size,
                width = animation.width,
                height = animation.height,
                minithumbnail = animation.minithumbnail?.data,
                isSpoiler = content.hasSpoiler
            )
        }

        is MessageVoiceNote -> {
            val note = content.voiceNote

            MessageMedia(
                kind = MessageMediaKind.VoiceNote,
                fileId = note.voice.id,
                fileUniqueId = note.voice.remote.uniqueId,
                fileName = "Voice message",
                mimeType = note.mimeType,
                size = note.voice.size,
                durationSeconds = note.duration,
                waveform = note.waveform
            )
        }

        is MessageVideoNote -> {
            val note = content.videoNote

            MessageMedia(
                kind = MessageMediaKind.VideoNote,
                previewFileId = note.thumbnail?.file?.id,
                previewUniqueId = note.thumbnail?.file?.remote?.uniqueId,
                fileId = note.video.id,
                fileUniqueId = note.video.remote.uniqueId,
                fileName = "Video message",
                mimeType = "video/mp4",
                size = note.video.size,
                width = note.length,
                height = note.length,
                minithumbnail = note.minithumbnail?.data
            )
        }

        is MessageVideo -> {
            val video = content.video

            MessageMedia(
                kind = MessageMediaKind.Video,
                previewFileId = video.thumbnail?.file?.id,
                previewUniqueId = video.thumbnail?.file?.remote?.uniqueId,
                fileId = video.video.id,
                fileUniqueId = video.video.remote.uniqueId,
                fileName = video.fileName,
                mimeType = video.mimeType,
                size = video.video.size,
                width = video.width,
                height = video.height,
                minithumbnail = video.minithumbnail?.data,
                isSpoiler = content.hasSpoiler,
                videoQualities = content.alternativeVideos
                    .map { alternative ->
                        MessageVideoQuality(
                            width = alternative.width,
                            height = alternative.height,
                            codec = alternative.codec,
                            fileId = alternative.video.id,
                            fileUniqueId = alternative.video.remote.uniqueId,
                            size = alternative.video.size
                        )
                    }
                    .sortedBy { quality -> quality.height }
            )
        }

        is MessageSticker -> {
            val sticker = content.sticker

            MessageMedia(
                kind = MessageMediaKind.Sticker,
                size = sticker.sticker.size,
                width = sticker.width,
                height = sticker.height,
                fileId = sticker.sticker.id,
                fileUniqueId = sticker.sticker.remote.uniqueId,
                stickerFileId = sticker.sticker.id,
                stickerUniqueId = sticker.sticker.remote.uniqueId,
                stickerSetId = sticker.setId,
                stickerFormat = when (sticker.format) {
                    is StickerFormatTgs -> MessageStickerFormat.Tgs
                    is StickerFormatWebm -> MessageStickerFormat.Webm
                    else -> MessageStickerFormat.Webp
                }
            )
        }

        is MessageAnimatedEmoji -> content.animatedEmoji.sticker?.let { sticker ->
            MessageMedia(
                kind = MessageMediaKind.Sticker,
                size = sticker.sticker.size,
                width = sticker.width,
                height = sticker.height,
                fileId = sticker.sticker.id,
                fileUniqueId = sticker.sticker.remote.uniqueId,
                stickerFileId = sticker.sticker.id,
                stickerUniqueId = sticker.sticker.remote.uniqueId,
                stickerFormat = when (sticker.format) {
                    is StickerFormatTgs -> MessageStickerFormat.Tgs
                    is StickerFormatWebm -> MessageStickerFormat.Webm
                    else -> MessageStickerFormat.Webp
                }
            )
        }

        is MessageGift -> content.gift.sticker.let { sticker ->
            MessageMedia(
                kind = MessageMediaKind.Sticker,
                size = sticker.sticker.size,
                width = sticker.width,
                height = sticker.height,
                fileId = sticker.sticker.id,
                fileUniqueId = sticker.sticker.remote.uniqueId,
                stickerFileId = sticker.sticker.id,
                stickerUniqueId = sticker.sticker.remote.uniqueId,
                stickerFormat = when (sticker.format) {
                    is StickerFormatTgs -> MessageStickerFormat.Tgs
                    is StickerFormatWebm -> MessageStickerFormat.Webm
                    else -> MessageStickerFormat.Webp
                }
            )
        }

        else -> null
    }.also { media ->
        media?.let(::rememberMediaFileName)
    }
}

internal fun TelegramSession.rememberMediaFileName(media: MessageMedia) {
    val fileId = media.fileId ?: return
    val name = media.fileName?.takeIf { it.isNotBlank() } ?: return

    mediaFileNames.update { names -> names + (fileId to name) }
}

internal fun TelegramSession.registerDownload(fileId: Int, size: Long) {
    val name = mediaFileNames.value[fileId] ?: return

    _downloads.update { current ->
        (
            listOf(
                DownloadedFile(
                    accountId = accountId,
                    fileId = fileId,
                    fileName = name,
                    size = size,
                    downloadedAt = currentTimeMillis()
                )
            ) + current.filterNot { file -> file.fileId == fileId }
        ).take(200)
    }
}

internal fun TelegramSession.messageCustomEmojis(content: MessageContent): List<CustomEmojiSpan> {
    val text = when (content) {
        is MessageText -> content.text
        is MessagePhoto -> content.caption
        is MessageDocument -> content.caption
        is MessageVideo -> content.caption
        is MessageAnimation -> content.caption
        else -> return emptyList()
    }

    return customEmojiSpans(text)
}

internal fun TelegramSession.customEmojiSpans(text: FormattedText): List<CustomEmojiSpan> {
    return text.entities.mapNotNull { entity ->
        val type = entity.type as? TextEntityTypeCustomEmoji
            ?: return@mapNotNull null

        CustomEmojiSpan(
            start = entity.offset,
            end = entity.offset + entity.length,
            emojiId = type.customEmojiId
        )
    }
}

internal fun Sticker.needsRepainting(): Boolean {
    return (fullType as? StickerFullTypeCustomEmoji)?.needsRepainting == true
}

/** Builds the custom emoji entities for outgoing formatted text. */
internal fun TelegramSession.customEmojiEntities(
    text: String,
    customEmojis: List<CustomEmojiSpan>
): Array<TextEntity> {
    if (customEmojis.isEmpty()) return emptyArray()

    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyArray()

    val leading = text.indexOf(trimmed).coerceAtLeast(0)

    return customEmojis.mapNotNull { span ->
        val start = span.start - leading
        val end = span.end - leading

        if (start < 0 || end > trimmed.length || start >= end) {
            null
        } else {
            TextEntity(
                offset = start,
                length = end - start,
                type = TextEntityTypeCustomEmoji(customEmojiId = span.emojiId)
            )
        }
    }.toTypedArray()
}

internal fun TelegramSession.mapSticker(sticker: Sticker): MessageMedia {
    return MessageMedia(
        kind = MessageMediaKind.Sticker,
        previewFileId = sticker.thumbnail?.file?.id,
        previewUniqueId = sticker.thumbnail?.file?.remote?.uniqueId,
        fileId = sticker.sticker.id,
        fileUniqueId = sticker.sticker.remote.uniqueId,
        stickerFileId = sticker.sticker.id,
        stickerUniqueId = sticker.sticker.remote.uniqueId,
        stickerSetId = sticker.setId,
        stickerId = sticker.id,
        stickerEmoji = sticker.emoji,
        needsRepainting = sticker.needsRepainting(),
        size = sticker.sticker.size,
        width = sticker.width,
        height = sticker.height,
        stickerFormat = when (sticker.format) {
            is StickerFormatTgs -> MessageStickerFormat.Tgs
            is StickerFormatWebm -> MessageStickerFormat.Webm
            else -> MessageStickerFormat.Webp
        }
    )
}

internal fun TelegramSession.mapAnimation(animation: Animation): MessageMedia {
    return MessageMedia(
        kind = MessageMediaKind.Animation,
        previewFileId = animation.thumbnail?.file?.id,
        fileId = animation.animation.id,
        fileName = animation.fileName.ifBlank { "GIF" },
        mimeType = animation.mimeType,
        size = animation.animation.size,
        width = animation.width,
        height = animation.height,
        minithumbnail = animation.minithumbnail?.data
    )
}

internal fun Photo.previewSize(): PhotoSize? {
    return sizes
        .filter { it.width in 1..800 }
        .maxByOrNull { it.width }
        ?: sizes.minByOrNull { it.width }
}

/**
 * Custom emoji entities live on the original message text, while the
 * preview is trimmed and has its newlines flattened. Offsets are remapped
 * onto the preview so the dialog list can render them inline.
 */
internal fun TelegramSession.messagePreviewCustomEmojis(
    message: Message?,
    preview: String
): List<CustomEmojiSpan> {
    if (message == null) return emptyList()

    val original = messageBody(message.content)
    if (original.replace('\n', ' ').trim() != preview) return emptyList()

    val leading = original.length - original.trimStart().length

    return messageCustomEmojis(message.content).mapNotNull { span ->
        val start = span.start - leading
        val end = span.end - leading

        if (start < 0 || end > preview.length || start >= end) {
            null
        } else {
            span.copy(start = start, end = end)
        }
    }
}

internal fun TelegramSession.messagePreview(message: Message?): String {
    if (message == null) return "No messages yet"

    return messageBody(message.content)
        .replace('\n', ' ')
        .trim()
        .ifBlank { "Message" }
}

internal fun TelegramSession.messageBody(content: MessageContent): String {
    return when (content) {
        is MessageText -> content.text.text
        is MessagePhoto -> content.caption.text.ifBlank { "Photo" }
        is MessageVideo -> content.caption.text.ifBlank { "Video" }
        is MessageAnimatedEmoji -> "Emoji"
        is MessageGift -> content.text.text.ifBlank { "Gift" }
        is MessageSticker -> "Sticker"
        is MessageDocument -> content.caption.text.ifBlank {
            content.document.fileName.ifBlank { "Document" }
        }
        is MessagePoll -> content.poll.question.text.ifBlank { "Poll" }
        is MessageLocation -> "Location"
        is MessageLiveLocation -> "Live location"
        is MessageSupergroupChatCreate -> supergroupChatCreateText(content)
        is MessageChatSetBackground -> "Chat background changed"
        is MessageChatChangePhoto -> "Chat photo changed"
        is MessageRichMessage -> richPlainText(content.message).ifBlank { "Rich message" }
        else -> serviceMessageText(content)
    }
}

