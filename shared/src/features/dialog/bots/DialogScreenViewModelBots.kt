package com.mrboomdev.scrollix.features.dialog.bots

import com.mrboomdev.scrollix.DeepLinkHandler
import com.mrboomdev.scrollix.data.model.BotCallbackAnswer
import com.mrboomdev.scrollix.data.model.BotCommandInfo
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.InlineBotResult
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.ReplyKeyboardButton
import com.mrboomdev.scrollix.features.dialog.composer.ComposerEditKind
import com.mrboomdev.scrollix.features.dialog.composer.parseInlineBotQuery
import com.mrboomdev.scrollix.features.dialog.sendMessage
import com.mrboomdev.scrollix.features.dialog.viewmodel.DialogScreenViewModel
import com.mrboomdev.scrollix.features.dialog.viewmodel.ensureInputEmojiLoaded
import com.mrboomdev.scrollix.features.dialog.viewmodel.recordComposerEdit
import com.mrboomdev.scrollix.features.dialog.viewmodel.scheduleDraftSave
import com.mrboomdev.scrollix.features.dialog.viewmodel.updateInput
import com.mrboomdev.scrollix.ui.notice.AppNotice
import com.mrboomdev.scrollix.ui.notice.NoticeCenter

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Presses an inline keyboard button that needs the provider. The bot answers
 * synchronously with a toast, an alert or a URL; a bot that sends a new message
 * instead only needs its update, which arrives through the normal history.
 */
internal fun DialogScreenViewModel.pressBotButton(message: ChatMessage, button: MessageButton) {
    val data = button.callbackData ?: return
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.pressCallbackButton(
            providerDialogId = providerDialogId,
            messageId = message.messageId,
            data = data
        )
            .onSuccess { answer -> showBotAnswer(answer) }
            .onFailure { failure ->
                NoticeCenter.error(failure.message ?: "The bot did not answer")
            }
    }
}

/** Shows what a bot replied to a callback, including a URL it asked to open. */
internal fun DialogScreenViewModel.showBotAnswer(answer: BotCallbackAnswer) {
    when {
        answer.showAlert -> callbackAlert = answer
        answer.text.isNotBlank() -> NoticeCenter.post(AppNotice(answer.text))
    }

    val url = answer.url ?: return

    // t.me links stay inside the app so a bot can open a chat or a pack.
    if (DeepLinkHandler.isTelegramLink(url)) {
        DeepLinkHandler.publish(url)
    } else {
        NoticeCenter.post(
            AppNotice(
                message = answer.text.ifBlank { "The bot asked to open a link" },
                actionLabel = "Open",
                action = { DeepLinkHandler.publish(url) }
            )
        )
    }
}

/**
 * Loads the command menu and the short description of the chat's bot once the
 * screen opens. Both are empty for a regular chat, so nothing is requested
 * twice and the empty state stays as it was.
 */
internal fun DialogScreenViewModel.loadBotCommands() {
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.loadBotCommands(providerDialogId)
            .onSuccess { loaded -> botCommands = loaded }
    }

    viewModelScope.launch {
        currentSession.loadBotDescription(providerDialogId)
            .onSuccess { description -> botDescription = description }
    }
}

/**
 * A reply keyboard text button sends its label right away, the way Telegram
 * does, instead of leaving it in the composer for another tap.
 */
internal fun DialogScreenViewModel.sendReplyKeyboardText(text: String) {
    if (text.isBlank()) return
    input = text
    inputSelection = androidx.compose.ui.text.TextRange(text.length)
    sendMessage(keyboardController = null)
}

/** A mini app button of the reply keyboard opens like an inline one. */
internal fun DialogScreenViewModel.openReplyKeyboardWebApp(button: ReplyKeyboardButton) {
    val url = button.webAppUrl ?: return

    if (session == null) return

    webAppButton = MessageButton(
        text = button.text,
        webAppUrl = url,
        botUserId = button.botUserId
    )
}

/**
 * Refreshes the inline picker for the current draft. The request is debounced
 * and skipped while the draft still names the same bot with the same query, so
 * typing a message body does not fire a query per keystroke.
 */
internal fun DialogScreenViewModel.refreshInlineBotQuery() {
    val parsed = parseInlineBotQuery(input)

    if (parsed == null) {
        inlineBotJob?.cancel()
        inlineBotQuery = null
        inlineBotQueryId = null
        inlineBotResults = emptyList()
        inlineBotLoading = false
        return
    }

    if (parsed == inlineBotQuery) return
    inlineBotQuery = parsed

    inlineBotJob?.cancel()
    inlineBotJob = viewModelScope.launch {
        delay(300)

        val currentSession = session ?: return@launch

        inlineBotLoading = true

        try {
            currentSession.searchInlineBot(
                providerDialogId = providerDialogId,
                username = parsed.username,
                query = parsed.query
            )
                .onSuccess { page ->
                    // A reply for an already edited draft must not replace the
                    // picker of the newer query.
                    if (inlineBotQuery != parsed) return@onSuccess

                    inlineBotQueryId = page.queryId
                    inlineBotResults = page.results
                }
                .onFailure { failure ->
                    if (inlineBotQuery != parsed) return@onFailure

                    inlineBotQueryId = null
                    inlineBotResults = emptyList()
                    NoticeCenter.error(failure.message ?: "The bot did not answer")
                }
        } finally {
            if (inlineBotQuery == parsed) inlineBotLoading = false
        }
    }
}

/** Sends a picked inline result and clears the draft that asked for it. */
internal fun DialogScreenViewModel.sendInlineBotResult(result: InlineBotResult) {
    val currentSession = session ?: return
    val queryId = inlineBotQueryId ?: return

    viewModelScope.launch {
        inlineBotSending = result.id

        currentSession.sendInlineBotResult(
            providerDialogId = providerDialogId,
            queryId = queryId,
            resultId = result.id
        )
            .onSuccess {
                inlineBotJob?.cancel()
                inlineBotQuery = null
                inlineBotQueryId = null
                inlineBotResults = emptyList()
                updateInput("", TextRange.Zero)
            }
            .onFailure { failure ->
                NoticeCenter.error(failure.message ?: "The result could not be sent")
            }

        inlineBotSending = null
    }
}

/** Sends every message of a saved quick reply into this chat. */
internal fun DialogScreenViewModel.sendBusinessQuickReply(shortcutId: Int) {
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.sendBusinessQuickReply(providerDialogId, shortcutId)
            .onFailure { failure ->
                NoticeCenter.error(failure.message ?: "The quick reply was not sent")
            }
    }
}

/** Replaces the slash token with a picked command and leaves the caret after it. */
internal fun DialogScreenViewModel.applyBotCommand(command: BotCommandInfo) {
    recordComposerEdit(ComposerEditKind.Discrete)

    val text = command.insertion + " "
    input = text
    inputSelection = androidx.compose.ui.text.TextRange(text.length)
    inputCustomEmojis = emptyList()
    inputFormatting = emptyList()
    ensureInputEmojiLoaded()
    scheduleDraftSave()
}
