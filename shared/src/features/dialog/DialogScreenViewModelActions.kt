package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.outbox.OutboxManager

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Message actions the chat screen offers: deleting, forwarding, pinning and
 * dropping a queued send. Everything runs in the view-model scope, so an action
 * survives recomposition and is cancelled with the screen.
 */
internal fun DialogScreenViewModel.deleteMessages(messageIds: LongArray, forEveryone: Boolean = true) {
    if (session == null || messageIds.isEmpty()) return

    // Negative ids are queued local rows that never reached the server. Cancel
    // them instead of asking the provider to delete them.
    messageIds.filter { id -> id < 0 }.forEach { id ->
        OutboxManager.cancelByLocalMessageId(id)
    }

    val serverIds = messageIds.filter { id -> id > 0 }.toLongArray()

    if (serverIds.isEmpty()) {
        selectedMessageIds = selectedMessageIds - messageIds.toSet()
        return
    }

    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.deleteMessages(providerDialogId, serverIds, forEveryone)
            .onSuccess {
                selectedMessageIds = selectedMessageIds - messageIds.toSet()
            }
            .onFailure { failure ->
                val exception = failure.asSessionException()

                // A for-me delete is never queued: the outbox entry cannot
                // carry the flag, so a later retry would delete for everyone.
                if (exception.retryable && forEveryone) {
                    // The server is unreachable; keep the deletion and deliver it
                    // from the outbox when the account is back.
                    OutboxManager.enqueueDelete(
                        providerId = currentSession.providerId,
                        accountId = currentSession.accountId,
                        providerDialogId = providerDialogId,
                        messageIds = serverIds.toList()
                    )

                    selectedMessageIds = selectedMessageIds - messageIds.toSet()
                } else {
                    error = failure.message ?: "Failed to delete messages"
                }
            }
    }
}

/** Resolves a shareable link to the message; null when there is none. */
internal suspend fun DialogScreenViewModel.messageLink(messageId: Long): String? {
    val currentSession = session ?: return null
    return currentSession.messageLink(providerDialogId, messageId).getOrNull()
}

/** Puts the message's text into the composer and switches it to editing. */
internal fun DialogScreenViewModel.startEditing(message: ChatMessage) {
    editingMessage = message
    pendingAttachments = emptyList()

    input = message.text
    inputSelection = TextRange(message.text.length)
    inputCustomEmojis = message.customEmojis

    ensureInputEmojiLoaded()
}

/** Every delete is confirmed first, because it reaches the other side. */
internal fun DialogScreenViewModel.requestDelete(messageIds: LongArray) {
    if (messageIds.isEmpty()) return

    deleteConfirmationIds = messageIds
}

internal fun DialogScreenViewModel.forwardMessages(messageIds: LongArray, targetDialogId: String) {
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.forwardMessages(
            providerDialogId = providerDialogId,
            messageIds = messageIds,
            targetDialogId = targetDialogId,
            hideSender = forwardHideSender,
            removeCaption = forwardRemoveCaption
        ).onSuccess {
            selectedMessageIds = emptySet()
        }.onFailure {
            error = it.message ?: "Failed to forward messages"
        }
    }
}

internal fun DialogScreenViewModel.pinMessage(message: ChatMessage, isPinned: Boolean) {
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.setMessagePinned(providerDialogId, message.messageId, isPinned)
            .onFailure { error = it.message ?: "Failed to pin message" }
    }
}

/** Drops a queued message that has not reached the server yet. */
internal fun DialogScreenViewModel.cancelPendingMessage(messageId: Long) {
    OutboxManager.cancelByLocalMessageId(messageId)
}

/** Flags the chat unread so it comes back to the list as new. */
internal fun DialogScreenViewModel.markDialogUnread(unread: Boolean) {
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.setDialogUnread(providerDialogId, unread)
            .onFailure { error = it.message ?: "Failed to update the chat state" }
    }
}

internal fun DialogScreenViewModel.unpinPinnedMessage() {
    val messageId = dialog?.pinnedMessageId ?: return
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.setMessagePinned(providerDialogId, messageId, isPinned = false)
            .onFailure { error = it.message ?: "Failed to unpin message" }
    }
}

internal fun DialogScreenViewModel.stopLiveLocation(messageId: Long) {
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.stopLiveLocation(
            providerDialogId = providerDialogId,
            messageId = messageId
        ).onFailure { failure ->
            error = failure.message ?: "Failed to stop sharing the location"
        }
    }
}

internal fun DialogScreenViewModel.sendPoll(draft: PollDraft) {
    val currentSession = session ?: return
    val options = draft.options.filter { it.text.isNotBlank() }

    viewModelScope.launch {
        currentSession.sendPoll(
            providerDialogId = providerDialogId,
            question = draft.question,
            options = options.map { it.text },
            isAnonymous = draft.isAnonymous,
            allowsMultipleAnswers = draft.allowsMultipleAnswers,
            allowsRevoting = draft.allowsRevoting,
            questionCustomEmojis = draft.questionCustomEmojis,
            optionCustomEmojis = options.map { it.customEmojis },
            optionMediaPaths = options.map { it.mediaPath },
            isQuiz = draft.isQuiz,
            correctOptionIndex = draft.correctOptionIndex,
            explanation = draft.explanation
        ).onSuccess {
            scrollRequest = ScrollRequest.Latest
        }.onFailure { failure ->
            error = failure.message ?: "Failed to create the poll"
        }
    }
}

internal fun DialogScreenViewModel.sendLocation(
    latitude: Double,
    longitude: Double,
    livePeriodSeconds: Int
) {
    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.sendLocation(
            providerDialogId = providerDialogId,
            latitude = latitude,
            longitude = longitude,
            livePeriodSeconds = livePeriodSeconds
        ).onSuccess {
            scrollRequest = ScrollRequest.Latest
        }.onFailure { failure ->
            error = failure.message ?: "Failed to share the location"
        }
    }
}
