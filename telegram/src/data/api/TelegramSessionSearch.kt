package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.PostSearchPage
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.model.PublicChatSearchKind
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.provider.TelegramLinkTarget
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock

internal suspend fun TelegramSession.searchMessagesImpl(providerDialogId: String, query: String, limit: Int): Result<List<ChatMessage>> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val trimmed = query.trim()
    if (trimmed.isEmpty()) return Result.success(emptyList())

    return try {
        when (val result = client.searchChatMessages(
            chatId = chatId,
            query = trimmed,
            fromMessageId = 0,
            offset = 0,
            limit = limit.coerceIn(1, 100),
            filter = SearchMessagesFilterEmpty()
        )) {
            is TdlResult.Success -> Result.success(
                result.result.messages
                    .map(::mapMessage)
                    .sortedByDescending { it.messageId }
            )

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.searchGlobalMessagesImpl(query: String, limit: Int): Result<List<ChatMessage>> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return Result.success(emptyList())

    return try {
        val pageSize = limit.coerceIn(1, 100)
        val found = mutableListOf<ChatMessage>()
        var offset = ""

        // TDLib pages global results; ask for more until the limit is met.
        while (found.size < pageSize) {
            val page = when (val result = client.searchMessages(
                // null searches every chat, archived ones included.
                chatList = null,
                query = trimmed,
                offset = offset,
                limit = pageSize - found.size,
                filter = SearchMessagesFilterEmpty(),
                minDate = 0,
                maxDate = 0
            )) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> return Result.failure(
                    result.toSessionException()
                )
            }

            if (page.messages.isEmpty()) break

            found += page.messages
                .filterNotNull()
                .map(::mapMessage)

            val nextOffset = page.nextOffset
            if (nextOffset.isEmpty() || nextOffset == offset) break
            offset = nextOffset
        }

        // Chats that were never opened still need to be known, so the
        // search results can show their titles and avatars.
        found.asSequence()
            .mapNotNull { message -> message.providerDialogId.toLongOrNull() }
            .distinct()
            .filterNot { chatId -> _chats.value.containsKey(chatId) }
            .forEach { chatId ->
                when (val chat = client.getChat(chatId)) {
                    is TdlResult.Success -> upsertChat(chat.result)
                    is TdlResult.Failure -> Unit
                }
            }

        Result.success(found)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.searchChatsImpl(query: String, limit: Int): Result<List<Dialog>> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return Result.success(emptyList())

    return try {
        val chatIds = when (val result = client.searchChatsOnServer(
            query = trimmed,
            limit = limit.coerceIn(1, 100)
        )) {
            is TdlResult.Success -> result.result.chatIds.toList()
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        // Fetch the chats so they can be shown with their real metadata.
        chatIds.forEach { chatId ->
            when (val chat = client.getChat(chatId)) {
                is TdlResult.Success -> upsertChat(chat.result)
                is TdlResult.Failure -> Unit
            }
        }

        if (chatIds.isNotEmpty()) {
            emitDialogs()
        }

        val dialogs = chatIds.mapNotNull { chatId ->
            _dialogs.value.firstOrNull { dialog ->
                dialog.providerDialogId == chatId.toString()
            }
        }

        Result.success(dialogs)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.searchPublicChatsImpl(query: String, limit: Int, kind: PublicChatSearchKind): Result<List<Dialog>> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return Result.success(emptyList())

    return try {
        val filter = when (kind) {
            PublicChatSearchKind.Any -> null
            PublicChatSearchKind.Channels -> SearchChatTypeFilterChannel()
            PublicChatSearchKind.Bots -> SearchChatTypeFilterBot()
        }

        val chatIds = when (val result = client.searchPublicChats(
            query = trimmed,
            typeFilter = filter
        )) {
            is TdlResult.Success -> result.result.chatIds
                .take(limit.coerceIn(1, 100))
                .toList()

            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val chats = chatIds.mapNotNull { chatId ->
            when (val result = client.getChat(chatId)) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> null
            }
        }

        chats.forEach(::upsertChat)

        // Bots are private chats, so the dialog kind can only be resolved
        // as a bot once TDLib has loaded the user behind the chat.
        if (kind == PublicChatSearchKind.Bots) {
            chats.mapNotNull { chat -> (chat.type as? ChatTypePrivate)?.userId }
                .distinct()
                .filterNot { userId -> _users.value.containsKey(userId) }
                .forEach { userId ->
                    when (val result = client.getUser(userId)) {
                        is TdlResult.Success -> _users.update {
                            it + (userId to result.result)
                        }

                        is TdlResult.Failure -> Unit
                    }
                }
        }

        Result.success(
            chats.mapNotNull { chat ->
                _chats.value[chat.id]?.let { entry -> toDialog(entry, null) }
            }
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.searchPublicPostsImpl(query: String, offset: String, limit: Int, starCount: Long): Result<PostSearchPage> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return Result.success(PostSearchPage())

    return try {
        val page = when (val result = client.searchPublicPosts(
            query = trimmed,
            offset = offset,
            limit = limit.coerceIn(1, 100),
            starCount = starCount
        )) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val messages = page.messages.map(::mapMessage)
        val chatIds = messages.map { message -> message.providerDialogId.toLong() }.distinct()

        chatIds.forEach { chatId ->
            when (val chat = client.getChat(chatId)) {
                is TdlResult.Success -> upsertChat(chat.result)
                is TdlResult.Failure -> Unit
            }
        }

        Result.success(
            PostSearchPage(
                messages = messages,
                dialogs = chatIds.mapNotNull { chatId ->
                    _chats.value[chatId]?.let { entry -> toDialog(entry, null) }
                },
                nextOffset = page.nextOffset,
                quota = page.searchLimits?.toQuota(),
                areLimitsExceeded = page.areLimitsExceeded
            )
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadPublicPostSearchQuotaImpl(query: String): Result<PostSearchQuota> {
    return try {
        when (val result = client.getPublicPostSearchLimits(query.trim())) {
            is TdlResult.Success -> Result.success(result.result.toQuota())
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.resolveUsernameImpl(username: String): Result<Long> {
    val name = username.removePrefix("@")
    if (name.isBlank()) return Result.failure(IllegalArgumentException("Invalid username"))

    return try {
        when (val response = client.searchPublicChat(name)) {
            is TdlResult.Success -> {
                val userId = (response.result.type as? ChatTypePrivate)?.userId
                    ?: return Result.failure(IllegalStateException("Not a user"))

                Result.success(userId)
            }

            is TdlResult.Failure -> Result.failure(
                IllegalStateException("User @$name was not found")
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.resolveTelegramLinkImpl(url: String): Result<String> {
    return resolveTelegramLinkTarget(url).mapCatching { target ->
        when (target) {
            is TelegramLinkTarget.Dialog -> target.providerDialogId
            is TelegramLinkTarget.StickerSet -> throw IllegalArgumentException(
                "Sticker set links do not point at a chat"
            )
        }
    }
}

internal suspend fun TelegramSession.resolveTelegramLinkTargetImpl(url: String): Result<TelegramLinkTarget> {
    val link = url.trim()
    val lower = link.lowercase()

    if (!lower.startsWith("tg://") &&
        !lower.contains("t.me/") &&
        !lower.contains("telegram.me/")
    ) {
        return Result.failure(IllegalArgumentException("Not a Telegram link"))
    }

    // Explicit fallbacks for the pack links this app is expected to
    // handle, in case TDLib cannot classify the link.
    if (lower.startsWith("tg://addstickers") || lower.startsWith("tg://addemoji")) {
        val name = link.substringAfter("set=", "")
            .substringBefore('&')
            .substringBefore('#')

        if (name.isNotBlank()) {
            val customEmoji = lower.startsWith("tg://addemoji")

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

    return try {
        // TDLib understands the addstickers/addemoji names, tg:// forms,
        // invite links and message links, so it classifies the link
        // instead of the app guessing from path prefixes.
        when (val classified = client.getInternalLinkType(link)) {
            is TdlResult.Success -> when (val type = classified.result) {
                is InternalLinkTypeStickerSet -> Result.success(
                    TelegramLinkTarget.StickerSet(
                        name = type.stickerSetName,
                        kind = if (type.expectCustomEmoji) {
                            StickerPackKind.CustomEmoji
                        } else {
                            StickerPackKind.Sticker
                        },
                        expectCustomEmoji = type.expectCustomEmoji
                    )
                )

                is InternalLinkTypePublicChat -> publicChatTarget(
                    username = type.chatUsername,
                    messageId = parseTelegramMessageLink(link)?.messageId
                )
                is InternalLinkTypeChatInvite -> resolveInviteTarget(link)
                else -> resolveLegacyTarget(link)
            }

            is TdlResult.Failure -> resolveLegacyTarget(link)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadMentionAvatarsImpl(text: String): Map<String, Avatar> {
    if (!text.contains('@')) return emptyMap()

    val usernames = MentionUsernameRegex.findAll(text)
        .map { match -> match.groupValues[1] }
        .distinct()
        .take(10)
        .toList()

    if (usernames.isEmpty()) return emptyMap()

    val avatars = linkedMapOf<String, Avatar>()
    // A snapshot is enough because usernames are deduplicated above; the
    // mutex only protects the shared map from concurrent mention renders.
    val cachedMentions = mentionAvatarMutex.withLock { mentionAvatarCache.toMap() }

    for (username in usernames) {
        if (cachedMentions.containsKey(username)) {
            cachedMentions[username]?.let { avatar -> avatars[username] = avatar }
            continue
        }

        try {
            val userId = when (val result = client.searchPublicChat(username)) {
                is TdlResult.Success -> (result.result.type as? ChatTypePrivate)?.userId
                is TdlResult.Failure -> null
            } ?: continue

            val user = when (val result = client.getUser(userId)) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> continue
            }

            val avatar = userAvatar(user)
            mentionAvatarMutex.withLock { mentionAvatarCache[username] = avatar }
            avatars[username] = avatar
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // A mention that can't be resolved simply renders without an avatar.
            mentionAvatarMutex.withLock { mentionAvatarCache[username] = null }
        }
    }

    return avatars
}
