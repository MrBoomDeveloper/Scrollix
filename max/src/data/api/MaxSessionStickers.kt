package com.mrboomdev.scrollix.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.mrboomdev.scrollix.data.max.network.maxFailure
import com.mrboomdev.scrollix.data.max.network.maxInvalidChatId
import com.mrboomdev.scrollix.data.max.network.array
import com.mrboomdev.scrollix.data.max.network.int
import com.mrboomdev.scrollix.data.max.network.long
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.max.network.objects
import com.mrboomdev.scrollix.data.max.network.string
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.StickerSetInfo
import com.mrboomdev.scrollix.media.renderStickerAnimationGif

internal suspend fun MaxSession.loadMaxStickerSets(force: Boolean = false): Map<Long, StickerSetInfo> {
    if (!force && maxStickerPacks.isNotEmpty()) return maxStickerPacks

    return try {
        val media = mutableMapOf<Long, MutableList<MessageMedia>>()
        val titles = mutableMapOf<Long, String>()

        // MAX uses both singular and plural asset type names depending on
        // the deployment; request both so a pack list is never empty just
        // because of the label.
        listOf("STICKER", "STICKERS").forEach { assetType ->
            requestOnLiveConnection(
                opcode = 27,
                payload = buildJsonObject {
                    put("type", assetType)
                    put("sync", 0)
                }
            )?.let { root -> collectStickerSets(root, media, titles) }
        }

        // The catalogue is published across several sections; walk each
        // one until it stops returning new pages.
        listOf(
            "NEW_STICKER_SETS",
            "STICKER_SETS",
            "MY_STICKER_SETS",
            "FAVORITE_STICKER_SETS"
        ).forEach { section ->
            var from = 0

            repeat(4) {
                val page = requestOnLiveConnection(
                    opcode = 26,
                    payload = buildJsonObject {
                        put("sectionId", section)
                        put("from", from)
                        put("count", 100)
                    }
                )

                page?.let { root -> collectStickerSets(root, media, titles) }

                val marker = page?.long("marker") ?: 0
                if (marker <= 0 || marker.toInt() == from) return@repeat
                from = marker.toInt()
            }
        }

        maxStickerPacks.clear()

        media.forEach { (setId, rawStickers) ->
            val stickers = rawStickers
                .filter { sticker -> sticker.stickerId != null }
                .distinctBy { sticker -> sticker.stickerId }

            if (stickers.isNotEmpty()) {
                maxStickerPacks[setId] = StickerSetInfo(
                    id = setId,
                    title = titles[setId] ?: "$SyntheticStickerPackTitle #$setId",
                    name = "max-$setId",
                    isInstalled = true,
                    stickers = stickers
                )
            }
        }

        maxStickerPacks
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        maxStickerPacks
    }
}

/** Walks a sticker-catalogue response and keeps every set that has images. */
internal fun MaxSession.collectStickerSets(
    element: JsonElement?,
    media: MutableMap<Long, MutableList<MessageMedia>>,
    titles: MutableMap<Long, String>
) {
    when (element) {
        is JsonArray -> element.forEach { item ->
            collectStickerSets(item, media, titles)
        }

        is JsonObject -> {
            // Some MAX responses put the set metadata in `set` and the
            // sticker list in `stickers`; both are accepted here.
            val setObject = element.obj("set")
            val rawStickers = element.array("stickers")?.objects()
                ?: setObject?.array("stickers")?.objects()

            if (rawStickers != null) {
                val setId = rawStickers.firstNotNullOfOrNull { sticker -> sticker.long("setId") }
                    ?: setObject?.long("id")
                    ?: element.long("setId")
                    ?: element.long("id")

                if (setId != null) {
                    titles.putIfAbsent(
                        setId,
                        element.string("title") ?: element.string("name")
                            ?: "$SyntheticStickerPackTitle #$setId"
                    )

                    val stickers = media.getOrPut(setId) { mutableListOf() }
                    rawStickers.forEach { sticker ->
                        parseStickerMedia(sticker, setId)?.let(stickers::add)
                    }
                }
            }

            element.values.forEach { value ->
                collectStickerSets(value, media, titles)
            }
        }

        else -> Unit
    }
}

internal fun MaxSession.parseStickerMedia(objectValue: JsonObject, setId: Long): MessageMedia? {
    val stickerId = objectValue.long("id") ?: return null
    val url = listOf("url", "iconUrl", "imageUrl", "baseUrl")
        .firstNotNullOfOrNull { key ->
            objectValue.string(key)?.takeIf { it.startsWith("http") }
        }
        ?: objectValue.obj("photo")?.string("url")?.takeIf { it.startsWith("http") }
        ?: objectValue.obj("sticker")?.string("url")?.takeIf { it.startsWith("http") }
    val lottieUrl = objectValue.string("lottiePlayUrl")
        ?: objectValue.string("lottieUrl")
        ?: objectValue.obj("sticker")?.string("lottieUrl")

    if (url == null && lottieUrl == null) return null

    val fileId = url?.let(::mediaIdForUrl)
    val emoji = (objectValue.array("tags")?.firstOrNull() as? JsonPrimitive)?.content

    return MessageMedia(
        kind = MessageMediaKind.Sticker,
        previewFileId = fileId,
        previewUniqueId = url,
        fileId = fileId,
        fileUniqueId = url,
        stickerFileId = fileId,
        stickerUniqueId = url,
        stickerFormat = MessageStickerFormat.Webp,
        // Packs may nest the set id inside the sticker entry.
        stickerSetId = objectValue.long("setId") ?: setId,
        stickerId = stickerId,
        stickerEmoji = emoji,
        lottieUrl = lottieUrl,
        width = objectValue.int("width") ?: objectValue.obj("photo")?.int("width") ?: 0,
        height = objectValue.int("height") ?: objectValue.obj("photo")?.int("height") ?: 0
    )
}

internal fun MaxSession.collectedStickerMedia(): List<MessageMedia> {
    return histories.value.values
        .flatMap { controller -> controller.state.value.messages }
        .asSequence()
        .flatMap { message -> message.allMedia.asSequence() }
        .filter { media -> media.kind == MessageMediaKind.Sticker }
        .filter { media -> media.fileId != null || media.fileUniqueId != null }
        .distinctBy { media -> media.stickerUniqueId ?: media.fileUniqueId ?: media.fileId }
        .toList()
}

internal suspend fun MaxSession.sendStickerImpl(providerDialogId: String, sticker: MessageMedia): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())
    val stickerId = sticker.stickerId

    // A sticker from a MAX pack goes out natively: MAX only needs its own
    // sticker id, so the animation and attribution stay intact.
    if (stickerId != null) {
        val native = sendAttachments(
            chatId = chatId,
            text = "",
            attaches = buildJsonArray {
                add(
                    buildJsonObject {
                        put("_type", "STICKER")
                        put("stickerId", stickerId)
                    }
                )
            }
        )

        // A server id the pack no longer accepts falls back to uploading
        // the sticker image, so the sticker still reaches the chat.
        if (native.isSuccess) return native.map { }
    }

    val bytes = loadMediaBytes(
        fileId = sticker.stickerFileId ?: sticker.fileId,
        uniqueId = sticker.stickerUniqueId ?: sticker.fileUniqueId
    ) ?: return Result.failure(maxFailure("Could not load the sticker", SessionErrorCode.NotFound))

    return sendStickerBytes(
        providerDialogId = providerDialogId,
        bytes = bytes,
        format = sticker.stickerFormat,
        width = sticker.width,
        height = sticker.height
    )
}

internal suspend fun MaxSession.sendStickerBytesImpl(providerDialogId: String, bytes: ByteArray, format: MessageStickerFormat, width: Int, height: Int): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())

    if (bytes.isEmpty()) {
        return Result.failure(maxFailure("Sticker is empty"))
    }

    val image = sniffImage(bytes)

    val upload = if (image != null) {
        bytes to image
    } else {
        // TGS (gzip) and WebM are not images, so they have to be
        // rasterized before MAX can display them.
        val gif = renderStickerAnimationGif(
            bytes = bytes,
            format = format,
            width = width,
            height = height
        ) ?: return Result.failure(
            UnsupportedOperationException(
                "This sticker could not be converted for MAX"
            )
        )

        gif to MaxImageUpload("gif", "image/gif")
    }

    val fileName = "sticker.${upload.second.extension}"
    val photo = sendPhotoBytes(
        chatId = chatId,
        bytes = upload.first,
        fileName = fileName,
        image = upload.second,
        isGif = upload.second.extension == "gif"
    )

    if (photo.isSuccess) return photo

    // MAX does not take every sticker image through the photo pipeline, so
    // fall back to the documented file attachment instead of dropping it.
    return sendFileAttach(
        chatId = chatId,
        bytes = upload.first,
        fileName = fileName,
        extension = upload.second.extension,
        contentType = upload.second.contentType
    )
}

internal suspend fun MaxSession.sendAnimationBytesImpl(providerDialogId: String, bytes: ByteArray, mimeType: String?, width: Int, height: Int, durationSeconds: Int): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())

    if (bytes.isEmpty()) {
        return Result.failure(maxFailure("Animation is empty"))
    }

    val image = sniffImage(bytes)
    val extension = image?.extension ?: "gif"
    val fileName = "animation.$extension"

    if (image != null) {
        // `isGif` is what makes MAX render the photo as an animation
        // instead of a still picture.
        val photo = sendPhotoBytes(chatId, bytes, fileName, image, isGif = true)

        if (photo.isSuccess) return photo
    }

    if (mimeType?.startsWith("video/") == true || looksLikeMp4(bytes)) {
        val video = sendVideoBytes(
            chatId = chatId,
            bytes = bytes,
            width = width,
            height = height,
            durationSeconds = durationSeconds,
            asGif = true
        )

        if (video.isSuccess) return video
    }

    // A GIF MAX refuses as a photo or video still goes out as a file.
    return sendFileAttach(
        chatId = chatId,
        bytes = bytes,
        fileName = fileName,
        extension = extension,
        contentType = image?.contentType ?: mimeType ?: "image/gif"
    )
}
