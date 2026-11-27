package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.outbox.OutboxAttachment
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.platform.media.MediaRecordingKind
import com.mrboomdev.scrollix.platform.media.RecordedMedia

import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Sending, editing and reactions. These run in the view-model scope, so a send
 * that is in flight when the composer is re-laid-out is not started twice.
 */
internal fun DialogScreenViewModel.applyMessageReaction(
    message: ChatMessage,
    option: MessageReactionOption
) {
    val currentSession = session ?: return

    val isChosen = message.reactions.any { reaction ->
        reaction.isChosen && if (option.customEmojiId != null) {
            reaction.customEmojiId == option.customEmojiId
        } else {
            reaction.customEmojiId == null && reaction.emoji == option.emoji
        }
    }

    viewModelScope.launch {
        if (isChosen) {
            currentSession.removeMessageReaction(
                providerDialogId = providerDialogId,
                messageId = message.messageId,
                emoji = option.emoji,
                customEmojiId = option.customEmojiId
            )
        } else {
            currentSession.addMessageReaction(
                providerDialogId = providerDialogId,
                messageId = message.messageId,
                emoji = option.emoji,
                customEmojiId = option.customEmojiId
            )
        }
    }
}

internal fun DialogScreenViewModel.sendMessage(
    keyboardController: SoftwareKeyboardController?,
    scheduling: MessageScheduling? = null
) {
    val text = input.trim()
    val attachments = pendingAttachments
    val currentSession = session ?: return

    // A caption edit may empty the caption on purpose, so it is exempt from the
    // "nothing to send" guard.
    val editingCaption = editingMessage?.editsCaption == true

    if (text.isEmpty() && attachments.isEmpty() && !editingCaption) return
    if (editingMessage != null && attachments.isNotEmpty()) return
    // Edits keep their own flow; a scheduled edit would target a message that
    // does not exist yet.
    if (editingMessage != null && scheduling != null) return

    keyboardController?.hide()

    val editing = editingMessage

    // Editing keeps its flow, and attachments keep the blocking review modal;
    // only a plain text message is shown optimistically.
    if (editing != null || attachments.isNotEmpty()) {
        val spans = inputCustomEmojis
        val formatting = inputFormatting
        val replyToMessageId = replyingTo?.messageId

        clearComposer()

        if (editing != null) {
            editingMessage = null

            viewModelScope.launch {
                isSending = true
                error = null

                val entry = OutboxManager.enqueueEdit(
                    providerId = currentSession.providerId,
                    accountId = currentSession.accountId,
                    providerDialogId = providerDialogId,
                    messageId = editing.messageId,
                    text = text,
                    customEmojis = spans,
                    formatting = formatting,
                    isCaptionEdit = editing.editsCaption
                )

                OutboxManager.sendNow(entry.id).onFailure { failure ->
                    error = failure.message ?: "Failed to edit message"
                }

                isSending = false
            }

            return
        }

        pendingAttachments = emptyList()
        replyingTo = null

        // A scheduled message stays in the queue instead of jumping the list.
        if (scheduling == null) {
            scrollRequest = ScrollRequest.Latest
        }

        viewModelScope.launch {
            isSending = true
            error = null

            val entry = OutboxManager.enqueueFiles(
                providerId = currentSession.providerId,
                accountId = currentSession.accountId,
                providerDialogId = providerDialogId,
                attachments = attachments.map { attachment ->
                    OutboxAttachment(
                        path = attachment.file.path,
                        asPhoto = attachment.asPhoto
                    )
                },
                caption = text,
                replyToMessageId = replyToMessageId,
                customEmojis = spans,
                formatting = formatting,
                scheduling = scheduling
            )

            OutboxManager.sendNow(entry.id).onFailure { failure ->
                error = failure.message ?: "Failed to send attachments"
            }

            isSending = false
        }

        return
    }

    // The outbox shows the row immediately and owns the retry loop, so the
    // composer is free as soon as the entry is persisted. Retryable failures
    // (offline, timeout, flood wait) stay queued and are retried with backoff;
    // only a permanent provider error marks the row failed.
    val spans = inputCustomEmojis
    val formatting = inputFormatting
    val replyToMessageId = replyingTo?.messageId

    clearComposer()
    replyingTo = null
    error = null

    if (scheduling == null) {
        scrollRequest = ScrollRequest.Latest
    }

    viewModelScope.launch {
        val entry = OutboxManager.enqueue(
            providerId = currentSession.providerId,
            accountId = currentSession.accountId,
            providerDialogId = providerDialogId,
            text = text,
            replyToMessageId = replyToMessageId,
            customEmojis = spans,
            formatting = formatting,
            scheduling = scheduling
        )

        OutboxManager.sendNow(entry.id).onFailure { failure ->
            error = failure.message ?: "Failed to send message"
        }
    }
}

internal fun DialogScreenViewModel.sendRecordedMedia(recording: RecordedMedia) {
    val currentSession = session ?: return
    if (isSendingRecording) return

    isSendingRecording = true
    recordingError = null

    viewModelScope.launch {
        val durationSeconds = (recording.durationMs / 1000L)
            .toInt()
            .coerceAtLeast(1)

        val result = when (recording.kind) {
            MediaRecordingKind.Voice -> currentSession.sendVoiceNote(
                providerDialogId = providerDialogId,
                path = recording.path,
                durationSeconds = durationSeconds
            )

            MediaRecordingKind.VideoNote -> currentSession.sendVideoNote(
                providerDialogId = providerDialogId,
                path = recording.path,
                durationSeconds = durationSeconds
            )
        }

        isSendingRecording = false

        result.onSuccess {
            activeRecorder?.release(recording)
            recordedMedia = null
            scrollRequest = ScrollRequest.Latest
        }.onFailure { failure ->
            // Keep the review dialog open so the note can be retried.
            recordingError = failure.message ?: "Failed to send the recording"
            recordedMedia = recording
        }
    }
}
