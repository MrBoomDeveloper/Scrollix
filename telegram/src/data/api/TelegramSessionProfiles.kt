package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.CommonGroup
import com.mrboomdev.scrollix.data.model.ChatMemberInfo
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.PersonalChannel
import com.mrboomdev.scrollix.data.model.ProfileTrack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.model.StickerSetInfo
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.provider.MessageHistoryController
import com.mrboomdev.scrollix.data.provider.MessagePage
import com.mrboomdev.scrollix.data.provider.MessagePageLoader
import com.mrboomdev.scrollix.data.provider.TelegramLinkTarget
import com.mrboomdev.scrollix.data.storage.CacheStore
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal fun TelegramSession.historyFor(providerDialogId: String): MessageHistoryController? {
    val chatId = providerDialogId.toLongOrNull() ?: return null

    histories.value[providerDialogId]?.let { return it }

    val created = MessageHistoryController(
        pageLoader = object : MessagePageLoader {
            override suspend fun load(
                fromMessageId: Long,
                limit: Int
            ): Result<MessagePage> {
                return load(fromMessageId, limit, offset = 0)
            }

            override suspend fun load(
                fromMessageId: Long,
                limit: Int,
                offset: Int
            ): Result<MessagePage> {
                return try {
                    Result.success(
                        fetchChatHistory(
                            chatId = chatId,
                            fromMessageId = fromMessageId,
                            offset = offset,
                            limit = limit
                        )
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Result.failure(exception)
                }
            }
        }
    )

    // Only the controller that actually won the race may seed and replay;
    // a second historyFor() call creates a throwaway controller otherwise.
    histories.update { existing ->
        if (existing.containsKey(providerDialogId)) existing
        else existing + (providerDialogId to created)
    }

    val winner = histories.value[providerDialogId] ?: return null
    if (winner !== created) return winner

    // Cached messages fill the window immediately while the real page loads.
    scope.launch {
        val cached = CacheStore.loadMessages(accountId, providerDialogId)
        if (cached.isNotEmpty() && !closed) created.seed(cached)

        val pending = pendingLiveMessages.remove(providerDialogId)
        if (!pending.isNullOrEmpty() && !closed) {
            created.merge(pending.map(::mapMessage))
        }
    }

    return winner
}

internal suspend fun TelegramSession.fetchChatHistory(
    chatId: Long,
    fromMessageId: Long,
    limit: Int,
    offset: Int = 0
): MessagePage {
    return when (val result = client.getChatHistory(
        chatId = chatId,
        fromMessageId = fromMessageId,
        offset = offset,
        limit = limit.coerceIn(1, 100),
        onlyLocal = false
    )) {
        is TdlResult.Success -> MessagePage(
            messages = result.result.messages.filterNotNull().map(::mapMessage),
            totalCount = result.result.totalCount
        )

        is TdlResult.Failure -> throw result.toSessionException()
    }
}

/**
 * TDLib's remote unique id is a durable content identifier, while the
 * numeric file id is only valid for the current run and can point at
 * another file after a restart. Cached media is stored under the former.
 */
internal suspend fun TelegramSession.cacheKeyFor(fileId: Int): String? {
    return try {
        when (val result = client.getFile(fileId)) {
            is TdlResult.Success -> result.result.remote.uniqueId.takeIf { it.isNotBlank() }
            is TdlResult.Failure -> null
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        null
    }
}

internal suspend fun TelegramSession.supergroupProfile(
    chat: Chat,
    type: ChatTypeSupergroup
): UserProfileDetails {
    val fullInfo = when (val result = client.getSupergroupFullInfo(type.supergroupId)) {
        is TdlResult.Success -> result.result
        is TdlResult.Failure -> null
    }

    val isSubscribed = resolveCurrentUserId()?.let { meId ->
        when (val result = client.getChatMember(
            chatId = chat.id,
            memberId = MessageSenderUser(meId)
        )) {
            is TdlResult.Success -> result.result.status.isSubscribed()
            is TdlResult.Failure -> null
        }
    }

    return UserProfileDetails(
        displayName = chat.title,
        avatar = chatAvatar(chat),
        kind = if (type.isChannel) DialogKind.Channel else DialogKind.Group,
        memberCount = fullInfo?.memberCount?.takeIf { it > 0 },
        description = fullInfo?.description?.ifBlank { null },
        isVerified = type.supergroupId in _verifiedSupergroups.value,
        emojiStatusId = emojiStatusId(chat.emojiStatus),
        members = loadChatMembers(chat.id),
        isSubscribed = isSubscribed,
        discussionChatId = fullInfo?.linkedChatId?.takeIf { it != 0L }?.toString()
    )
}

internal suspend fun TelegramSession.basicGroupProfile(
    chat: Chat,
    type: ChatTypeBasicGroup
): UserProfileDetails {
    val fullInfo = when (val result = client.getBasicGroupFullInfo(type.basicGroupId)) {
        is TdlResult.Success -> result.result
        is TdlResult.Failure -> null
    }

    return UserProfileDetails(
        displayName = chat.title,
        avatar = chatAvatar(chat),
        kind = DialogKind.Group,
        memberCount = fullInfo?.members?.size?.takeIf { it > 0 },
        description = fullInfo?.description?.ifBlank { null },
        emojiStatusId = emojiStatusId(chat.emojiStatus),
        members = loadChatMembers(chat.id)
    )
}

internal suspend fun TelegramSession.loadChatMembers(
    chatId: Long,
    limit: Int = 20
): List<ChatMemberInfo> {
    val members = when (val result = client.searchChatMembers(
        chatId,
        "",
        limit,
        ChatMembersFilterMembers()
    )) {
        is TdlResult.Success -> result.result.members
        is TdlResult.Failure -> return emptyList()
    }

    return members.mapNotNull { member ->
        val userId = (member.memberId as? MessageSenderUser)?.userId
            ?: return@mapNotNull null

        val user = when (val result = client.getUser(userId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return@mapNotNull null
        }

        ChatMemberInfo(
            userId = userId,
            name = userDisplayName(user),
            username = user.usernames?.activeUsernames?.firstOrNull(),
            avatar = userAvatar(user),
            emojiStatusId = emojiStatusId(user.emojiStatus)
        )
    }
}

internal fun TelegramSession.chatAvatar(chat: Chat): Avatar {
    return chatAvatar(chat.title, chat.photo)
}

internal fun TelegramSession.chatAvatar(title: String, photo: ChatPhotoInfo?): Avatar {
    val image = photo?.small?.let(::avatarBytes)
        ?: photo?.minithumbnail?.data
    val fallback = Avatar.fromName(title)

    return image?.let { bytes ->
        Avatar(
            initials = fallback.initials,
            colorSeed = fallback.colorSeed,
            image = bytes
        )
    } ?: fallback
}

internal suspend fun TelegramSession.loadProfileTracks(
    userId: Long,
    limit: Int = 20
): List<ProfileTrack> {
    val audios = when (val result = client.getUserProfileAudios(
        userId = userId,
        offset = 0,
        limit = limit
    )) {
        is TdlResult.Success -> result.result.audios
        is TdlResult.Failure -> return emptyList()
    }

    return audios.map(::mapProfileTrack)
}

internal fun TelegramSession.mapProfileTrack(audio: Audio): ProfileTrack {
    val media = mapAudio(audio)

    return ProfileTrack(
        title = audio.title.ifBlank {
            media.fileName?.takeIf { it.isNotBlank() } ?: "Audio"
        },
        performer = audio.performer.takeIf { it.isNotBlank() },
        durationSeconds = audio.duration,
        media = media
    )
}

internal suspend fun TelegramSession.loadCommonGroups(userId: Long, limit: Int = 10): List<CommonGroup> {
    val chatIds = when (val result = client.getGroupsInCommon(
        userId = userId,
        offsetChatId = 0L,
        limit = limit
    )) {
        is TdlResult.Success -> result.result.chatIds
        is TdlResult.Failure -> return emptyList()
    }

    val groups = mutableListOf<CommonGroup>()

    for (chatId in chatIds) {
        when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> groups += CommonGroup(
                providerDialogId = result.result.id.toString(),
                title = result.result.title,
                avatar = chatAvatar(result.result)
            )

            is TdlResult.Failure -> Unit
        }
    }

    return groups
}

internal suspend fun TelegramSession.loadPersonalChannel(chatId: Long): PersonalChannel? {
    val chat = when (val result = client.getChat(chatId)) {
        is TdlResult.Success -> result.result
        is TdlResult.Failure -> return null
    }
    val type = chat.type as? ChatTypeSupergroup ?: return null

    if (!type.isChannel) return null

    val fullInfo = when (val result = client.getSupergroupFullInfo(type.supergroupId)) {
        is TdlResult.Success -> result.result
        is TdlResult.Failure -> null
    }

    return PersonalChannel(
        providerDialogId = chat.id.toString(),
        title = chat.title,
        avatar = chatAvatar(chat),
        subscriberCount = fullInfo?.memberCount?.takeIf { it > 0 }
    )
}

internal fun TelegramSession.formatBirthdate(birthdate: Birthdate): String {
    val months = listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    )

    val day = birthdate.day.takeIf { it > 0 }
    val month = months.getOrNull(birthdate.month - 1)
    val year = birthdate.year.takeIf { it > 0 }

    return listOfNotNull(day?.toString(), month, year?.toString())
        .joinToString(" ")
}

/** Re-reads a chat and pushes it into the dialog list. */
internal suspend fun TelegramSession.refreshChat(chatId: Long) {
    when (val chat = client.getChat(chatId)) {
        is TdlResult.Success -> upsertChat(chat.result)
        is TdlResult.Failure -> Unit
    }
}

/** Re-reads a single message and pushes it into the open history window. */
internal suspend fun TelegramSession.refreshMessage(chatId: Long, messageId: Long) {
    val message = when (val result = client.getMessage(
        chatId = chatId,
        messageId = messageId
    )) {
        is TdlResult.Success -> mapMessage(result.result)
        is TdlResult.Failure -> return
    }

    histories.value[chatId.toString()]?.mutate { cached ->
        if (cached.messageId == messageId) message else cached
    }
}

internal fun PublicPostSearchLimits.toQuota(): PostSearchQuota {
    return PostSearchQuota(
        dailyFreeQueryCount = dailyFreeQueryCount,
        remainingFreeQueryCount = remainingFreeQueryCount,
        nextFreeQueryIn = nextFreeQueryIn,
        starCount = starCount,
        isCurrentQueryFree = isCurrentQueryFree
    )
}

internal fun NotificationScope.toTdlibScope(): NotificationSettingsScope {
    return when (this) {
        NotificationScope.PrivateChats -> NotificationSettingsScopePrivateChats()
        NotificationScope.Groups -> NotificationSettingsScopeGroupChats()
        NotificationScope.Channels -> NotificationSettingsScopeChannelChats()
    }
}

internal fun TelegramSession.reactionType(emoji: String, customEmojiId: Long?): ReactionType {
    return if (customEmojiId != null) {
        ReactionTypeCustomEmoji(customEmojiId = customEmojiId)
    } else {
        ReactionTypeEmoji(emoji = emoji)
    }
}

internal fun AvailableReaction.toOption(): MessageReactionOption? {
    return when (val type = type) {
        is ReactionTypeEmoji -> MessageReactionOption(emoji = type.emoji)
        is ReactionTypeCustomEmoji -> MessageReactionOption(
            emoji = "",
            customEmojiId = type.customEmojiId
        )

        else -> null
    }
}

internal fun <T> TelegramSession.notSignedIn(): Result<T> =
    Result.failure(IllegalStateException("Sign in to Telegram first"))

internal suspend fun TelegramSession.resolveLegacyTarget(link: String): Result<TelegramLinkTarget> {
    if (!link.contains("t.me/") && !link.contains("telegram.me/")) {
        return Result.failure(IllegalArgumentException("Unsupported Telegram link"))
    }

    val path = link
        .substringAfter("t.me/", link.substringAfter("telegram.me/"))
        .substringBefore("?")
        .substringBefore("#")
        .trim('/')

    if (path.isBlank()) return Result.failure(IllegalArgumentException("Empty link"))
    if (path.startsWith("+") || path.startsWith("joinchat/")) return resolveInviteTarget(link)

    if (path.startsWith("addstickers/") || path.startsWith("addemoji/")) {
        val customEmoji = path.startsWith("addemoji/")
        val name = path.substringAfter('/').substringBefore('/')

        if (name.isNotBlank()) {
            return Result.success(
                TelegramLinkTarget.StickerSet(
                    name = name,
                    kind = if (customEmoji) {
                        StickerPackKind.CustomEmoji
                    } else {
                        StickerPackKind.Sticker
                    },
                    expectCustomEmoji = customEmoji
                )
            )
        }
    }

    val parsed = parseTelegramMessageLink(link)

    // Private message links have no username at all; their numeric id is
    // the only way to address the chat, so they cannot go through the
    // public-chat search below.
    if (parsed?.providerDialogId != null) {
        return Result.success(
            TelegramLinkTarget.Dialog(
                providerDialogId = parsed.providerDialogId,
                messageId = parsed.messageId
            )
        )
    }

    val username = parsed?.username
        ?: path.split('/').firstOrNull().orEmpty()

    return publicChatTarget(username, parsed?.messageId)
}

internal suspend fun TelegramSession.publicChatTarget(
    username: String,
    messageId: Long? = null
): Result<TelegramLinkTarget> {
    if (username.isBlank()) {
        return Result.failure(IllegalArgumentException("Empty link"))
    }

    return when (val response = client.searchPublicChat(username)) {
        is TdlResult.Success -> Result.success(
            TelegramLinkTarget.Dialog(
                providerDialogId = response.result.id.toString(),
                messageId = messageId
            )
        )

        is TdlResult.Failure -> Result.failure(
            IllegalStateException("@$username was not found")
        )
    }
}

internal suspend fun TelegramSession.resolveInviteTarget(link: String): Result<TelegramLinkTarget> {
    val info = when (val checked = client.checkChatInviteLink(link)) {
        is TdlResult.Success -> checked.result
        is TdlResult.Failure -> return Result.failure(
            IllegalStateException("Invalid invite link")
        )
    }

    if (info.chatId != 0L) {
        return Result.success(TelegramLinkTarget.Dialog(info.chatId.toString()))
    }

    return when (val joined = client.joinChatByInviteLink(link)) {
        is TdlResult.Success -> {
            when (val result = joined.result) {
                is ChatJoinResultSuccess -> Result.success(
                    TelegramLinkTarget.Dialog(result.chatId.toString())
                )

                else -> Result.failure(
                    IllegalStateException("Could not join the chat")
                )
            }
        }

        is TdlResult.Failure -> Result.failure(
            IllegalStateException("Could not join the chat")
        )
    }
}

internal fun TelegramSession.webAppOpenParameters(): WebAppOpenParameters {
    return WebAppOpenParameters(
        theme = ThemeParameters(
            backgroundColor = 0xFFFFFFFF.toInt(),
            secondaryBackgroundColor = 0xFFF1F1F1.toInt(),
            headerBackgroundColor = 0xFFFFFFFF.toInt(),
            bottomBarBackgroundColor = 0xFFFFFFFF.toInt(),
            sectionBackgroundColor = 0xFFFFFFFF.toInt(),
            sectionSeparatorColor = 0xFFE3E3E3.toInt(),
            textColor = 0xFF1A1A1A.toInt(),
            accentTextColor = 0xFF2481CC.toInt(),
            sectionHeaderTextColor = 0xFF2481CC.toInt(),
            subtitleTextColor = 0xFF999999.toInt(),
            destructiveTextColor = 0xFFD14E4E.toInt(),
            hintColor = 0xFF999999.toInt(),
            linkColor = 0xFF2481CC.toInt(),
            buttonColor = 0xFF50A8EB.toInt(),
            buttonTextColor = 0xFFFFFFFF.toInt()
        ),
        applicationName = "Scrollix",
        mode = WebAppOpenModeFullSize()
    )
}

internal suspend fun TelegramSession.mapStickerSet(
    set: StickerSet,
    kind: StickerPackKind? = null
): StickerSetInfo {
    // Remember the full sticker objects: a custom emoji picked from an
    // installed pack must not depend on a second getCustomEmojiStickers
    // round trip to start rendering.
    customEmojiCacheMutex.withLock {
        set.stickers.forEach { sticker ->
            customEmojiCache[sticker.id] = sticker
        }
    }

    val resolvedKind = kind ?: if (set.stickers.any { sticker ->
            sticker.fullType is StickerFullTypeCustomEmoji
        }
    ) {
        StickerPackKind.CustomEmoji
    } else {
        StickerPackKind.Sticker
    }

    return StickerSetInfo(
        id = set.id,
        title = set.title,
        name = set.name,
        isInstalled = set.isInstalled,
        stickers = set.stickers.map { sticker ->
            MessageMedia(
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
        },
        kind = resolvedKind
    )
}

internal suspend fun TelegramSession.sendContent(
    chatId: Long,
    content: InputMessageContent
): Result<Unit> {
    return try {
        when (val result = client.sendMessage(
            chatId = chatId,
            inputMessageContent = content
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
