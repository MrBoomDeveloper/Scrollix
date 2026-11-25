package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.max.network.maxInvalidChatId
import com.mrboomdev.scrollix.data.max.network.maxNotConnected
import com.mrboomdev.scrollix.data.max.network.array
import com.mrboomdev.scrollix.data.max.network.asLongOrNull
import com.mrboomdev.scrollix.data.max.network.bool
import com.mrboomdev.scrollix.data.max.network.int
import com.mrboomdev.scrollix.data.max.network.long
import com.mrboomdev.scrollix.data.max.network.double
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.max.network.objects
import com.mrboomdev.scrollix.data.max.network.string
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMemberInfo
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.SharedMediaKind
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.fetchUrlBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun MaxSession.parseChat(objectValue: JsonObject): MaxChat {
    val id = objectValue.long("id") ?: 0
    val type = objectValue.string("type") ?: "DIALOG"
    val participantsObject = objectValue.obj("participants")
    val participants = participantsObject?.keys
        ?.mapNotNull { key -> key.toLongOrNull() }
        .orEmpty()

    val lastMessageObject = objectValue.obj("lastMessage")
    val options = objectValue.obj("options")

    return MaxChat(
        id = id,
        type = type,
        title = objectValue.string("title"),
        description = objectValue.string("description"),
        status = objectValue.string("status"),
        ownerId = objectValue.long("owner") ?: 0,
        adminIds = objectValue.array("admins")
            ?.mapNotNull { it.asLongOrNull() }
            ?.toSet()
            .orEmpty(),
        participantIds = participants,
        participantsCount = objectValue.int("participantsCount"),
        pictureUrl = objectValue.obj("picture")?.string("url")
            ?: objectValue.string("baseIconUrl")
            ?: objectValue.string("baseRawIconUrl"),
        lastMessage = lastMessageObject?.let { parseMessage(id, it) },
        unread = objectValue.int("newMessages") ?: 0,
        lastEventTime = objectValue.long("lastEventTime")
            ?: objectValue.long("modified")
            ?: objectValue.long("createdTime")
            ?: 0,
        pinnedMessageId = objectValue.obj("pinnedMessage")?.long("id"),
        isVerified = maxVerifiedFlag(objectValue),
        canCopyMessages = options?.bool("MESSAGE_COPY_NOT_ALLOWED") != true,
        allCanPinMessages = options?.bool("ALL_CAN_PIN_MESSAGE") == true
    )
}

/**
 * The profile was built before its avatar bytes were prefetched, so the
 * Accounts list kept showing initials for the signed-in user.
 */
internal fun MaxSession.refreshOwnProfileAvatar() {
    val me = users[meId] ?: return
    val current = _profile.value ?: return

    _profile.value = current.copy(avatar = avatarFor(me.name, me.pictureUrl))
}

internal fun MaxSession.emitDialogs() {
    val typingState = typing.value

    val sortedChats = chats.values
        .filter { chat -> chat.status != "REMOVED" }
        .sortedByDescending { chat -> chat.lastMessage?.time ?: chat.lastEventTime }

    _dialogs.value = sortedChats.map { chat -> toDialog(chat, typingState[chat.id]) }

    // The full list (avatar, title, last message, time, unread count) is
    // persisted after a short pause so bursts of updates write once.
    dialogsSaveJob?.cancel()
    dialogsSaveJob = scope.launch {
        delay(1_500)

        val dialogs = _dialogs.value
        if (dialogs.isNotEmpty()) {
            CacheStore.saveDialogs(accountId, dialogs)
        }
    }
}

internal fun MaxSession.toDialog(chat: MaxChat, typingText: String?): Dialog {
    val title = if (chat.id == 0L) {
        "Saved Messages"
    } else {
        chat.title
            ?: chat.peerUser()?.name
            ?: "MAX chat ${chat.id}"
    }

    val lastMessage = chat.lastMessage
    val media = lastMessage?.attaches?.firstOrNull()?.let { attach ->
        toMedia(attach, chat.id, lastMessage.id)
    }
    val peer = chat.peerUser()

    return Dialog(
        providerId = MaxProvider.id,
        accountId = accountId,
        providerDialogId = chat.id.toString(),
        identity = DialogIdentity(MaxProvider.id, chat.id.toString()),
        kind = chat.kind(),
        title = title,
        statusText = chat.participantsCount?.takeIf { chat.type != "DIALOG" }
            ?.let { count -> "$count members" },
        // A direct chat can offer the call action when MAX exposes the
        // peer's phone number.
        phoneNumber = peer?.phone,
        typingText = typingText,
        preview = lastMessage?.let(::previewText).orEmpty(),
        lastMessageMedia = media,
        lastMessageIsOutgoing = lastMessage?.senderId != null && lastMessage.senderId == meId,
        lastMessageIsRead = true,
        timestamp = lastMessage?.time ?: chat.lastEventTime,
        unreadCount = chat.unread,
        memberCount = chat.participantsCount,
        pinnedMessageId = chat.pinnedMessageId,
        // MAX does not expose posting rights for channels; the composer
        // stays available for every chat the account still belongs to and
        // the server reports a failure if posting is not allowed.
        canSendMessages = chat.status != "REMOVED",
        canPinMessages = meId != 0L && (
            meId == chat.ownerId ||
                meId in chat.adminIds ||
                chat.allCanPinMessages
        ),
        canDeleteMessages = chat.type != "DIALOG" && meId != 0L && (
            meId == chat.ownerId || meId in chat.adminIds
        ),
        isPinned = chat.id in pinnedChats,
        isSavedMessages = chat.id == 0L,
        isVerified = chat.isVerified || peer?.isVerified == true,
        order = lastMessage?.time ?: chat.lastEventTime,
        avatar = avatarFor(title, chat.pictureUrl ?: peer?.pictureUrl)
    )
}

internal fun MaxSession.previewText(message: MaxMessage): String {
    if (message.text.isNotBlank()) return message.text

    return message.attaches.firstOrNull()?.previewLabel.orEmpty()
}

internal suspend fun MaxSession.prefetchAvatars() {
    val urls = buildSet {
        chats.values.forEach { chat -> chat.pictureUrl?.let(::add) }
        users.values.forEach { user -> user.pictureUrl?.let(::add) }
    }
        .filterNot { url -> avatarBytes.containsKey(url) }
        .take(64)

    urls.chunked(6).forEach { chunk ->
        coroutineScope {
            chunk.map { url ->
                async {
                    url to runCatching { fetchUrlBytes(url) }.getOrNull()
                }
            }.awaitAll()
        }.forEach { (url, bytes) ->
            if (bytes != null && bytes.isNotEmpty()) {
                avatarBytes[url] = bytes
            }
        }
    }
}

internal fun MaxSession.avatarFor(name: String, url: String?): Avatar {
    val base = Avatar.fromName(name)

    if (url == null) return base

    return Avatar(
        initials = base.initials,
        colorSeed = base.colorSeed,
        image = avatarBytes[url],
        url = url
    )
}

// -------------------------------------------------------------- mapping --

internal fun MaxSession.parseUser(objectValue: JsonObject): MaxUser? = parseMaxUser(objectValue)

internal fun MaxSession.parseMessage(chatId: Long, objectValue: JsonObject): MaxMessage {
    val rawLink = objectValue.obj("link")
    val link = rawLink?.takeIf { it.string("type") == "FORWARD" }
    val forwards = link?.string("chatName")
    // Replies are a link of type REPLY carrying the original message id.
    val reply = rawLink?.takeIf { it.string("type") == "REPLY" }?.let { value ->
        val replyId = value.long("mid")
            ?: value.obj("message")?.long("id")
            ?: return@let null

        MessageReply(
            messageId = replyId,
            text = value.obj("message")?.string("text").orEmpty()
        )
    }

    val rawText = objectValue.string("text").orEmpty().ifBlank {
        link?.obj("message")?.string("text").orEmpty()
    }
    val isWelcome = rawText.trim() == SavedDialogWelcomeKey
    val text = if (isWelcome) {
        "Welcome to Saved Messages. Messages you send here are visible only to you."
    } else {
        rawText
    }

    val attaches = objectValue.array("attaches").objects().map(::parseAttach)

    return MaxMessage(
        id = objectValue.long("id") ?: 0,
        senderId = objectValue.long("sender"),
        text = text,
        time = objectValue.long("time") ?: 0,
        attaches = attaches,
        reactions = mapReactions(objectValue.obj("reactionInfo")),
        views = objectValue.obj("stats")?.int("views") ?: 0,
        forwardedFromName = forwards,
        replyTo = reply,
        isService = isWelcome || attaches.any { it.type.equals("CONTROL", true) }
    )
}

internal fun MaxSession.parseAttach(objectValue: JsonObject): MaxAttach {
    val type = objectValue.string("_type") ?: objectValue.string("type") ?: "UNKNOWN"
    val url = listOf("url", "fileUrl", "photoUrl", "baseUrl", "thumbnailUrl", "previewData")
        .firstNotNullOfOrNull { key ->
            objectValue.string(key)?.takeIf { it.startsWith("http") }
        }

    val latitude = objectValue.double("latitude")
        ?: objectValue.obj("location")?.double("latitude")
    val longitude = objectValue.double("longitude")
        ?: objectValue.obj("location")?.double("longitude")
    val livePeriod = objectValue.long("livePeriod")
        ?: objectValue.obj("location")?.long("livePeriod")
        ?: 0
    val photo = objectValue.obj("photo")
    val sticker = objectValue.obj("sticker")
    val video = objectValue.obj("video")
    // Video and GIF attachments in history only carry a server id and a
    // token. Their bytes are resolved lazily through GET_VIDEO_URL; the
    // static thumbnail is the only immediately downloadable URL.
    val thumbnailUrl = objectValue.string("thumbnail")
        ?.takeIf { it.startsWith("http") }
        ?: objectValue.obj("thumbnail")?.string("url")?.takeIf { it.startsWith("http") }
        ?: video?.string("thumbnail")?.takeIf { it.startsWith("http") }

    return MaxAttach(
        type = type,
        url = url,
        remoteId = objectValue.string("fileId")
            ?: objectValue.string("photoId")
            ?: objectValue.string("videoId")
            ?: objectValue.string("token"),
        fileName = objectValue.string("name") ?: objectValue.string("fileName"),
        mimeType = objectValue.string("mimeType") ?: objectValue.string("mime"),
        size = objectValue.long("size") ?: objectValue.long("fileSize") ?: 0,
        width = objectValue.int("width") ?: photo?.int("width")
            ?: video?.int("width") ?: 0,
        height = objectValue.int("height") ?: photo?.int("height")
            ?: video?.int("height") ?: 0,
        thumbnailUrl = thumbnailUrl,
        videoId = objectValue.long("videoId")
            ?: objectValue.long("video_id")
            ?: video?.long("videoId")
            ?: objectValue.long("gifId"),
        videoToken = objectValue.string("token") ?: video?.string("token"),
        previewBytes = objectValue.string("previewData")?.let(::decodeDataUri),
        durationSeconds = ((objectValue.long("duration") ?: 0) / 1000).toInt(),
        // MAX models an animated image as a photo with the `isGif` flag,
        // but some clients only send the file as `FILE`/`PHOTO` with a
        // GIF mime type or `.gif` name. Both shapes must stay animated.
        isGif = objectValue.bool("isGif") == true ||
            objectValue.bool("gif") == true ||
            photo?.bool("isGif") == true ||
            type.equals("GIF", ignoreCase = true) ||
            (objectValue.string("mimeType") ?: objectValue.string("mime"))
                ?.startsWith("image/gif", ignoreCase = true) == true ||
            (objectValue.string("name") ?: objectValue.string("fileName"))
                ?.endsWith(".gif", ignoreCase = true) == true ||
            url?.substringBefore('?')?.endsWith(".gif", ignoreCase = true) == true,
        stickerId = objectValue.long("stickerId") ?: sticker?.long("id"),
        setId = objectValue.long("setId") ?: sticker?.long("setId"),
        // MAX ships most stickers as Lottie animations; the static `url`
        // only carries the first frame.
        lottieUrl = objectValue.string("lottiePlayUrl")
            ?: objectValue.string("lottieUrl")
            ?: sticker?.string("lottieUrl"),
        latitude = latitude,
        longitude = longitude,
        // MAX sends the live period in milliseconds; small values are
        // treated as seconds for responses from older clients.
        livePeriodSeconds = when {
            livePeriod <= 0 -> 0
            livePeriod >= 1000 -> (livePeriod / 1000).toInt()
            else -> livePeriod.toInt()
        },
        poll = if (type.equals("POLL", true)) parsePoll(objectValue) else null
    )
}

internal fun MaxSession.parsePoll(objectValue: JsonObject): MaxPoll? {
    val source = objectValue.obj("poll") ?: objectValue
    val title = source.string("title") ?: return null
    val settings = source.int("settings") ?: 0
    val state = source.obj("state")
    val results = state?.array("result").objects()
    val answers = source.array("answers").objects()

    val options = answers.mapIndexed { index, answer ->
        val answerId = answer.long("answerId")
            ?: answer.long("id")
            ?: (index + 1).toLong()
        val result = results.firstOrNull { it.long("answerId") == answerId }

        MaxPollOption(
            id = answerId.toString(),
            text = answer.string("text").orEmpty(),
            votes = result?.int("voteCount") ?: answer.int("voteCount") ?: 0,
            // MAX marks the option the current account voted for in the
            // first bit of PollResult.options.
            isChosen = ((result?.int("options") ?: 0) and 1) != 0
        )
    }

    return MaxPoll(
        id = source.long("pollId") ?: objectValue.long("pollId") ?: 0,
        question = title,
        options = options,
        totalVoterCount = state?.int("total") ?: options.sumOf { it.votes },
        isAnonymous = (settings and 1) != 0,
        allowsMultipleAnswers = (settings and 2) != 0,
        isClosed = (settings and 8) != 0
    )
}

internal fun MaxSession.toChatMessage(chatId: Long, message: MaxMessage): ChatMessage {
    val user = message.senderId?.let(users::get)
    val chat = chats[chatId]
    val chatTitle = chat?.title ?: chat?.peerUser()?.name
    val isOwn = message.senderId != null && message.senderId == meId
    // MAX has no per-message rights: the author may delete a message and a
    // chat owner or admin moderates the rest. Channels expose no admin
    // list, so only authored posts are deletable there.
    val canModerate = chat != null && meId != 0L && (
        meId == chat.ownerId || meId in chat.adminIds
    )
    val canDelete = isOwn || canModerate

    return ChatMessage(
        providerId = MaxProvider.id,
        accountId = accountId,
        providerDialogId = chatId.toString(),
        messageId = message.id,
        senderName = user?.name ?: chatTitle,
        senderAvatar = user?.let { avatarFor(it.name, it.pictureUrl) }
            ?: chat?.let { avatarFor(chatTitle ?: "MAX chat", it.pictureUrl) },
        senderId = message.senderId ?: 0,
        forwardedFromName = message.forwardedFromName,
        replyTo = message.replyTo,
        text = message.text,
        timestamp = message.time,
        isOutgoing = isOwn,
        isService = message.isService,
        canBeDeleted = canDelete,
        canBeDeletedForEveryone = canDelete,
        canBeForwarded = chat?.canCopyMessages != false,
        media = message.attaches.firstOrNull()?.let { attach ->
            toMedia(attach, chatId, message.id)
        },
        // MAX delivers every attachment of one message in the same
        // `attaches` array. Mapping only the first one turned media
        // galleries into single images; the full list drives the album
        // grid and the fullscreen viewer.
        mediaItems = message.attaches.mapNotNull { attach ->
            toMedia(attach, chatId, message.id)
        },
        mediaAlbumId = message.id.takeIf { message.attaches.size > 1 },
        poll = message.attaches.firstNotNullOfOrNull { attach -> attach.toPollInfo() },
        location = message.attaches.firstNotNullOfOrNull { attach -> attach.toLocationInfo() },
        reactions = message.reactions,
        viewCount = message.views,
        isRead = true
    )
}

internal fun MaxSession.toMedia(
    attach: MaxAttach,
    chatId: Long,
    messageId: Long
): MessageMedia? {
    val isGif = attach.isGif ||
        attach.mimeType?.startsWith("image/gif", ignoreCase = true) == true ||
        attach.fileName?.endsWith(".gif", ignoreCase = true) == true ||
        attach.url?.substringBefore('?')?.endsWith(".gif", ignoreCase = true) == true

    val kind = when (attach.type.uppercase()) {
        // A GIF that MAX stores as a photo still has to animate in chat.
        "PHOTO" -> if (isGif) MessageMediaKind.Animation else MessageMediaKind.Photo
        "VIDEO" -> if (isGif) {
            MessageMediaKind.Animation
        } else {
            MessageMediaKind.Video
        }
        "AUDIO", "UNSUPPORTED" -> MessageMediaKind.VoiceNote
        // MAX usually wraps GIFs in a FILE attachment (sometimes naming
        // it `sticker.gif`), which used to render as a downloadable
        // binary instead of an autoplaying animation.
        "FILE" -> if (isGif) MessageMediaKind.Animation else MessageMediaKind.File
        "STICKER" -> MessageMediaKind.Sticker
        "GIF" -> MessageMediaKind.Animation
        else -> return null
    }

    val url = attach.url
    var fileId = url?.let(::mediaIdForUrl)

    // A video/GIF attachment usually has no direct URL. Give it a stable
    // synthetic media id and remember how to resolve its real link later.
    if (fileId == null && attach.videoId != null &&
        (kind == MessageMediaKind.Video || kind == MessageMediaKind.Animation)
    ) {
        val key = "max-video:$chatId:$messageId:${attach.videoId}"
        fileId = mediaIdForUrl(key)
        videoRefsById[fileId] = MaxVideoRef(
            chatId = chatId,
            messageId = messageId,
            videoId = attach.videoId,
            token = attach.videoToken
        )
    }

    // The preview is the static thumbnail when MAX provided one; the
    // animated attachment itself is not a preview image.
    val previewUrl = attach.thumbnailUrl
        ?: url?.takeIf { kind == MessageMediaKind.Photo || kind == MessageMediaKind.Sticker }
        ?: attach.url?.takeIf { kind == MessageMediaKind.Animation }
    val previewFileId = previewUrl?.let(::mediaIdForUrl)

    val stickerFormat = when {
        attach.mimeType == "application/x-tgsticker" || url?.endsWith(".tgs") == true ->
            MessageStickerFormat.Tgs

        attach.mimeType == "video/webm" || url?.endsWith(".webm") == true ->
            MessageStickerFormat.Webm

        else -> MessageStickerFormat.Webp
    }

    return MessageMedia(
        kind = kind,
        previewFileId = previewFileId ?: fileId,
        previewUniqueId = previewUrl ?: url,
        fileId = fileId,
        fileUniqueId = url ?: attach.remoteId,
        stickerFileId = fileId.takeIf { kind == MessageMediaKind.Sticker },
        stickerUniqueId = url.takeIf { kind == MessageMediaKind.Sticker },
        stickerFormat = stickerFormat,
        lottieUrl = attach.lottieUrl,
        fileName = attach.fileName,
        mimeType = attach.mimeType,
        size = attach.size,
        width = attach.width,
        height = attach.height,
        durationSeconds = attach.durationSeconds,
        minithumbnail = attach.previewBytes
    )
}

internal fun MaxSession.mapReactions(objectValue: JsonObject?): List<MessageReactionInfo> {
    val counters = objectValue?.array("counters") ?: objectValue?.array("reactions")
    val chosen = objectValue?.string("yourReaction")
        ?: objectValue?.string("your_reaction")

    return counters.objects().mapNotNull { counter ->
        // Depending on the deployment a counter carries the emoji as a
        // plain string or as a `{reactionType, id}` object.
        val reaction = counter.obj("reaction")
        val emoji = counter.string("reaction")
            ?: reaction?.string("emoji")
            ?: reaction?.string("id")
            ?: counter.string("emoji")
            ?: return@mapNotNull null

        MessageReactionInfo(
            emoji = emoji,
            count = counter.int("count") ?: reaction?.int("count") ?: 0,
            isChosen = emoji == chosen
        )
    }
}

internal fun MaxSession.mediaIdForUrl(url: String): Int {
    mediaIdsByUrl[url]?.let { return it }

    var candidate = url.hashCode()

    while (true) {
        val existing = mediaUrlsById[candidate]

        if (existing == null || existing == url) break

        candidate++
    }

    mediaIdsByUrl[url] = candidate
    mediaUrlsById[candidate] = url

    return candidate
}

internal fun MaxSession.mediaCachePath(url: String): String {
    return "max/$accountId/media/${url.hashCode()}-${url.length}.bin"
}

/**
 * MAX sends a small inline poster as a base64 data URI. Decoding it here
 * gives photos and GIFs something to show before the real bytes arrive.
 */
internal fun MaxSession.decodeDataUri(value: String): ByteArray? {
    val comma = value.indexOf(',')
    if (!value.startsWith("data:") || comma < 0) return null

    val header = value.substring(0, comma)
    if (!header.contains("base64", ignoreCase = true)) return null

    return runCatching {
        java.util.Base64.getDecoder().decode(value.substring(comma + 1))
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

// -------------------------------------------------------------- storage --

internal suspend fun MaxSession.loadUserProfileImpl(providerDialogId: String, userId: Long?): Result<UserProfileDetails> {
    val chat = providerDialogId.toLongOrNull()?.let(chats::get)
    val resolvedUserId = userId ?: chat?.peerUserId()

    if (resolvedUserId != null) {
        val user = users[resolvedUserId]
        val name = user?.name ?: chat?.title ?: "User $resolvedUserId"

        return Result.success(
            UserProfileDetails(
                userId = resolvedUserId,
                displayName = name,
                avatar = user?.let { avatarFor(it.name, it.pictureUrl) }
                    ?: chat?.let { avatarFor(name, it.pictureUrl) }
                    ?: Avatar.fromName(name),
                username = user?.username,
                phoneNumber = user?.phone,
                bio = user?.description,
                isVerified = user?.isVerified == true,
                kind = DialogKind.Direct,
                // MAX service accounts are the app's own notices and cannot
                // be blocked from a third-party client.
                canBlock = resolvedUserId != meId && user?.isServiceAccount != true
            )
        )
    }

    if (chat == null) {
        return Result.failure(maxInvalidChatId())
    }

    val title = chat.title ?: "MAX chat"
    val members = chat.participantIds.take(50).map { participantId ->
        val member = users[participantId]
        val name = member?.name ?: "User $participantId"

        ChatMemberInfo(
            userId = participantId,
            name = name,
            username = member?.username,
            avatar = member?.let { avatarFor(it.name, it.pictureUrl) }
        )
    }

    return Result.success(
        UserProfileDetails(
            displayName = title,
            avatar = avatarFor(title, chat.pictureUrl),
            kind = chat.kind(),
            isVerified = chat.isVerified,
            memberCount = chat.participantsCount,
            description = chat.description,
            members = members
        )
    )
}

internal suspend fun MaxSession.loadSharedMediaImpl(providerDialogId: String, kind: SharedMediaKind, fromMessageId: Long, limit: Int): Result<List<ChatMessage>> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())
    val active = rpc ?: return Result.failure(maxNotConnected())

    val type = when (kind) {
        SharedMediaKind.Photo -> "IMAGE"
        SharedMediaKind.Video -> "VIDEO"
        SharedMediaKind.Animation -> "VIDEO"
        SharedMediaKind.Voice -> "AUDIO"
        SharedMediaKind.Link -> "SHARE"
        SharedMediaKind.File -> "FILE"
    }

    return try {
        val response = active.request(
            opcode = 51,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("type", type)
                put("count", limit)
            }
        )

        val messages = response?.array("messages").objects()
            .map { parseMessage(chatId, it) }
            .filter { it.id != 0L }
            .sortedByDescending { it.time }
            .map { toChatMessage(chatId, it) }

        Result.success(messages)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}

// --------------------------------------------------------- dialog state --
