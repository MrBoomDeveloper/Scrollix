package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import com.mrboomdev.scrollix.data.model.CustomEmojiAsset
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.WebAppLaunch
import com.mrboomdev.scrollix.data.model.SharedMediaKind
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.storage.CacheStore

internal suspend fun TelegramSession.reorderPinnedDialogsImpl(providerDialogIds: List<String>, folderId: Int?): Result<Unit> {
    val chatIds = providerDialogIds.mapNotNull { id -> id.toLongOrNull() }
    if (chatIds.isEmpty()) return Result.success(Unit)

    val chatList = folderId?.let { folder -> ChatListFolder(folder) } ?: ChatListMain()

    return try {
        // Telegram orders pinned chats by the moment they were pinned.
        // Unpin everything first, then pin from the last item to the first
        // so the requested order is what the server returns.
        chatIds.forEach { chatId ->
            client.toggleChatIsPinned(
                chatList = chatList,
                chatId = chatId,
                isPinned = false
            )
        }

        chatIds.asReversed().forEach { chatId ->
            client.toggleChatIsPinned(
                chatList = chatList,
                chatId = chatId,
                isPinned = true
            )
        }

        emitDialogs()
        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.deleteDialogImpl(providerDialogId: String, revoke: Boolean): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val chat = when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> null
        }

        val result = when (chat?.type) {
            // Groups and channels are left instead of wiped: deleting the
            // history of a shared chat would only clear it for this user.
            is ChatTypeBasicGroup, is ChatTypeSupergroup -> client.leaveChat(chatId)
            else -> client.deleteChatHistory(
                chatId = chatId,
                removeFromChatList = true,
                revoke = revoke
            )
        }

        when (result) {
            is TdlResult.Success -> {
                _chats.update { entries -> entries - chatId }
                histories.update { entries -> entries - providerDialogId }
                CacheStore.deleteMessages(accountId, providerDialogId)
                emitDialogs()
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

internal suspend fun TelegramSession.setDialogPinnedImpl(providerDialogId: String, isPinned: Boolean, folderId: Int?): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val chatList = folderId?.let { folder -> ChatListFolder(folder) } ?: ChatListMain()

    return try {
        when (val result = client.toggleChatIsPinned(
            chatList = chatList,
            chatId = chatId,
            isPinned = isPinned
        )) {
            is TdlResult.Success -> {
                // Re-read the chat so the pinned state comes back from the server.
                when (val chat = client.getChat(chatId)) {
                    is TdlResult.Success -> upsertChat(chat.result)
                    is TdlResult.Failure -> Unit
                }

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

internal suspend fun TelegramSession.setChatSubscribedImpl(providerDialogId: String, subscribed: Boolean): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val result = if (subscribed) client.joinChat(chatId) else client.leaveChat(chatId)

        when (result) {
            is TdlResult.Success -> {
                // Membership lives on the chat, so refresh it for the lists.
                refreshChat(chatId)
                Result.success(Unit)
            }

            is TdlResult.Failure -> {
                // A pending join request still means the user subscribed.
                if (subscribed && result.message.contains("INVITE_REQUEST_SENT")) {
                    refreshChat(chatId)
                    return Result.success(Unit)
                }

                // The join may fail because the user is already there (or
                // already gone); trust the actual membership in that case.
                val isMember = resolveCurrentUserId()?.let { meId ->
                    when (val member = client.getChatMember(
                        chatId = chatId,
                        memberId = MessageSenderUser(meId)
                    )) {
                        is TdlResult.Success -> member.result.status.isSubscribed()
                        is TdlResult.Failure -> false
                    }
                } ?: false

                if (isMember == subscribed) {
                    refreshChat(chatId)
                    Result.success(Unit)
                } else {
                    Result.failure(result.toSessionException())
                }
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadOwnProfileImpl(): Result<UserProfileDetails> {
    return try {
        val user = when (val result = client.getMe()) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val fullInfo = when (val result = client.getUserFullInfo(user.id)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> null
        }

        Result.success(
            UserProfileDetails(
                userId = user.id,
                displayName = userDisplayName(user),
                avatar = userAvatar(user),
                username = user.usernames?.activeUsernames?.firstOrNull(),
                phoneNumber = user.phoneNumber.ifBlank { null },
                bio = fullInfo?.bio?.text?.ifBlank { null },
                birthdate = fullInfo?.birthdate?.let(::formatBirthdate),
                status = userStatusText(user),
                isOnline = (user.status as? UserStatusOnline) != null,
                isVerified = user.verificationStatus?.isVerified == true,
                isPremium = user.isPremium,
                emojiStatusId = emojiStatusId(user.emojiStatus),
                accentColorsLight = user.upgradedGiftColors
                    ?.lightThemeColors
                    ?.map { color -> color.toLong() }
                    .orEmpty(),
                accentColorsDark = user.upgradedGiftColors
                    ?.darkThemeColors
                    ?.map { color -> color.toLong() }
                    .orEmpty(),
                personalChannel = fullInfo?.personalChatId
                    ?.takeIf { chatId -> chatId != 0L }
                    ?.let { chatId -> loadPersonalChannel(chatId) }
            )
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadUserProfileImpl(providerDialogId: String, userId: Long?): Result<UserProfileDetails> {
    return try {
        val resolvedUserId = userId ?: run {
            val chatId = providerDialogId.toLongOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

            val chat = when (val result = client.getChat(chatId)) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> return Result.failure(result.toSessionException())
            }

            (chat.type as? ChatTypePrivate)?.userId ?: return Result.success(
                when (val type = chat.type) {
                    is ChatTypeSupergroup -> supergroupProfile(chat, type)
                    is ChatTypeBasicGroup -> basicGroupProfile(chat, type)
                    else -> UserProfileDetails()
                }
            )
        }

        val user = when (val result = client.getUser(resolvedUserId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val fullInfo = when (val result = client.getUserFullInfo(resolvedUserId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> null
        }

        val personalChatId = fullInfo?.personalChatId ?: 0L
        val currentStatus = _userStatuses.value[resolvedUserId] ?: user.status

        Result.success(
            UserProfileDetails(
                userId = resolvedUserId,
                displayName = userDisplayName(user),
                avatar = userAvatar(user),
                username = user.usernames?.activeUsernames?.firstOrNull(),
                phoneNumber = user.phoneNumber.ifBlank { null },
                bio = fullInfo?.bio?.text?.ifBlank { null },
                birthdate = fullInfo?.birthdate?.let(::formatBirthdate),
                status = userStatusText(user),
                isOnline = currentStatus is UserStatusOnline,
                isVerified = user.verificationStatus?.isVerified == true,
                isPremium = user.isPremium,
                emojiStatusId = emojiStatusId(user.emojiStatus),
                accentColorsLight = user.upgradedGiftColors
                    ?.lightThemeColors
                    ?.map { color -> color.toLong() }
                    .orEmpty(),
                accentColorsDark = user.upgradedGiftColors
                    ?.darkThemeColors
                    ?.map { color -> color.toLong() }
                    .orEmpty(),
                personalChannel = if (personalChatId != 0L) {
                    loadPersonalChannel(personalChatId)
                } else {
                    null
                },
                commonGroups = loadCommonGroups(resolvedUserId),
                tracks = loadProfileTracks(resolvedUserId),
                isBlocked = fullInfo?.blockList != null,
                // Telegram support/service accounts cannot be blocked.
                canBlock = !user.isSupport && resolvedUserId != currentUserId,
                isContact = user.isContact,
                canManageContacts = true
            )
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadProfilePhotosImpl(userId: Long): Result<List<MessageMedia>> {
    return try {
        val photos = when (val result = client.getUserProfilePhotos(
            userId = userId,
            offset = 0,
            limit = 100
        )) {
            is TdlResult.Success -> result.result.photos
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        Result.success(
            photos.map { photo ->
                // ChatPhoto exposes sized variants instead of the small/big
                // pair of ProfilePhoto, so both the thumbnail and the
                // original are picked from the size list.
                val sizes = photo.sizes.sortedBy { size -> size.photo.size }
                val preview = sizes.firstOrNull()
                val best = sizes.lastOrNull()

                MessageMedia(
                    kind = MessageMediaKind.Photo,
                    previewFileId = preview?.photo?.id,
                    previewUniqueId = preview?.photo?.remote?.uniqueId,
                    fileId = best?.photo?.id,
                    fileUniqueId = best?.photo?.remote?.uniqueId,
                    width = best?.width ?: 0,
                    height = best?.height ?: 0,
                    fileName = "Profile photo",
                    mimeType = "image/jpeg",
                    size = best?.photo?.size ?: 0L,
                    minithumbnail = photo.minithumbnail?.data
                )
            }
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadSharedMediaImpl(providerDialogId: String, kind: SharedMediaKind, fromMessageId: Long, limit: Int): Result<List<ChatMessage>> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val filter = when (kind) {
        SharedMediaKind.Photo -> SearchMessagesFilterPhoto()
        SharedMediaKind.Video -> SearchMessagesFilterVideo()
        SharedMediaKind.Animation -> SearchMessagesFilterAnimation()
        SharedMediaKind.Voice -> SearchMessagesFilterVoiceNote()
        SharedMediaKind.Link -> SearchMessagesFilterUrl()
        SharedMediaKind.File -> SearchMessagesFilterDocument()
    }

    return try {
        when (val result = client.searchChatMessages(
            chatId = chatId,
            query = "",
            fromMessageId = fromMessageId,
            offset = 0,
            limit = limit.coerceIn(1, 100),
            filter = filter
        )) {
            is TdlResult.Success -> Result.success(
                result.result.messages
                    .map(::mapMessage)
                    .sortedByDescending { message -> message.messageId }
            )

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadCustomEmojiImpl(emojiId: Long): CustomEmojiAsset? {
    return try {
        val sticker = customEmojiCacheMutex.withLock {
            customEmojiCache[emojiId]
        } ?: run {
            when (val response = client.getCustomEmojiStickers(longArrayOf(emojiId))) {
                is TdlResult.Success -> {
                    val found = response.result.stickers.firstOrNull() ?: return null

                    customEmojiCacheMutex.withLock {
                        customEmojiCache[emojiId] = found
                    }

                    found
                }

                is TdlResult.Failure -> return null
            }
        }

        // Fall back to the thumbnail when the full sticker file is not
        // available yet, so a custom emoji never degrades to plain text.
        val data = loadFile(sticker.sticker.id).getOrNull()
            ?: sticker.thumbnail?.file?.id?.let { fileId ->
                loadFile(fileId).getOrNull()
            }
            ?: return null

        CustomEmojiAsset(
            data = data,
            needsRepainting = sticker.needsRepainting()
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        null
    }
}

internal suspend fun TelegramSession.loadCustomEmojiSetIdsImpl(emojiIds: List<Long>): Map<Long, Long> {
    val distinct = emojiIds.distinct()
    if (distinct.isEmpty()) return emptyMap()

    return try {
        loadCustomEmojiStickers(distinct)
            .mapNotNull { (emojiId, sticker) ->
                sticker.setId
                    .takeIf { setId -> setId != 0L }
                    ?.let { setId -> emojiId to setId }
            }
            .toMap()
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        emptyMap()
    }
}

internal suspend fun TelegramSession.loadMessageReadDateImpl(providerDialogId: String, messageId: Long): Result<Long?> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getMessageReadDate(
            chatId = chatId,
            messageId = messageId
        )) {
            is TdlResult.Success -> Result.success(
                (result.result as? MessageReadDateRead)
                    ?.readDate
                    ?.toLong()
                    ?.times(1000L)
            )

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.openWebAppImpl(providerDialogId: String, button: MessageButton): Result<WebAppLaunch> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))
    val url = button.webAppUrl
        ?: return Result.failure(IllegalArgumentException("Not a web app button"))
    val botUserId = button.botUserId
        ?: return Result.failure(IllegalStateException("The bot behind the button is unknown"))

    return try {
        when (val result = client.openWebApp(
            chatId = chatId,
            botUserId = botUserId,
            url = url,
            topicId = null,
            replyTo = null,
            parameters = webAppOpenParameters()
        )) {
            is TdlResult.Success -> Result.success(
                WebAppLaunch(
                    id = result.result.launchId,
                    url = result.result.url.url,
                    requireSameOrigin = result.result.url.requireSameOrigin
                )
            )

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.openMainWebAppImpl(providerDialogId: String): Result<WebAppLaunch> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val chat = when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val botUserId = (chat.type as? ChatTypePrivate)?.userId
            ?: return Result.failure(IllegalStateException("The chat is not a bot"))

        val parameters = webAppOpenParameters()

        // An empty URL asks TDLib for the bot's main mini app. When that
        // is unavailable, the resolved main app URL can still be rendered.
        when (val result = client.openWebApp(
            chatId = chatId,
            botUserId = botUserId,
            url = "",
            topicId = null,
            replyTo = null,
            parameters = parameters
        )) {
            is TdlResult.Success -> Result.success(
                WebAppLaunch(
                    id = result.result.launchId,
                    url = result.result.url.url,
                    requireSameOrigin = result.result.url.requireSameOrigin
                )
            )

            is TdlResult.Failure -> when (val fallback = client.getMainWebApp(
                chatId = chatId,
                botUserId = botUserId,
                startParameter = "",
                parameters = parameters
            )) {
                is TdlResult.Success -> Result.success(
                    WebAppLaunch(
                        id = 0,
                        url = fallback.result.url.url,
                        requireSameOrigin = fallback.result.url.requireSameOrigin
                    )
                )

                is TdlResult.Failure -> Result.failure(result.toSessionException())
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.closeWebAppImpl(launchId: Long) {
    if (launchId <= 0) return
    runCatching { client.closeWebApp(launchId) }
}
