package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.BotCallbackAnswer
import com.mrboomdev.scrollix.data.model.BotCommandInfo
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.model.WebAppLaunch

/**
 * Bot keyboards, commands and mini apps. They live in their own contract so
 * providers that have no bots simply keep the defaults below instead of
 * stubbing every method.
 */
interface BotSession {
    /**
     * Resolves a bot mini app button through the provider and returns the URL
     * to render. Providers without mini apps keep the default failure.
     */
    suspend fun openWebApp(
        providerDialogId: String,
        button: MessageButton
    ): Result<WebAppLaunch> = Result.failure(
        UnsupportedOperationException("Mini apps are not supported by this provider")
    )

    /** Opens the main mini app of a bot chat, when the bot exposes one. */
    suspend fun openMainWebApp(providerDialogId: String): Result<WebAppLaunch> =
        Result.failure(
            UnsupportedOperationException("Main mini apps are not supported by this provider")
        )

    /** Tells the provider that a mini app closed. */
    suspend fun closeWebApp(launchId: Long) = Unit

    /**
     * Presses an inline keyboard button of [messageId] that carries callback
     * data. The bot answers synchronously, so its reply can be shown without
     * waiting for a message update.
     */
    suspend fun pressCallbackButton(
        providerDialogId: String,
        messageId: Long,
        data: String
    ): Result<BotCallbackAnswer> = Result.failure(
        SessionException(
            SessionErrorCode.Unsupported,
            "Callback buttons are not supported by this provider"
        )
    )

    /**
     * Commands the bot of this chat advertises, in menu order. Empty for a
     * non-bot chat; providers without bot menus return an empty list.
     */
    suspend fun loadBotCommands(providerDialogId: String): Result<List<BotCommandInfo>> =
        Result.success(emptyList())

    /**
     * The "what can this bot do" line a bot advertises to everyone. Empty for
     * a chat that has no bot or a bot without a description.
     */
    suspend fun loadBotDescription(providerDialogId: String): Result<String> =
        Result.success("")

    /**
     * Runs an inline query against [username] for the draft being typed in
     * [providerDialogId]. The page carries the id that sends a picked result.
     */
    suspend fun searchInlineBot(
        providerDialogId: String,
        username: String,
        query: String,
        offset: String = ""
    ): Result<com.mrboomdev.scrollix.data.model.InlineBotPage> = Result.failure(
        SessionException(
            SessionErrorCode.Unsupported,
            "Inline bots are not supported by this provider"
        )
    )

    /** Sends one [InlineBotResult] into the chat it was searched in. */
    suspend fun sendInlineBotResult(
        providerDialogId: String,
        queryId: Long,
        resultId: String
    ): Result<Unit> = Result.failure(
        SessionException(
            SessionErrorCode.Unsupported,
            "Inline bots are not supported by this provider"
        )
    )

    /**
     * Shares a message a mini app prepared (`shareMessage`). The page sends
     * only the prepared id; [botUserId] is the bot that owns the mini app when
     * the caller already knows it, and [targetDialogId] is the chat the user
     * picked.
     */
    suspend fun sharePreparedMessage(
        providerDialogId: String,
        botUserId: Long?,
        preparedMessageId: String,
        targetDialogId: String
    ): Result<Unit> = Result.failure(
        SessionException(
            SessionErrorCode.Unsupported,
            "Sharing prepared messages is not supported by this provider"
        )
    )
}
