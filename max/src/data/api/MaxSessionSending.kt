package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.max.network.maxFailure
import com.mrboomdev.scrollix.data.max.network.maxInvalidChatId
import com.mrboomdev.scrollix.data.max.network.maxNotConnected
import com.mrboomdev.scrollix.data.max.network.maxUnsupported
import com.mrboomdev.scrollix.data.max.network.array
import com.mrboomdev.scrollix.data.max.network.long
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.max.network.objects
import com.mrboomdev.scrollix.data.max.network.string
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.max.network.uploadMaxFile
import com.mrboomdev.scrollix.data.max.network.uploadMaxRaw
import com.mrboomdev.scrollix.fetchUrlBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Drops deleted ids from the window, the dialog preview and the badges. */
internal fun MaxSession.removeDeletedLocally(
    providerDialogId: String,
    chatId: Long,
    messageIds: LongArray
) {
    histories.value[providerDialogId]?.remove(messageIds.toSet())

    val chat = chats[chatId]
    val lastMessageId = chat?.lastMessage?.id

    if (chat != null && lastMessageId != null && lastMessageId in messageIds) {
        chats[chatId] = chat.copy(lastMessage = null)
    }

    emitDialogs()
}

internal suspend fun MaxSession.sendAttachments(
    chatId: Long,
    text: String,
    attaches: JsonArray
): Result<MaxMessage?> {
    val payload = buildJsonObject {
        put("chatId", chatId)
        put("notify", true)
        put(
            "message",
            buildJsonObject {
                put("text", text)
                put("cid", System.currentTimeMillis())
                put("elements", JsonArray(emptyList()))
                put("attaches", attaches)
            }
        )
    }

    return try {
        val response = requestOnLiveConnection(opcode = 64, payload = payload)

        val message = response?.obj("message")?.let { parseMessage(chatId, it) }
        if (message != null && message.id != 0L) {
            applySentMessage(chatId, message)
        }

        Result.success(message)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}

internal suspend fun MaxSession.forwardCopy(targetChatId: Long, message: MaxMessage): Result<Unit> {
    var failure: Throwable? = null

    if (message.text.isNotBlank()) {
        sendText(targetChatId, message.text).onFailure { error -> failure = error }
    }

    message.attaches.forEach { attach ->
        forwardAttach(targetChatId, attach).onFailure { error ->
            if (failure == null) failure = error
        }
    }

    return failure?.let { error -> Result.failure(error) } ?: Result.success(Unit)
}

internal suspend fun MaxSession.sendText(chatId: Long, text: String): Result<Unit> {
    return sendAttachments(
        chatId = chatId,
        text = text,
        attaches = JsonArray(emptyList())
    ).map { }
}

internal suspend fun MaxSession.forwardAttach(targetChatId: Long, attach: MaxAttach): Result<Unit> {
    val type = attach.type.uppercase()

    if (type == "STICKER") {
        val stickerId = attach.stickerId
        val setId = attach.setId

        if (stickerId != null && setId != null) {
            return sendAttachments(
                chatId = targetChatId,
                text = "",
                attaches = buildJsonArray {
                    add(
                        buildJsonObject {
                            put("_type", "STICKER")
                            put("stickerType", "STATIC")
                            put("setId", setId)
                            put("stickerId", stickerId)
                        }
                    )
                }
            ).map { }
        }
    }

    // A sticker without server ids is copied as its image.
    if (type == "STICKER") return forwardAttachFile(targetChatId, attach)

    return when (type) {
        "PHOTO", "GIF", "VIDEO", "FILE", "UNSUPPORTED" ->
            forwardAttachFile(targetChatId, attach)

        else -> Result.success(Unit)
    }
}

internal suspend fun MaxSession.forwardAttachFile(
    targetChatId: Long,
    attach: MaxAttach
): Result<Unit> {
    val url = attach.url
        ?: return Result.failure(maxFailure("The attachment has no downloadable URL", SessionErrorCode.InvalidRequest))
    val bytes = fetchUrlBytes(url)
        ?: return Result.failure(maxFailure("Could not download the attachment", SessionErrorCode.Network))

    if (bytes.isEmpty()) {
        return Result.failure(maxFailure("The attachment is empty"))
    }

    val type = attach.type.uppercase()
    val image = sniffImage(bytes)
    val fileName = attach.fileName ?: defaultFileName(url, attach.mimeType)
    val extension = fileName.substringAfterLast('.', "bin")

    if (image != null && type != "FILE" && type != "VIDEO") {
        val photo = sendPhotoBytes(
            chatId = targetChatId,
            bytes = bytes,
            fileName = fileName,
            image = image,
            isGif = type == "GIF" || attach.isGif
        )

        if (photo.isSuccess) return photo
    }

    if (type == "VIDEO" || type == "GIF" || looksLikeMp4(bytes)) {
        val video = sendVideoBytes(
            chatId = targetChatId,
            bytes = bytes,
            width = attach.width,
            height = attach.height,
            durationSeconds = attach.durationSeconds,
            asGif = type == "GIF" || attach.isGif
        )

        if (video.isSuccess) return video
    }

    return sendFileAttach(
        chatId = targetChatId,
        bytes = bytes,
        fileName = fileName,
        extension = extension,
        contentType = attach.mimeType ?: image?.contentType ?: "application/octet-stream"
    )
}

internal fun MaxSession.defaultFileName(url: String, mimeType: String?): String {
    val extension = when {
        mimeType?.contains("jpeg") == true -> "jpg"
        mimeType?.contains("png") == true -> "png"
        mimeType?.contains("webp") == true -> "webp"
        mimeType?.contains("gif") == true -> "gif"
        mimeType?.contains("mp4") == true -> "mp4"
        mimeType?.contains("webm") == true -> "webm"
        else -> url.substringBefore('?').substringAfterLast('.', "bin")
            .takeIf { it.length in 1..5 } ?: "bin"
    }

    return "attachment.$extension"
}

internal suspend fun MaxSession.sendPhotoBytes(
    chatId: Long,
    bytes: ByteArray,
    fileName: String,
    image: MaxImageUpload,
    isGif: Boolean = false
): Result<Unit> {
    val upload = uploadPhoto(bytes, fileName, image.contentType)
        ?: return Result.failure(maxFailure("Could not upload the image to MAX", SessionErrorCode.Network))

    return sendPhotoAttach(chatId, upload, isGif)
}

/**
 * The photo attach shape differs between MAX builds. Every accepted shape
 * is tried in turn (an accepted send stops the loop), because a rejected
 * shape makes the whole message fail and nothing shows up in the chat.
 */
internal suspend fun MaxSession.sendPhotoAttach(
    chatId: Long,
    upload: MaxPhotoUpload,
    isGif: Boolean = false
): Result<Unit> {
    val attempts = buildList {
        upload.token?.let { token ->
            add(
                buildJsonObject {
                    put("_type", "PHOTO")
                    put("photoToken", token)
                    if (isGif) put("isGif", true)
                }
            )

            add(
                buildJsonObject {
                    put("_type", "PHOTO")
                    if (isGif) put("isGif", true)
                    put(
                        "photo",
                        buildJsonObject {
                            if (isGif) put("isGif", true)
                            put(
                                "photos",
                                buildJsonObject {
                                    put(
                                        "0",
                                        buildJsonObject { put("token", token) }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        upload.photoId?.let { photoId ->
            add(
                buildJsonObject {
                    put("_type", "PHOTO")
                    put("photoId", photoId)
                    if (isGif) put("isGif", true)
                }
            )
        }
    }

    if (attempts.isEmpty()) {
        return Result.failure(maxFailure("MAX did not return a photo token"))
    }

    var failure: Throwable? = null

    for (attach in attempts) {
        val result = sendAttachments(
            chatId = chatId,
            text = "",
            attaches = buildJsonArray { add(attach) }
        ).map { }

        if (result.isSuccess) return result

        failure = result.exceptionOrNull()
    }

    return Result.failure(failure ?: Exception("MAX did not accept the photo"))
}

/**
 * The attachment flow the MAX clients use: `FILE_UPLOAD` (87) → HTTP
 * upload → `NOTIF_ATTACH` (136) → `MSG_SEND` with a `FILE` attach. It takes
 * any bytes, so a sticker or GIF the photo pipeline refuses still lands in
 * the chat instead of silently disappearing.
 */
internal suspend fun MaxSession.sendFileAttach(
    chatId: Long,
    bytes: ByteArray,
    fileName: String,
    extension: String,
    contentType: String
): Result<Unit> {
    val response = runCatching {
        requireRpc().request(
            opcode = 87,
            payload = buildJsonObject {
                put("name", fileName)
                put("size", bytes.size)
                put("ext", extension)
                put("count", 1)
            }
        )
    }.getOrNull() ?: return Result.failure(maxFailure("MAX did not accept the upload", SessionErrorCode.InvalidRequest))

    val target = mediaUploadTarget(response, idKeys = emptySet())
        ?: return Result.failure(maxFailure("MAX did not return an upload URL"))

    val body = uploadMaxFile(target.url, fileName, bytes, contentType)
        ?: return Result.failure(maxFailure("Could not upload the file to MAX", SessionErrorCode.Network))

    val uploaded = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
    val info = uploaded?.obj("info")

    val fileKey = uploaded?.string("fileId")
        ?: info?.string("fileId")
        ?: uploaded?.array("info")?.objects()?.firstOrNull()?.string("fileId")
        ?: target.fileKey
        ?: return Result.failure(maxFailure("MAX did not return a file id"))

    val token = uploaded?.string("token")
        ?: info?.string("token")
        ?: uploaded?.array("info")?.objects()?.firstOrNull()?.string("token")
        ?: target.token

    awaitAttachReady()

    val attach = buildJsonObject {
        put("_type", "FILE")
        put("fileId", fileKey)
        token?.let { value -> put("token", value) }
        put("name", fileName)
        put("size", bytes.size)
    }

    return sendAttachments(
        chatId = chatId,
        text = "",
        attaches = buildJsonArray { add(attach) }
    ).map { }
}

/**
 * A file may only be attached after MAX confirms the upload with
 * `NOTIF_ATTACH` (136). The wait is best-effort: a send without the push is
 * still attempted after the timeout, so a missing notification cannot make
 * a sticker send hang forever.
 */
internal suspend fun MaxSession.awaitAttachReady(timeoutMillis: Long = 8_000) {
    val active = rpc ?: return

    runCatching {
        withTimeoutOrNull(timeoutMillis) {
            active.pushes.first { push -> push.opcode == 136 }
        }
    }
}

/**
 * `IMAGE_UPLOAD_URL` (80) answers with the upload URL; its query carries
 * the photo ids the server fills in after the POST, and the upload body
 * carries the token and ids MAX uses across its media endpoints.
 */
internal suspend fun MaxSession.uploadPhoto(
    bytes: ByteArray,
    fileName: String,
    contentType: String
): MaxPhotoUpload? {
    val response = runCatching {
        requireRpc().request(
            opcode = 80,
            payload = buildJsonObject { put("count", 1) }
        )
    }.getOrNull() ?: return null

    val target = mediaUploadTarget(
        response = response,
        idKeys = setOf("photoId", "id")
    ) ?: return null

    val body = uploadMaxFile(target.url, fileName, bytes, contentType)
        ?: return null

    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    awaitAttachReady()

    val upload = MaxPhotoUpload(
        token = photoTokenOf(root) ?: photoTokenOf(response) ?: target.token,
        photoId = root?.long("photoId")
            ?: root?.obj("photo")?.long("photoId")
            ?: target.id
            ?: photoIdFromUrl(target.url)
    )

    if (upload.token == null && upload.photoId == null) return null

    return upload
}

/** `photoIds` in the upload URL point at the photos the POST fills in. */
internal fun MaxSession.photoIdFromUrl(url: String): Long? {
    val value = url.substringAfter("photoIds=", "").substringBefore('&')

    return value.split(',', '%', ';')
        .firstNotNullOfOrNull { part -> part.toLongOrNull() }
}

/** Uploads an MP4 as a MAX video attachment (opcode 82). */
internal suspend fun MaxSession.sendVideoBytes(
    chatId: Long,
    bytes: ByteArray,
    width: Int,
    height: Int,
    durationSeconds: Int,
    asGif: Boolean = false
): Result<Unit> {
    val response = runCatching {
        requireRpc().request(
            opcode = 82,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("count", 1)
            }
        )
    }.getOrNull()
        ?: return Result.failure(maxFailure("MAX did not accept the video upload", SessionErrorCode.InvalidRequest))

    val target = mediaUploadTarget(
        response = response,
        idKeys = setOf("videoId", "id")
    ) ?: return Result.failure(maxFailure("MAX did not return a video upload URL"))

    val videoId = target.id
        ?: return Result.failure(maxFailure("MAX did not return a video id"))

    uploadMaxRaw(target.url, bytes, "video/mp4")
        ?: return Result.failure(maxFailure("Could not upload the GIF to MAX", SessionErrorCode.Network))

    awaitAttachReady()

    fun videoAttach(type: String, gif: Boolean) = buildJsonObject {
        put("_type", type)
        put("videoId", videoId)
        target.token?.let { token -> put("token", token) }

        if (gif) put("isGif", true)
        if (width > 0) put("width", width)
        if (height > 0) put("height", height)

        // MAX expects video durations in milliseconds.
        if (durationSeconds > 0) put("duration", durationSeconds * 1000L)
    }

    // A GIF is offered as a GIF attach first, so MAX animates it in place
    // instead of rendering a video bubble. Older builds only know VIDEO;
    // the `isGif` flag keeps the animation in that shape as well.
    val attaches = if (asGif) {
        listOf(
            videoAttach("GIF", true),
            videoAttach("VIDEO", true),
            videoAttach("VIDEO", false)
        )
    } else {
        listOf(videoAttach("VIDEO", false))
    }

    var lastFailure: Throwable? = null

    for (attach in attaches) {
        val result = sendAttachments(
            chatId = chatId,
            text = "",
            attaches = buildJsonArray { add(attach) }
        ).map { }

        if (result.isSuccess) return result
        lastFailure = result.exceptionOrNull()
    }

    return Result.failure(
        lastFailure ?: Exception("MAX did not accept the video")
    )
}

internal fun MaxSession.photoTokenOf(response: JsonElement?): String? {
    val root = response?.jsonObject ?: return null

    root.string("token")?.let { return it }
    root.string("photoToken")?.let { return it }

    root.obj("photo")?.let { photo ->
        (photo.string("token") ?: photo.string("photoToken"))?.let { return it }
    }

    val info = root.array("info")?.objects().orEmpty() +
        listOfNotNull(root.obj("info"))

    info.firstNotNullOfOrNull { entry ->
        entry.string("token") ?: entry.string("photoToken")
    }?.let { return it }

    val photos = root.array("photos")?.objects().orEmpty() +
        root.obj("photos")
            ?.values
            ?.mapNotNull { value -> value as? JsonObject }
            .orEmpty()

    return photos.firstNotNullOfOrNull { entry ->
        entry.string("token") ?: entry.string("photoToken")
    }
}

internal fun MaxSession.mediaUploadTarget(
    response: JsonElement?,
    idKeys: Set<String>
): MaxUploadTarget? {
    val root = response?.jsonObject ?: return null

    val candidates = buildList {
        add(root)
        root.obj("photo")?.let(::add)
        root.obj("info")?.let(::add)
        root.array("info")?.objects()?.let(::addAll)
        root.array("photos")?.objects()?.let(::addAll)
        root.obj("photos")?.values?.forEach { value ->
            (value as? JsonObject)?.let(::add)
        }
    }

    return candidates.firstNotNullOfOrNull { candidate ->
        val url = candidate.string("url")?.takeIf { it.startsWith("http") }
            ?: return@firstNotNullOfOrNull null

        var id: Long? = null
        for (key in idKeys) {
            id = candidate.long(key)
            if (id != null) break
        }

        MaxUploadTarget(
            url = url,
            token = candidate.string("token") ?: candidate.string("photoToken"),
            id = id,
            fileKey = candidate.string("fileId")
        )
    }
}

/** Detects the static image formats MAX accepts as a photo. */
internal fun MaxSession.sniffImage(bytes: ByteArray): MaxImageUpload? {
    fun matches(vararg values: Int): Boolean {
        if (bytes.size < values.size) return false

        return values.indices.all { index ->
            (bytes[index].toInt() and 0xFF) == values[index]
        }
    }

    return when {
        matches(0x89, 0x50, 0x4E, 0x47) -> MaxImageUpload("png", "image/png")
        matches(0xFF, 0xD8, 0xFF) -> MaxImageUpload("jpg", "image/jpeg")
        matches(0x47, 0x49, 0x46, 0x38) -> MaxImageUpload("gif", "image/gif")

        bytes.size > 12 &&
            bytes.decodeToString(0, 4) == "RIFF" &&
            bytes.decodeToString(8, 12) == "WEBP" ->
            MaxImageUpload("webp", "image/webp")

        else -> null
    }
}

/** An MP4/MOV container, which MAX takes through the video upload. */
internal fun MaxSession.looksLikeMp4(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    if (bytes.decodeToString(4, 8) != "ftyp") return false

    val brand = bytes.decodeToString(8, 12)
    return brand.startsWith("isom") ||
        brand.startsWith("mp4") ||
        brand.startsWith("M4V") ||
        brand.startsWith("qt")
}

internal fun MaxSession.unsupported(feature: String): Result<Unit> {
    return Result.failure(maxUnsupported(feature))
}

// --------------------------------------------------------- live updates --

internal suspend fun MaxSession.sendMessageImpl(providerDialogId: String, text: String, replyToMessageId: Long?, customEmojis: List<CustomEmojiSpan>): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())

    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return Result.failure(maxFailure("Message is empty", SessionErrorCode.InvalidRequest))
    }

    val payload = buildJsonObject {
        put("chatId", chatId)
        put("notify", true)
        put(
            "message",
            buildJsonObject {
                put("text", trimmed)
                put("cid", System.currentTimeMillis())
                put("elements", JsonArray(emptyList()))
                put("attaches", JsonArray(emptyList()))
                replyToMessageId?.let { id ->
                    // MAX replies are a `link` of type REPLY carrying the
                    // original message id.
                    put(
                        "link",
                        buildJsonObject {
                            put("type", "REPLY")
                            put("mid", id.toString())
                        }
                    )
                }
            }
        )
    }

    return try {
        val response = requestOnLiveConnection(opcode = 64, payload = payload)

        val message = response?.obj("message")?.let { parseMessage(chatId, it) }
        if (message != null && message.id != 0L) {
            applySentMessage(chatId, message)
        }

        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}

internal suspend fun MaxSession.deleteMessagesImpl(providerDialogId: String, messageIds: LongArray): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())

    if (messageIds.isEmpty()) return Result.success(Unit)

    // Message ids travel as strings in some MAX clients and as numbers in
    // others, and the server answers cmd=3 for the shape it does not
    // expect. Both are tried so a delete is never lost to the encoding.
    val idShapes = listOf(
        messageIds.map { id -> JsonPrimitive(id.toString()) },
        messageIds.map { id -> JsonPrimitive(id) }
    )

    var lastFailure: Throwable? = null

    // Deleting is always meant for every participant, so forMe=false is
    // tried first. MAX only cascades older messages when a CHAT_ACTIVITY
    // (92) watermark was set right before the delete; this client never
    // sends 92 (reads go through CHAT_READ, 50), so the delete is safe.
    for (forMe in listOf(false, true)) {
        for (ids in idShapes) {
            val payload = buildJsonObject {
                put("chatId", chatId)
                put("messageIds", JsonArray(ids))
                put("forMe", forMe)
            }

            val failure = try {
                requestOnLiveConnection(opcode = 66, payload = payload)
                null
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                exception
            }

            if (failure == null) {
                removeDeletedLocally(providerDialogId, chatId, messageIds)
                return Result.success(Unit)
            }

            lastFailure = failure
        }
    }

    return Result.failure(
        (lastFailure ?: Exception("MAX did not delete the message")).asSessionException()
    )
}

internal suspend fun MaxSession.forwardMessagesImpl(providerDialogId: String, messageIds: LongArray, targetDialogId: String): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())
    val targetChatId = targetDialogId.toLongOrNull()
        ?: return Result.failure(maxFailure("Invalid MAX target chat id", SessionErrorCode.InvalidRequest))

    if (messageIds.isEmpty()) return Result.success(Unit)

    return try {
        val response = requestOnLiveConnection(
            opcode = 71,
            payload = buildJsonObject {
                put("chatId", chatId)
                put(
                    "messageIds",
                    buildJsonArray {
                        messageIds.forEach { id -> add(JsonPrimitive(id.toString())) }
                    }
                )
            }
        )

        val messages = response?.array("messages").objects()
            .map { parseMessage(chatId, it) }
            .filter { it.id != 0L }
            .sortedBy { it.time }

        if (messages.isEmpty()) {
            return Result.failure(maxFailure("The forwarded message could not be loaded", SessionErrorCode.NotFound))
        }

        var failure: Throwable? = null

        messages.forEach { message ->
            forwardCopy(targetChatId, message).onFailure { error ->
                if (failure == null) failure = error
            }
        }

        failure?.let { error -> Result.failure(error) } ?: Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}

internal suspend fun MaxSession.sendPollImpl(providerDialogId: String, question: String, options: List<String>, isAnonymous: Boolean, allowsMultipleAnswers: Boolean, allowsRevoting: Boolean, questionCustomEmojis: List<CustomEmojiSpan>, optionCustomEmojis: List<List<CustomEmojiSpan>>, optionMediaPaths: List<String?>, isQuiz: Boolean, correctOptionIndex: Int?, explanation: String): Result<Unit> {
    if (isQuiz) {
        return Result.failure(
            UnsupportedOperationException("Quizzes are not supported for MAX")
        )
    }

    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())
    val trimmedQuestion = question.trim()
    val answers = options.map(String::trim).filter(String::isNotEmpty)

    if (trimmedQuestion.isEmpty() || answers.size < 2) {
        return Result.failure(maxFailure("A poll needs a question and two answers", SessionErrorCode.InvalidRequest))
    }

    // FLAG_SETTINGS_ANONYMOUS=1, MULTISELECT=2, REVOTE=4. Custom emojis
    // and option media are Telegram-only for now, so they are ignored.
    val settings = (if (isAnonymous) 1 else 0) or
        (if (allowsMultipleAnswers) 2 else 0) or
        (if (allowsRevoting) 4 else 0)

    val attach = buildJsonObject {
        put("_type", "POLL")
        put("title", trimmedQuestion)
        put(
            "answers",
            buildJsonArray {
                answers.forEach { answer ->
                    add(buildJsonObject { put("text", answer) })
                }
            }
        )
        put("settings", settings)
    }

    return sendAttachments(
        chatId = chatId,
        text = "",
        attaches = buildJsonArray { add(attach) }
    ).map { }
}

internal suspend fun MaxSession.votePollImpl(providerDialogId: String, messageId: Long, optionIds: List<String>): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())
    val active = rpc ?: return Result.failure(maxNotConnected())
    val answers = optionIds.mapNotNull(String::toLongOrNull)

    if (answers.isEmpty()) {
        return Result.failure(maxFailure("No poll answers selected", SessionErrorCode.InvalidRequest))
    }

    val pollId = histories.value[providerDialogId]
        ?.state
        ?.value
        ?.messages
        ?.firstOrNull { message -> message.messageId == messageId }
        ?.poll
        ?.id
        ?.takeIf { it != 0L }
        ?: fetchPoll(chatId, messageId)?.id
        ?: return Result.failure(maxFailure("Poll is not loaded", SessionErrorCode.NotFound))

    return try {
        active.request(
            opcode = 304,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("messageId", messageId.toString())
                put("pollId", pollId)
                put("answersIds", buildJsonArray { answers.forEach { add(JsonPrimitive(it)) } })
            }
        )

        refreshPoll(chatId, messageId)
        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}

internal suspend fun MaxSession.stopLiveLocationImpl(providerDialogId: String, messageId: Long): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())
    val active = rpc ?: return Result.failure(maxNotConnected())

    return try {
        active.request(
            opcode = 124,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("messageId", messageId.toString())
            }
        )

        histories.value[providerDialogId]?.mutate { message ->
            val location = message.location

            if (message.messageId == messageId && location != null) {
                message.copy(
                    location = location.copy(isLive = false, expiresInSeconds = 0)
                )
            } else {
                message
            }
        }

        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}

internal suspend fun MaxSession.setMessagePinnedImpl(providerDialogId: String, messageId: Long, isPinned: Boolean): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())

    return try {
        // MAX pins through CHAT_SETTINGS (55): an id pins, an empty string
        // clears the pin.
        requestOnLiveConnection(
            opcode = 55,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("pinMessageId", if (isPinned) messageId.toString() else "")
            }
        )

        chats[chatId]?.let { chat ->
            chats[chatId] = chat.copy(
                pinnedMessageId = messageId.takeIf { isPinned }
            )
        }

        emitDialogs()
        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}
