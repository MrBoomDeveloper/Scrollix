package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.DeepLinkHandler
import com.mrboomdev.scrollix.data.model.BusinessQuickReply
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReplyQuote
import com.mrboomdev.scrollix.data.model.UploadProgressStore
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.features.dialog.bots.InvoicePaymentDialog
import com.mrboomdev.scrollix.features.dialog.bots.MainMiniAppDialog
import com.mrboomdev.scrollix.features.dialog.bots.MiniAppDialog
import com.mrboomdev.scrollix.features.dialog.bots.sendBusinessQuickReply
import com.mrboomdev.scrollix.features.dialog.composer.AttachmentReviewDialog
import com.mrboomdev.scrollix.features.dialog.composer.QuickReplyPickerDialog
import com.mrboomdev.scrollix.features.dialog.composer.RecordedMediaDialog
import com.mrboomdev.scrollix.features.dialog.composer.ScheduledMessagesDialog
import com.mrboomdev.scrollix.features.dialog.composer.VideoTrimSheet
import com.mrboomdev.scrollix.features.dialog.media.LocationPickerDialog
import com.mrboomdev.scrollix.features.dialog.message.MessageDetailsDialog
import com.mrboomdev.scrollix.features.dialog.message.MessageTextSelectionDialog
import com.mrboomdev.scrollix.features.dialog.message.ReactionListDialog
import com.mrboomdev.scrollix.features.dialog.viewmodel.DialogScreenViewModel
import com.mrboomdev.scrollix.features.dialog.viewmodel.appendToComposer
import com.mrboomdev.scrollix.features.dialog.viewmodel.clearComposer
import com.mrboomdev.scrollix.features.dialog.viewmodel.deleteMessages
import com.mrboomdev.scrollix.features.dialog.viewmodel.discardRecording
import com.mrboomdev.scrollix.features.dialog.viewmodel.forwardMessages
import com.mrboomdev.scrollix.features.dialog.viewmodel.replaceAttachment
import com.mrboomdev.scrollix.features.dialog.viewmodel.sendLocation
import com.mrboomdev.scrollix.features.dialog.viewmodel.sendPoll
import com.mrboomdev.scrollix.features.dialog.viewmodel.setForwardFolder
import com.mrboomdev.scrollix.features.dialog.viewmodel.setForwardQuery
import com.mrboomdev.scrollix.features.dialog.viewmodel.unpinPinnedMessage
import com.mrboomdev.scrollix.features.dialog.viewmodel.updateInput
import com.mrboomdev.scrollix.moved
import com.mrboomdev.scrollix.platform.files.FilePicker
import com.mrboomdev.scrollix.platform.media.MediaRecordingController
import com.mrboomdev.scrollix.ui.components.StickerSetDialog
import com.mrboomdev.scrollix.ui.components.StickerSetTarget
import com.mrboomdev.scrollix.ui.components.photoeditor.PhotoEditorDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp

@Composable
internal fun DialogChatOverlays(
    ui: DialogScreenViewModel,
    providerDialogId: String,
    isMobileLayout: Boolean,
    mediaRecorder: MediaRecordingController,
    filePicker: FilePicker,
    onSend: () -> Unit
) = with(ui) {
    var forwardTopicQueue by remember { mutableStateOf<List<Dialog>>(emptyList()) }
    var forwardTopicMessageIds by remember { mutableStateOf<LongArray?>(null) }

    // The review rows read the same fractions the pending bubble shows.
    val uploadProgress by UploadProgressStore.progress.collectAsState()
    val quickReplies by session?.businessQuickReplies?.collectAsState()
        ?: remember { mutableStateOf(emptyList<BusinessQuickReply>()) }

    editingAttachmentIndex?.let { index ->
        val attachment = pendingAttachments.getOrNull(index)

        if (attachment == null) {
            LaunchedEffect(index) { editingAttachmentIndex = null }
        } else {
            PhotoEditorDialog(
                path = attachment.file.path,
                onDismiss = { editingAttachmentIndex = null },
                onApply = { edited ->
                    replaceAttachment(index, edited)
                    editingAttachmentIndex = null
                }
            )
        }
    }

    trimmingAttachmentIndex?.let { index ->
        val attachment = pendingAttachments.getOrNull(index)

        if (attachment == null) {
            LaunchedEffect(index) { trimmingAttachmentIndex = null }
        } else {
            VideoTrimSheet(
                path = attachment.file.path,
                fileName = attachment.file.name,
                isMobile = isMobileLayout,
                onDismiss = { trimmingAttachmentIndex = null },
                onApply = { trimmed ->
                    replaceAttachment(index, trimmed)
                    trimmingAttachmentIndex = null
                }
            )
        }
    }

    selectTextMessage?.let { message ->
        MessageTextSelectionDialog(
            text = message.text,
            onReply = if (dialog?.canSendMessages != false) {
                { quote, position ->
                    replyQuote = MessageReplyQuote(text = quote, position = position)
                    replyingTo = message
                    selectTextMessage = null
                }
            } else {
                null
            },
            onDismiss = { selectTextMessage = null }
        )
    }

    detailsMessage?.let { message ->
        MessageDetailsDialog(
            // The sheet keeps up with an edit that lands while it is open.
            message = messagesById[message.messageId] ?: message,
            session = session,
            onDismiss = { detailsMessage = null }
        )
    }

    reactionListMessage?.let { message ->
        val live = messagesById[message.messageId] ?: message
        val focus = reactionListFocusKey

        ReactionListDialog(
            // The pressed reaction comes first; the rest keep server order.
            reactions = live.reactions.sortedByDescending { reaction ->
                MessageReactionOption(
                    emoji = reaction.emoji,
                    customEmojiId = reaction.customEmojiId
                ).key == focus
            },
            customEmojiData = customEmojiData,
            customEmojiAdaptive = customEmojiAdaptive,
            session = session,
            providerDialogId = providerDialogId,
            messageId = live.messageId,
            onDismiss = {
                reactionListMessage = null
                reactionListFocusKey = null
            }
        )
    }

    forwardingMessageIds?.let { messageIds ->
        ForwardPicker(
            messageIds = messageIds,
            isMobileLayout = isMobileLayout,
            targets = forwardTargets,
            folders = forwardFolderOptions,
            query = forwardQuery,
            folderKey = forwardFolderKey,
            supportsOptions = supportsForwardOptions,
            hideSender = forwardHideSender,
            removeCaption = forwardRemoveCaption,
            onQueryChange = ::setForwardQuery,
            onFolderChange = ::setForwardFolder,
            onHideSenderChange = { checked -> forwardHideSender = checked },
            onRemoveCaptionChange = { checked -> forwardRemoveCaption = checked },
            onDismiss = { forwardingMessageIds = null },
            onSend = { selected ->
                forwardingMessageIds = null

                val forumTargets = selected.filter { target -> target.isForum }

                forwardTopicQueue = forumTargets
                forwardTopicMessageIds = messageIds.takeIf { forumTargets.isNotEmpty() }

                selected
                    .filterNot { target -> target.isForum }
                    .forEach { target ->
                        forwardMessages(
                            messageIds = messageIds,
                            targetDialogId = target.providerDialogId
                        )
                    }
            }
        )
    }

    val nextForwardTopic = forwardTopicQueue.firstOrNull()
    val forwardTopicIds = forwardTopicMessageIds

    if (nextForwardTopic != null && forwardTopicIds != null) {
        ForwardTopicDialog(
            accountId = nextForwardTopic.accountId,
            providerDialogId = nextForwardTopic.providerDialogId,
            title = nextForwardTopic.title,
            onDismiss = {
                forwardTopicQueue = emptyList()
                forwardTopicMessageIds = null
            },
            onPick = { topicId ->
                forwardMessages(
                    messageIds = forwardTopicIds,
                    targetDialogId = nextForwardTopic.providerDialogId,
                    targetTopicId = topicId
                )

                forwardTopicQueue = forwardTopicQueue.drop(1)
                if (forwardTopicQueue.isEmpty()) forwardTopicMessageIds = null
            }
        )
    }

    deleteConfirmationIds?.let { messageIds ->
        AlertDialog(
            onDismissRequest = { deleteConfirmationIds = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    if (messageIds.size == 1) {
                        "Delete this message?"
                    } else {
                        "Delete ${messageIds.size} messages?"
                    }
                )
            },
            text = {
                Text(
                    "Deleting for everyone removes the message for every" +
                        " participant. Deleting for me only hides it here." +
                        " This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmationIds = null
                        deleteMessages(messageIds, forEveryone = true)
                    }
                ) {
                    Text("Delete for everyone")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleteConfirmationIds = null }) {
                        Text("Cancel")
                    }

                    TextButton(
                        onClick = {
                            deleteConfirmationIds = null
                            deleteMessages(messageIds, forEveryone = false)
                        }
                    ) {
                        Text("Delete for me")
                    }
                }
            }
        )
    }

    if (showUnpinConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnpinConfirmation = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            text = { Text("Unpin this message?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnpinConfirmation = false
                        unpinPinnedMessage()
                    }
                ) {
                    Text("Unpin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpinConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val stickerSetId = openedStickerSetId

    if (stickerSetId != null && session != null) {
        StickerSetDialog(
            target = StickerSetTarget.ById(stickerSetId),
            session = session,
            onDismiss = { openedStickerSetId = null }
        )
    }

    val activeWebAppButton = webAppButton

    if (activeWebAppButton != null) {
        MiniAppDialog(
            session = session,
            providerDialogId = providerDialogId,
            button = activeWebAppButton,
            onDismiss = { webAppButton = null }
        )
    }

    // A callback button's answer is modal only when the bot asked for it;
    // toasts go through the notice host instead.
    callbackAlert?.let { alert ->
        AlertDialog(
            onDismissRequest = { callbackAlert = null },
            confirmButton = {
                TextButton(onClick = {
                    callbackAlert = null
                    alert.url?.let { url -> DeepLinkHandler.publish(url) }
                }) {
                    Text(if (alert.url != null) "Open" else "OK")
                }
            },
            dismissButton = if (alert.url != null) {
                {
                    TextButton(onClick = { callbackAlert = null }) {
                        Text("Cancel")
                    }
                }
            } else {
                null
            },
            text = { Text(alert.text.ifBlank { "The bot did not say anything" }) }
        )
    }

    if (showMainWebApp) {
        MainMiniAppDialog(
            session = session,
            providerDialogId = providerDialogId,
            title = dialog?.title ?: "Mini app",
            onDismiss = { showMainWebApp = false }
        )
    }

    val invoiceMessage = paymentMessage

    if (invoiceMessage != null && session != null) {
        InvoicePaymentDialog(
            session = session,
            providerDialogId = providerDialogId,
            message = invoiceMessage,
            onDismiss = { paymentMessage = null }
        )
    }

    if (showQuickReplies && session != null) {
        QuickReplyPickerDialog(
            replies = quickReplies,
            onDismiss = { showQuickReplies = false },
            onPick = { reply ->
                showQuickReplies = false
                sendBusinessQuickReply(reply.id)
            }
        )
    }

    if (showPollDialog) {
        PollCreateDialog(
            session = session,
            isMobile = isMobileLayout,
            onDismiss = { showPollDialog = false },
            onCreate = { draft ->
                showPollDialog = false
                sendPoll(draft)
            }
        )
    }

    if (showChecklistDialog) {
        ChecklistCreateDialog(
            header = "New checklist",
            confirmLabel = "Send",
            onDismiss = { showChecklistDialog = false },
            onConfirm = { draft ->
                showChecklistDialog = false
                sendChecklist(draft)
            }
        )
    }

    addChecklistTasksTarget?.let { target ->
        ChecklistCreateDialog(
            header = "Add tasks",
            confirmLabel = "Add",
            allowTitleEdit = false,
            showOptions = false,
            onDismiss = { addChecklistTasksTarget = null },
            onConfirm = { draft ->
                addChecklistTasksTarget = null
                addChecklistTasks(target, draft.tasks)
            }
        )
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            onDismiss = { showLocationDialog = false },
            onSend = { latitude, longitude, livePeriodSeconds ->
                showLocationDialog = false
                sendLocation(latitude, longitude, livePeriodSeconds)
            }
        )
    }

    recordedMedia?.let { recording ->
        RecordedMediaDialog(
            recording = recording,
            isSending = isSendingRecording,
            error = recordingError,
            onDiscard = { discardRecording() },
            onSend = { sendRecordedMedia(recording) }
        )
    }

    threadRoot?.let { root ->
        session?.let { currentSession ->
            MessageThreadDialog(
                rootMessage = root,
                session = currentSession,
                onDismiss = { threadRoot = null }
            )
        }
    }

    if (showTopics) {
        val currentDialog = dialog

        if (session != null && currentDialog != null) {
            ForumTopicsDialog(
                session = session,
                providerDialogId = providerDialogId,
                title = currentDialog.title,
                onDismiss = { showTopics = false }
            )
        }
    }

    if (showScheduledMessages) {
        val currentSession = session

        if (currentSession != null) {
            ScheduledMessagesDialog(
                session = currentSession,
                providerDialogId = providerDialogId,
                onDismiss = { showScheduledMessages = false }
            )
        }
    }

    if (showAutoDeleteTime) {
        val currentSession = session

        if (currentSession != null) {
            AutoDeleteTimeDialog(
                session = currentSession,
                providerDialogId = providerDialogId,
                onDismiss = { showAutoDeleteTime = false }
            )
        }
    }

    if (showTranslationLanguage) {
        TranslationLanguageDialog(
            currentCode = normalizeTranslationLanguage(settings.translationLanguage),
            deviceLanguage = translationLanguageCode(Locale.current.language),
            onSelect = { code ->
                showTranslationLanguage = false
                setTranslationLanguage(code)
                translateLoadedMessages()
            },
            onDismiss = { showTranslationLanguage = false }
        )
    }

    // Attachments are reviewed in a blocking modal: the chat stays dimmed and
    // the composer can't be used until the message is sent or discarded.
    if (pendingAttachments.isNotEmpty()) {
        AttachmentReviewDialog(
            attachments = pendingAttachments,
            caption = input,
            captionCustomEmojis = inputCustomEmojis,
            captionEmojiData = inputEmojiData,
            captionEmojiAdaptive = inputEmojiAdaptive,
            isSending = isSending,
            uploadProgress = uploadProgress,
            sendPaused = attachmentSendPaused,
            sendError = attachmentSendError,
            compressPhotos = attachmentCompressPhotos,
            onCompressPhotosChange = { enabled ->
                attachmentCompressPhotos = enabled
            },
            isMobile = isMobileLayout,
            session = session,
            onCaptionChange = { value -> updateInput(value, TextRange(value.length)) },
            onInsertEmoji = { fallback, emojiId -> appendToComposer(fallback, emojiId) },
            onAddImages = { filePicker.pickImages() },
            onAddFiles = { filePicker.pickFiles() },
            onMove = { from, to -> pendingAttachments = pendingAttachments.moved(from, to) },
            onEditImage = { index -> editingAttachmentIndex = index },
            onTrimVideo = { index -> trimmingAttachmentIndex = index },
            onPauseSend = { pauseAttachmentSend() },
            onResumeSend = { resumeAttachmentSend() },
            onRemove = { index ->
                pendingAttachments = pendingAttachments.filterIndexed { itemIndex, _ ->
                    itemIndex != index
                }
            },
            onDiscard = {
                // The review owns the queued entry: leaving it behind would
                // send the message after the user discarded it.
                attachmentSendEntryId?.let { entryId ->
                    OutboxManager.cancelEntry(entryId)
                }

                attachmentSendEntryId = null
                attachmentSendPaused = false
                attachmentSendError = null
                pendingAttachments = emptyList()
                clearComposer()
                showAttachmentMenu = false
            },
            onSend = onSend,
            onSchedule = if (
                dialog?.isSavedMessages != true &&
                session?.supportsScheduling == true
            ) {
                { showSchedulePicker = true }
            } else {
                null
            },
            onSendSilently = if (session?.supportsSilentSend == true) {
                { sendMessage(keyboardController = null, silent = true) }
            } else {
                null
            }
        )
    }
}
