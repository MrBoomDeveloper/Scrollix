package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import dev.g000sha256.tdl.dto.BotCommand
import dev.g000sha256.tdl.dto.BotCommandScopeChat
import dev.g000sha256.tdl.dto.BotCommandScopeDefault
import dev.g000sha256.tdl.dto.CallbackQueryPayloadData
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.UserTypeBot
import com.mrboomdev.scrollix.data.model.BotCallbackAnswer
import com.mrboomdev.scrollix.data.model.BotCommandInfo
import com.mrboomdev.scrollix.data.model.InlineBotPage
import com.mrboomdev.scrollix.data.model.InlineBotResult
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.provider.TelegramLinkTarget
import kotlinx.coroutines.CancellationException

/**
 * Presses a callback button of an inline keyboard. TDLib forwards the payload
 * to the bot and waits for its answer, which is returned as a toast, an alert
 * or a URL to open; the bot may also send a new message in the meantime.
 */
internal suspend fun TelegramSession.pressCallbackButtonImpl(
    providerDialogId: String,
    messageId: Long,
    data: String
): Result<BotCallbackAnswer> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getCallbackQueryAnswer(
            chatId = chatId,
            messageId = messageId,
            payload = CallbackQueryPayloadData(data = data.encodeToByteArray())
        )) {
            is TdlResult.Success -> Result.success(
                BotCallbackAnswer(
                    text = result.result.text,
                    showAlert = result.result.showAlert,
                    url = result.result.url.takeIf { url -> url.isNotBlank() }
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

/**
 * Commands of the bot this chat is talking to. Chat-scoped commands cover the
 * menu a bot customized for one chat; the default scope is the fallback for a
 * bot that never customized it. A regular user or channel has no commands.
 */
internal suspend fun TelegramSession.loadBotCommandsImpl(
    providerDialogId: String
): Result<List<BotCommandInfo>> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val botUserId = botUserIdOf(chatId) ?: return Result.success(emptyList())

        val chatCommands = client.getCommands(
            scope = BotCommandScopeChat(chatId = chatId),
            languageCode = ""
        )

        val commands = when (chatCommands) {
            is TdlResult.Success -> chatCommands.result.commands
            is TdlResult.Failure -> {
                when (val fallback = client.getCommands(BotCommandScopeDefault(), "")) {
                    is TdlResult.Success -> fallback.result.commands
                    is TdlResult.Failure -> return Result.failure(fallback.toSessionException())
                }
            }
        }

        // The scope answers for whichever bot is in the chat; a stale answer
        // for another bot would show a menu that does nothing here.
        if (chatCommands is TdlResult.Success &&
            chatCommands.result.botUserId != botUserId
        ) {
            return Result.success(emptyList())
        }

        Result.success(commands.map(::mapBotCommand))
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

/**
 * Runs an inline query against a bot. The chat id matters: the bot may offer
 * different results depending on the chat the message will be sent to.
 */
internal suspend fun TelegramSession.searchInlineBotImpl(
    providerDialogId: String,
    username: String,
    query: String,
    offset: String
): Result<InlineBotPage> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))
    val name = username.removePrefix("@")

    if (name.isBlank()) return Result.failure(IllegalArgumentException("Invalid bot name"))

    return try {
        val chat = when (val found = client.searchPublicChat(name)) {
            is TdlResult.Success -> found.result
            is TdlResult.Failure -> return Result.failure(
                IllegalStateException("@$name was not found")
            )
        }

        val botUserId = (chat.type as? ChatTypePrivate)?.userId
            ?: return Result.failure(IllegalStateException("@$name is not a bot"))

        when (val result = client.getInlineQueryResults(
            botUserId = botUserId,
            chatId = chatId,
            userLocation = null,
            query = query,
            offset = offset
        )) {
            is TdlResult.Success -> Result.success(
                InlineBotPage(
                    queryId = result.result.inlineQueryId,
                    results = result.result.results.mapNotNull(::mapInlineBotResult),
                    nextOffset = result.result.nextOffset
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

/** Sends a picked inline result into the chat it was searched in. */
internal suspend fun TelegramSession.sendInlineBotResultImpl(
    providerDialogId: String,
    queryId: Long,
    resultId: String
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.sendInlineQueryResultMessage(
            chatId = chatId,
            topicId = null,
            replyTo = null,
            options = null,
            queryId = queryId,
            resultId = resultId,
            hideViaBot = false
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
 * Shares a message a mini app prepared. The page knows only the prepared id,
 * so the bot that owns the mini app is resolved here: either from the caller
 * or from the chat the app was opened in. The result is then sent into the
 * chat the user picked, the same way a picked inline result is sent.
 */
internal suspend fun TelegramSession.sharePreparedMessageImpl(
    providerDialogId: String,
    botUserId: Long?,
    preparedMessageId: String,
    targetDialogId: String
): Result<Unit> {
    val sourceChatId = providerDialogId.toLongOrNull()
    val targetChatId = targetDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid target dialog id"))

    val botId = botUserId
        ?: sourceChatId?.let { chatId -> botUserIdOf(chatId) }
        ?: return Result.failure(
            IllegalStateException("The bot that prepared this message is unknown")
        )

    return try {
        val prepared = when (val result = client.getPreparedInlineMessage(
            botUserId = botId,
            preparedMessageId = preparedMessageId
        )) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val resultId = prepared.result.resultId()
            ?: return Result.failure(
                IllegalStateException("The prepared message has no result id")
            )

        when (val result = client.sendInlineQueryResultMessage(
            chatId = targetChatId,
            topicId = null,
            replyTo = null,
            options = null,
            queryId = prepared.inlineQueryId,
            resultId = resultId,
            hideViaBot = false
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

/** The id of an inline result, whichever concrete kind it is. */
private fun InlineQueryResult.resultId(): String? = when (this) {
    is InlineQueryResultArticle -> id
    is InlineQueryResultPhoto -> id
    is InlineQueryResultDocument -> id
    is InlineQueryResultVideo -> id
    is InlineQueryResultAnimation -> id
    is InlineQueryResultAudio -> id
    is InlineQueryResultVoiceNote -> id
    is InlineQueryResultSticker -> id
    is InlineQueryResultContact -> id
    is InlineQueryResultLocation -> id
    is InlineQueryResultVenue -> id
    is InlineQueryResultGame -> id
    else -> null
}

/**
 * Keeps the results this client can show: they all have a title, and most of
 * them a thumbnail. Results without either (games, contacts, polls) are
 * dropped instead of rendering a row that says nothing.
 */
private fun mapInlineBotResult(result: InlineQueryResult): InlineBotResult? {
    val id: String
    val title: String
    val description: String
    val thumbnail: Thumbnail?

    when (result) {
        is InlineQueryResultArticle -> {
            id = result.id
            title = result.title
            description = result.description
            thumbnail = result.thumbnail
        }

        is InlineQueryResultPhoto -> {
            id = result.id
            title = result.title
            description = result.description
            thumbnail = result.photo.sizes
                .minByOrNull { size -> size.width }
                ?.let { size ->
                    Thumbnail(
                        format = ThumbnailFormatJpeg(),
                        width = size.width,
                        height = size.height,
                        file = size.photo
                    )
                }
        }

        is InlineQueryResultDocument -> {
            id = result.id
            title = result.title
            description = result.description
            thumbnail = result.document.thumbnail
        }

        is InlineQueryResultVideo -> {
            id = result.id
            title = result.title
            description = result.description
            thumbnail = result.video.thumbnail
        }

        is InlineQueryResultAnimation -> {
            id = result.id
            title = result.title
            description = ""
            thumbnail = result.animation.thumbnail
        }

        is InlineQueryResultAudio -> {
            id = result.id
            title = result.audio.title
            description = result.audio.performer
            thumbnail = result.audio.albumCoverThumbnail
        }

        is InlineQueryResultVoiceNote -> {
            id = result.id
            title = result.title
            description = ""
            thumbnail = null
        }

        is InlineQueryResultSticker -> {
            id = result.id
            title = result.sticker.emoji
            description = ""
            thumbnail = result.sticker.thumbnail
        }

        else -> return null
    }

    if (title.isBlank() && description.isBlank() && thumbnail == null) return null

    return InlineBotResult(
        id = id,
        title = title.ifBlank { description },
        description = if (title.isBlank()) "" else description,
        thumbnailFileId = thumbnail?.file?.id,
        thumbnailUniqueId = thumbnail?.file?.remote?.uniqueId
    )
}

/**
 * Opens `t.me/<bot>?start=<payload>`: the bot chat is opened and the hidden
 * parameter is delivered as the `/start` argument, exactly like the official
 * clients do. The returned target is the chat to open.
 */
internal suspend fun TelegramSession.botStartTarget(
    botUsername: String,
    startParameter: String
): Result<TelegramLinkTarget> {
    val chat = when (val result = client.searchPublicChat(botUsername)) {
        is TdlResult.Success -> result.result
        is TdlResult.Failure -> return Result.failure(
            IllegalStateException("@$botUsername was not found")
        )
    }

    val botUserId = (chat.type as? ChatTypePrivate)?.userId
        ?: return Result.failure(IllegalStateException("@$botUsername is not a bot"))

    sendBotStartImpl(botUserId, chat.id, startParameter).onFailure { failure ->
        return Result.failure(failure)
    }

    return Result.success(TelegramLinkTarget.Dialog(chat.id.toString()))
}

/** Resolves `t.me/<bot>/<app>` into a URL that can be rendered as a mini app. */
internal suspend fun TelegramSession.webAppLinkTarget(
    botUsername: String,
    webAppShortName: String,
    startParameter: String
): Result<TelegramLinkTarget> {
    val chat = when (val result = client.searchPublicChat(botUsername)) {
        is TdlResult.Success -> result.result
        is TdlResult.Failure -> return Result.failure(
            IllegalStateException("@$botUsername was not found")
        )
    }

    val botUserId = (chat.type as? ChatTypePrivate)?.userId
        ?: return Result.failure(IllegalStateException("@$botUsername is not a bot"))

    val url = buildString {
        append("https://t.me/")
        append(botUsername)

        if (webAppShortName.isNotBlank()) {
            append('/')
            append(webAppShortName)
        }

        if (startParameter.isNotBlank()) {
            append("?startapp=")
            append(startParameter)
        }
    }

    return try {
        when (val result = client.getWebAppUrl(
            botUserId = botUserId,
            url = url,
            parameters = webAppOpenParameters()
        )) {
            is TdlResult.Success -> Result.success(
                TelegramLinkTarget.WebApp(
                    title = chat.title.ifBlank { botUsername },
                    url = result.result.url
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

/**
 * Adds a shared chat folder. The folder becomes visible in the chat list
 * immediately, so resolving the link is also what imports it.
 */
internal suspend fun TelegramSession.chatFolderTarget(link: String): Result<TelegramLinkTarget> {
    return try {
        val info = when (val checked = client.checkChatFolderInviteLink(link)) {
            is TdlResult.Success -> checked.result
            is TdlResult.Failure -> return Result.failure(
                IllegalStateException("This folder link is no longer valid")
            )
        }

        when (val added = client.addChatFolderByInviteLink(
            inviteLink = link,
            // Chats that are not joined yet are left to the user, so adding
            // a folder never silently joins channels.
            chatIds = longArrayOf()
        )) {
            is TdlResult.Success -> Unit
            is TdlResult.Failure -> return Result.failure(added.toSessionException())
        }

        Result.success(
            TelegramLinkTarget.ChatFolder(
                title = info.chatFolderInfo.name.text.text
            )
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

/** Bot links that only make sense with a chat chosen first. */
internal fun <T> TelegramSession.groupBotLink(): Result<T> = Result.failure(
    SessionException(
        code = SessionErrorCode.Unsupported,
        message = "This bot link has to be opened inside a group"
    )
)

/** The public "what can this bot do" line, empty for a non-bot chat. */
internal suspend fun TelegramSession.loadBotDescriptionImpl(
    providerDialogId: String
): Result<String> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val botUserId = botUserIdOf(chatId) ?: return Result.success("")

        when (val result = client.getBotInfoShortDescription(botUserId, "")) {
            is TdlResult.Success -> Result.success(result.result.text)
            is TdlResult.Failure -> Result.success("")
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

/** Sends the hidden `/start` payload of a `t.me/<bot>?start=...` link. */
internal suspend fun TelegramSession.sendBotStartImpl(
    botUserId: Long,
    chatId: Long,
    parameter: String
): Result<Long> {
    return try {
        when (val result = client.sendBotStartMessage(
            botUserId = botUserId,
            chatId = chatId,
            parameter = parameter
        )) {
            is TdlResult.Success -> Result.success(result.result.id)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

private fun mapBotCommand(command: BotCommand): BotCommandInfo {
    return BotCommandInfo(
        command = command.command.removePrefix("/"),
        description = command.description,
        isEphemeral = command.isEphemeral
    )
}

/** The bot behind a private chat, or null when the chat is not a bot chat. */
private suspend fun TelegramSession.botUserIdOf(chatId: Long): Long? {
    val userId = when (val entry = _chats.value[chatId]) {
        null -> when (val chat = client.getChat(chatId)) {
            is TdlResult.Success -> (chat.result.type as? ChatTypePrivate)?.userId
            is TdlResult.Failure -> null
        }

        else -> (entry.type as? ChatTypePrivate)?.userId
    } ?: return null

    val cached = _users.value[userId]
    if (cached != null) {
        return userId.takeIf { cached.type is UserTypeBot }
    }

    return when (val user = client.getUser(userId)) {
        is TdlResult.Success -> userId.takeIf { user.result.type is UserTypeBot }
        is TdlResult.Failure -> null
    }
}
