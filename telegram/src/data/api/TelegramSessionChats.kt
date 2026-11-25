package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.DialogFolderPosition
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.readFileBytes

internal fun TelegramSession.requestFolderChatsLoad(folderIds: Collection<Int>) {
    if (closed || folderIds.isEmpty()) return

    scope.launch {
        val toLoad = folderChatsMutex.withLock {
            folderIds.filter { folderId ->
                folderId !in loadedFolderChats &&
                    loadingFolderChats.add(folderId)
            }
        }

        for (folderId in toLoad) {
            scope.launch {
                var succeeded = false

                try {
                    loadChatsForFolder(folderId)
                    succeeded = true
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    if (!closed) {
                        println(
                            "Failed to load Telegram folder chats: " +
                                exception.message
                        )
                    }
                } finally {
                    folderChatsMutex.withLock {
                        loadingFolderChats.remove(folderId)

                        if (succeeded) {
                            loadedFolderChats.add(folderId)
                        }
                    }
                }
            }
        }
    }
}

internal fun TelegramSession.requestFolderCustomEmojiLoad() {
    folderEmojiJob?.cancel()
    if (closed) return

    folderEmojiJob = scope.launch {
        val ids = _folders.value
            .flatMap { it.customEmojis }
            .map { it.customEmojiId }
            .distinct()

        if (ids.isEmpty()) return@launch

        try {
            val stickers = loadCustomEmojiStickers(ids)
            if (stickers.isEmpty()) return@launch

            currentCoroutineContext().ensureActive()
            applyCustomEmojiMetadata(stickers)
            loadCustomEmojiAssets(stickers)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!closed) {
                println(
                    "Failed to load Telegram folder custom emoji: " +
                        exception.message
                )
            }
        }
    }
}

internal suspend fun TelegramSession.loadCustomEmojiStickers(
    ids: List<Long>
): Map<Long, Sticker> {
    val result = mutableMapOf<Long, Sticker>()
    val missing = mutableListOf<Long>()

    customEmojiCacheMutex.withLock {
        for (id in ids) {
            val sticker = customEmojiCache[id]

            if (sticker != null) {
                result[id] = sticker
            } else {
                missing += id
            }
        }
    }

    for (chunk in missing.chunked(200)) {
        when (val response = client.getCustomEmojiStickers(chunk.toLongArray())) {
            is TdlResult.Success -> {
                val stickers = response.result.stickers.mapNotNull { sticker ->
                    val type = sticker.fullType as? StickerFullTypeCustomEmoji
                        ?: return@mapNotNull null
                    type.customEmojiId to sticker
                }

                customEmojiCacheMutex.withLock {
                    for ((id, sticker) in stickers) {
                        customEmojiCache[id] = sticker
                        result[id] = sticker
                    }
                }
            }

            is TdlResult.Failure -> {
                if (!closed) {
                    println(
                        "Failed to load Telegram custom emoji stickers: " +
                            response.message
                    )
                }
            }
        }
    }

    return result
}

internal fun TelegramSession.applyCustomEmojiMetadata(stickers: Map<Long, Sticker>) {
    _folders.update { folders ->
        folders.map { folder ->
            folder.copy(
                customEmojis = folder.customEmojis.map { emoji ->
                    val sticker = stickers[emoji.customEmojiId]

                    if (sticker == null) {
                        emoji
                    } else {
                        val type = sticker.fullType as? StickerFullTypeCustomEmoji

                        emoji.copy(
                            needsRepainting = type?.needsRepainting == true,
                            format = sticker.format.toFolderEmojiFormat()
                        )
                    }
                }
            )
        }
    }
}

internal suspend fun TelegramSession.loadCustomEmojiAssets(stickers: Map<Long, Sticker>) {
    val thumbnails = mutableMapOf<Long, ByteArray>()

    for ((customEmojiId, sticker) in stickers) {
        currentCoroutineContext().ensureActive()

        val fileId = sticker.thumbnail?.file?.id ?: continue
        loadFileBytes(fileId)?.let { thumbnails[customEmojiId] = it }
    }

    if (thumbnails.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        applyCustomEmojiAssets(thumbnails = thumbnails)
    }

    val data = mutableMapOf<Long, ByteArray>()

    for ((customEmojiId, sticker) in stickers) {
        currentCoroutineContext().ensureActive()

        val fileId = sticker.sticker.id
        loadFileBytes(fileId)?.let { data[customEmojiId] = it }
    }

    if (data.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        applyCustomEmojiAssets(data = data)
    }
}

internal suspend fun TelegramSession.loadFileBytes(fileId: Int): ByteArray? {
    if (fileId == 0) return null
    return loadFile(fileId).getOrNull()
}

internal fun TelegramSession.applyCustomEmojiAssets(
    thumbnails: Map<Long, ByteArray> = emptyMap(),
    data: Map<Long, ByteArray> = emptyMap()
) {
    if (thumbnails.isEmpty() && data.isEmpty()) return

    _folders.update { folders ->
        folders.map { folder ->
            folder.copy(
                customEmojis = folder.customEmojis.map { emoji ->
                    emoji.copy(
                        thumbnail = thumbnails[emoji.customEmojiId]
                            ?: emoji.thumbnail,
                        data = data[emoji.customEmojiId] ?: emoji.data
                    )
                }
            )
        }
    }
}

internal fun StickerFormat.toFolderEmojiFormat(): DialogFolderEmojiFormat {
    return when (this) {
        is StickerFormatTgs -> DialogFolderEmojiFormat.Tgs
        is StickerFormatWebp -> DialogFolderEmojiFormat.Webp
        is StickerFormatWebm -> DialogFolderEmojiFormat.Webm
    }
}

internal suspend fun TelegramSession.loadChatsForFolder(folderId: Int) {
    var loadAttempts = 0

    while (!closed && loadAttempts < 1000) {
        loadAttempts++

        when (val result = client.loadChats(ChatListFolder(folderId), 100)) {
            is TdlResult.Success -> Unit
            is TdlResult.Failure -> {
                if (result.code != 404) {
                    println(
                        "Failed to load Telegram folder $folderId: " +
                            result.message
                    )
                }

                break
            }
        }
    }

    when (val result = client.getChats(ChatListFolder(folderId), 10_000)) {
        is TdlResult.Success -> {
            for (chatId in result.result.chatIds) {
                if (closed) return

                when (val chat = client.getChat(chatId)) {
                    is TdlResult.Success -> upsertChat(chat.result)
                    is TdlResult.Failure -> Unit
                }
            }
        }

        is TdlResult.Failure -> {
            if (result.code != 404) {
                println(
                    "Failed to get Telegram folder $folderId chats: " +
                        result.message
                )
            }
        }
    }

    emitDialogs()
}

internal suspend fun TelegramSession.loadAllChats() {
    _isLoadingDialogs.value = true

    try {
        var loadAttempts = 0

        while (!closed && loadAttempts < 1000) {
            loadAttempts++

            when (val result = client.loadChats(ChatListMain(), 100)) {
                is TdlResult.Success -> Unit
                is TdlResult.Failure -> {
                    if (result.code != 404) {
                        println("Failed to load Telegram chats: ${result.message}")
                    }
                    break
                }
            }
        }

        when (val result = client.getChats(ChatListMain(), 10_000)) {
            is TdlResult.Success -> {
                for (chatId in result.result.chatIds) {
                    if (closed) return
                    when (val chat = client.getChat(chatId)) {
                        is TdlResult.Success -> upsertChat(chat.result)
                        is TdlResult.Failure -> Unit
                    }
                }
            }

            is TdlResult.Failure -> {
                println("Failed to get Telegram chats: ${result.message}")
            }
        }

        loadPrivateUsers()
        emitDialogs()
        requestFolderChatsLoad(_folders.value.map { it.id })
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        if (!closed) {
            println("Failed to load Telegram dialogs: ${exception.message}")
        }
    } finally {
        _isLoadingDialogs.value = false
        dialogsSynced = true

        // Drop cached entries TDLib no longer reports now that the list is complete.
        emitDialogs()
    }
}

internal fun TelegramSession.avatarBytes(file: File?): ByteArray? {
    if (file == null || file.id == 0) return null

    avatarCache.value[file.id]?.let { return it }
    requestAvatar(file)
    return null
}

internal fun TelegramSession.requestAvatar(file: File) {
    var shouldLoad = false

    avatarRequests.update { requested ->
        if (file.id in requested || avatarCache.value.containsKey(file.id)) {
            requested
        } else {
            shouldLoad = true
            requested + file.id
        }
    }

    if (!shouldLoad) return

    val cacheKey = file.remote.uniqueId.takeIf { it.isNotBlank() }

    scope.launch {
        cacheKey?.let { key ->
            val cached = withContext(Dispatchers.IO) {
                CacheStore.readMedia(accountId, key)
            }

            if (cached != null && cached.isNotEmpty()) {
                avatarCache.update { it + (file.id to cached) }
                refreshReactionAvatars()
                refreshMessageSenders()
                emitDialogs()
                _users.value[currentUserId]?.let(::updateProfile)
                return@launch
            }
        }

        val result = try {
            client.downloadFile(
                fileId = file.id,
                priority = 16,
                offset = 0,
                limit = 0,
                synchronous = true
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

        if (result is TdlResult.Success && result.result.local.isDownloadingCompleted) {
            val bytes = readFileBytes(result.result.local.path)
            if (bytes != null && bytes.isNotEmpty()) {
                avatarCache.update { it + (file.id to bytes) }

                cacheKey?.let { key ->
                    withContext(Dispatchers.IO) {
                        CacheStore.saveMedia(accountId, key, bytes)
                    }
                }
                refreshReactionAvatars()
                refreshMessageSenders()
                emitDialogs()
                _users.value[currentUserId]?.let(::updateProfile)
            }
        }
    }
}

internal fun TelegramSession.loadPrivateUsers() {
    _chats.value.values
        .mapNotNull { (it.type as? ChatTypePrivate)?.userId }
        .distinct()
        .forEach(::requestUser)
}

internal fun TelegramSession.requestMemberCount(chat: Chat) {
    val key = when (val type = chat.type) {
        is ChatTypeSupergroup -> "supergroup:${type.supergroupId}"
        is ChatTypeBasicGroup -> "basicGroup:${type.basicGroupId}"
        else -> return
    }

    if (_memberCounts.value.containsKey(key)) return

    var shouldLoad = false
    _requestedMemberCounts.update { requested ->
        if (key in requested) {
            requested
        } else {
            shouldLoad = true
            requested + key
        }
    }

    if (!shouldLoad) return

    scope.launch {
        val memberCount = try {
            when (val type = chat.type) {
                is ChatTypeSupergroup -> when (
                    val result = client.getSupergroupFullInfo(type.supergroupId)
                ) {
                    is TdlResult.Success -> result.result.memberCount
                    is TdlResult.Failure -> null
                }

                is ChatTypeBasicGroup -> when (
                    val result = client.getBasicGroupFullInfo(type.basicGroupId)
                ) {
                    is TdlResult.Success -> result.result.members.size
                    is TdlResult.Failure -> null
                }

                else -> null
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

        if (memberCount != null && memberCount > 0) {
            _memberCounts.update { it + (key to memberCount) }
            emitDialogs()
        }
    }
}

/**
 * Verification is a property of the `supergroup` object, not of the chat
 * entry, so a channel's mark has to be fetched once and cached.
 */
internal fun TelegramSession.requestSupergroupVerification(supergroupId: Long) {
    if (supergroupId in _verifiedSupergroups.value) return

    var shouldLoad = false
    _requestedVerifiedSupergroups.update { requested ->
        if (supergroupId in requested) {
            requested
        } else {
            shouldLoad = true
            requested + supergroupId
        }
    }

    if (!shouldLoad) return

    scope.launch {
        when (val result = client.getSupergroup(supergroupId)) {
            is TdlResult.Success -> {
                if (result.result.verificationStatus?.isVerified == true) {
                    _verifiedSupergroups.update { it + supergroupId }
                    emitDialogs()
                }
            }

            // Not remembered as requested, so a later emit can retry.
            is TdlResult.Failure -> _requestedVerifiedSupergroups.update {
                it - supergroupId
            }
        }
    }
}

internal fun TelegramSession.requestUser(userId: Long) {
    if (_users.value.containsKey(userId)) return

    var shouldLoad = false
    _requestedUsers.update { requested ->
        if (userId in requested) {
            requested
        } else {
            shouldLoad = true
            requested + userId
        }
    }

    if (!shouldLoad) return

    scope.launch {
        when (val result = client.getUser(userId)) {
            is TdlResult.Success -> {
                _users.update { it + (userId to result.result) }
                refreshReactionAvatars()
                refreshMessageSenders()
                emitDialogs()
            }

            is TdlResult.Failure -> Unit
        }
    }
}

internal fun TelegramSession.toChatWallpaper(chatBackground: ChatBackground): ChatWallpaper {
    val background = chatBackground.background
    val type = background.type
    val fill = when (type) {
        is BackgroundTypeFill -> type.fill
        is BackgroundTypePattern -> type.fill
        else -> null
    }

    val colors = when (fill) {
        is BackgroundFillSolid -> listOf(fill.color)
        is BackgroundFillGradient -> listOf(fill.topColor, fill.bottomColor)
        is BackgroundFillFreeformGradient -> fill.colors.toList()
        else -> emptyList()
    }

    val document = background.document

    return ChatWallpaper(
        fileId = document?.document?.id,
        fileUniqueId = document?.document?.remote?.uniqueId
            ?.takeIf { it.isNotBlank() },
        thumbnailFileId = document?.thumbnail?.file?.id
            ?: document?.document?.id,
        thumbnailUniqueId = document?.thumbnail?.file?.remote?.uniqueId
            ?.takeIf { it.isNotBlank() }
            ?: document?.document?.remote?.uniqueId?.takeIf { it.isNotBlank() },
        colors = colors,
        gradientAngle = (fill as? BackgroundFillGradient)?.rotationAngle ?: 0,
        isPattern = type is BackgroundTypePattern,
        intensity = (type as? BackgroundTypePattern)?.intensity ?: 100,
        darkThemeDimming = chatBackground.darkThemeDimming
    )
}

internal fun TelegramSession.requestChannelPostRights(chatId: Long) {
    var shouldRequest = false

    _channelPostRightRequests.update { requested ->
        if (chatId in requested) {
            requested
        } else {
            shouldRequest = true
            requested + chatId
        }
    }

    if (!shouldRequest) return

    val userId = currentUserId ?: return

    scope.launch {
        val allowed = when (val result = client.getChatMember(
            chatId,
            MessageSenderUser(userId)
        )) {
            is TdlResult.Success -> when (result.result.status) {
                is ChatMemberStatusAdministrator, is ChatMemberStatusCreator -> true
                else -> false
            }

            is TdlResult.Failure -> false
        }

        _channelPostRights.update { rights -> rights + (chatId to allowed) }

        // A channel admin gets the composer even though broadcast channel
        // permissions only describe what regular subscribers may do.
        if (allowed) {
            emitDialogs()
        }
    }
}

internal fun TelegramSession.emitDialogs() {
    val dialogs = _chats.value.values.mapNotNull { entry ->
        val mainPosition = entry.positions.firstOrNull {
            it.list is ChatListMain && it.order != 0L
        } ?: return@mapNotNull null

        toDialog(entry, mainPosition)
    }.sortedWith(dialogOrder)

    // While the first sync is still running, keep the cached snapshot in
    // front of whatever TDLib has delivered so far, otherwise the list
    // visibly fills in chat by chat after every restart.
    // Until the first full sync finishes, every partial emit is merged over
    // the cached snapshot instead of replacing it, otherwise the list is
    // wiped and then visibly fills in chat by chat.
    _dialogs.value = if (!dialogsSynced && restoredDialogs.isNotEmpty()) {
        mergeDialogs(restoredDialogs, dialogs)
    } else {
        dialogs
    }
}

/**
 * Builds the UI dialog for a chat. [mainPosition] is null for public chats
 * which are not in the chat list yet; those keep neutral ordering fields.
 */
internal fun TelegramSession.toDialog(entry: ChatEntry, mainPosition: ChatPosition?): Dialog {
    val users = _users.value

        val privateUserId = (entry.type as? ChatTypePrivate)?.userId
        val isSavedMessages = privateUserId != null && privateUserId == currentUserId
        val user = privateUserId?.let { users[it] }
        val kind = when (val type = entry.type) {
            is ChatTypePrivate -> {
                if (user?.type is UserTypeBot) DialogKind.Bot else DialogKind.Direct
            }

            is ChatTypeBasicGroup -> DialogKind.Group
            is ChatTypeSupergroup -> {
                if (type.isChannel) DialogKind.Channel else DialogKind.Group
            }

            is ChatTypeSecret -> DialogKind.Secret
        }

        val identity = when (val type = entry.type) {
            is ChatTypePrivate -> DialogIdentity(
                providerId = providerId,
                remoteId = "user:${type.userId}"
            )

            is ChatTypeBasicGroup -> DialogIdentity(
                providerId = providerId,
                remoteId = "basicGroup:${type.basicGroupId}"
            )

            is ChatTypeSupergroup -> DialogIdentity(
                providerId = providerId,
                remoteId = "supergroup:${type.supergroupId}"
            )

            is ChatTypeSecret -> DialogIdentity(
                providerId = providerId,
                // A secret chat is 1:1 with a user. Keying it by the peer
                // keeps it distinct from that user's normal chat while
                // letting the same contact's secret chats from several
                // accounts merge into one row.
                remoteId = "secret:user:${type.userId}"
            )
        }

        val photoFile = entry.photo?.small ?: user?.profilePhoto?.small
        val imageBytes = photoFile?.let(::avatarBytes)
            ?: entry.photo?.minithumbnail?.data
            ?: user?.profilePhoto?.minithumbnail?.data
        val lastMessage = entry.lastMessage
        val isChannelChat = (entry.type as? ChatTypeSupergroup)?.isChannel == true

        if (isChannelChat) {
            requestChannelPostRights(entry.chatId)
        }

        return Dialog(
            providerId = providerId,
            accountId = accountId,
            providerDialogId = entry.chatId.toString(),
            identity = identity,
            kind = kind,
            title = if (isSavedMessages) "Saved Messages" else entry.title,
            statusText = if (isSavedMessages) null else user?.let(::userStatusText),
            // Only direct chats with a known phone number offer the call
            // action; bots, groups and phone-less contacts hide it.
            phoneNumber = user?.phoneNumber?.ifBlank { null },
            typingText = if (isSavedMessages) null else entry.typingText,
            emojiStatusId = if (isSavedMessages) {
                null
            } else {
                emojiStatusId(entry.emojiStatus) ?: emojiStatusId(user?.emojiStatus)
            },
            preview = messagePreview(lastMessage),
            previewCustomEmojis = messagePreviewCustomEmojis(
                message = lastMessage,
                preview = messagePreview(lastMessage)
            ),
            lastMessageMedia = lastMessage?.let { message ->
                mapMedia(message.content)
            },
            lastMessageIsOutgoing = lastMessage?.isOutgoing == true,
            lastMessageIsRead = lastMessage != null &&
                lastMessage.isOutgoing &&
                lastMessage.id <= entry.lastReadOutboxMessageId,
            timestamp = (lastMessage?.date ?: 0).toLong() * 1000L,
            unreadCount = entry.unreadCount,
            memberCount = _memberCounts.value[identity.remoteId]?.takeIf { it > 0 },
            pinnedMessageId = _pinnedMessageIds.value[entry.chatId]?.takeIf { it > 0 },
        canSendMessages = entry.permissions.canSendBasicMessages ||
            (isChannelChat && _channelPostRights.value[entry.chatId] == true),
        canPinMessages = entry.permissions.canPinMessages,
        // Only moderators may delete someone else's message; every
        // account may delete its own.
        canDeleteMessages = entry.type !is ChatTypePrivate &&
            entry.permissions.canChangeInfo,
        isPinned = mainPosition?.isPinned == true,
            isMarkedAsUnread = entry.isMarkedAsUnread,
            isVerified = when (val type = entry.type) {
                is ChatTypePrivate -> user?.verificationStatus?.isVerified == true
                is ChatTypeSupergroup -> type.supergroupId in _verifiedSupergroups.value
                else -> false
            },
            isForum = entry.viewAsTopics,
            hasMainWebApp = entry.type is ChatTypePrivate &&
                (user?.type as? UserTypeBot)?.hasMainWebApp == true,
            isSavedMessages = isSavedMessages,
            order = mainPosition?.order ?: 0,
            folderIds = entry.positions.mapNotNull { position ->
                (position.list as? ChatListFolder)?.chatFolderId
            }.toSet(),
            folderPositions = entry.positions.mapNotNull { position ->
                val folderId = (position.list as? ChatListFolder)?.chatFolderId
                    ?: return@mapNotNull null

                DialogFolderPosition(
                    folderId = folderId,
                    order = position.order,
                    isPinned = position.isPinned
                )
            },
            wallpaper = entry.background?.let(::toChatWallpaper),
            avatar = if (isSavedMessages) {
                null
            } else {
                imageBytes?.let { image ->
                    Avatar(
                        initials = Avatar.fromName(entry.title).initials,
                        colorSeed = entry.title.hashCode(),
                        image = image
                    )
                } ?: Avatar.fromName(entry.title)
            }
        )
}

internal fun TelegramSession.mergeDialogs(restored: List<Dialog>, loaded: List<Dialog>): List<Dialog> {
    val merged = LinkedHashMap<String, Dialog>(restored.size + loaded.size)

    restored.forEach { dialog -> merged[dialog.providerDialogId] = dialog }
    loaded.forEach { dialog -> merged[dialog.providerDialogId] = dialog }

    return merged.values.sortedWith(dialogOrder)
}

private val dialogOrder = compareByDescending<Dialog> {
    if (it.isPinned) 1 else 0
}
    .thenByDescending { it.order }
    .thenByDescending { it.timestamp }

internal suspend fun TelegramSession.setDialogUnreadImpl(
    providerDialogId: String,
    unread: Boolean
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.toggleChatIsMarkedAsUnread(
            chatId = chatId,
            isMarkedAsUnread = unread
        )) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}
