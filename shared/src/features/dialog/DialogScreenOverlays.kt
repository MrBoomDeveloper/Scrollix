package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.platform.files.FilePicker
import com.mrboomdev.scrollix.platform.media.MediaRecordingController
import com.mrboomdev.scrollix.moved
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.components.StickerSetDialog
import com.mrboomdev.scrollix.ui.components.StickerSetTarget
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.search_icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
private fun ForwardOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
internal fun DialogChatOverlays(
    ui: DialogScreenViewModel,
    providerDialogId: String,
    isMobileLayout: Boolean,
    mediaRecorder: MediaRecordingController,
    filePicker: FilePicker,
    onSend: () -> Unit
) = with(ui) {
    selectTextMessage?.let { message ->
        MessageTextSelectionDialog(
            text = message.text,
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
            onDismiss = {
                reactionListMessage = null
                reactionListFocusKey = null
            }
        )
    }

    forwardingMessageIds?.let { messageIds ->
        AlertDialog(
            onDismissRequest = { forwardingMessageIds = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Forward to") },
            confirmButton = {
                TextButton(onClick = { forwardingMessageIds = null }) {
                    Text("Cancel")
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        value = forwardQuery,
                        onValueChange = { value -> setForwardQuery(value) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Search chats") },
                        leadingIcon = {
                            Icon(imageVector = search_icon, contentDescription = null)
                        },
                        trailingIcon = {
                            if (forwardQuery.isNotEmpty()) {
                                IconButton(onClick = { setForwardQuery("") }) {
                                    Icon(
                                        imageVector = close_icon,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        }
                    )

                    if (forwardFolderOptions.size > 1) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(count = forwardFolderOptions.size) { index ->
                                val (key, label) = forwardFolderOptions[index]

                                FilterChip(
                                    selected = forwardFolderKey == key,
                                    onClick = { setForwardFolder(key) },
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }
                    }

                    // Providers that can re-send content without the forward
                    // header offer both options; the rest never show them.
                    if (supportsForwardOptions) {
                        ForwardOptionRow(
                            label = "Hide sender",
                            checked = forwardHideSender,
                            onCheckedChange = { checked -> forwardHideSender = checked }
                        )

                        ForwardOptionRow(
                            label = "Remove caption",
                            checked = forwardRemoveCaption,
                            onCheckedChange = { checked -> forwardRemoveCaption = checked }
                        )
                    }

                    if (filteredForwardTargets.isEmpty()) {
                        Text(
                            modifier = Modifier.padding(vertical = 24.dp),
                            text = if (forwardQuery.isBlank()) {
                                "No chats to forward to"
                            } else {
                                "No chats match \"$forwardQuery\""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        LazyVerticalGrid(
                            modifier = Modifier.fillMaxWidth(),
                            columns = GridCells.Adaptive(minSize = 92.dp)
                        ) {
                            items(count = filteredForwardTargets.size) { index ->
                                val target = filteredForwardTargets[index]

                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            forwardingMessageIds = null
                                            forwardMessages(messageIds, target.providerDialogId)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (target.isSavedMessages) {
                                        SavedMessagesAvatar(size = 56.dp)
                                    } else {
                                        AvatarImage(
                                            avatar = target.avatar ?: Avatar.fromName(target.title),
                                            size = 56.dp
                                        )
                                    }

                                    Text(
                                        text = target.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    deleteConfirmationIds?.let { messageIds ->
        AlertDialog(
            onDismissRequest = { deleteConfirmationIds = null },
            containerColor = MaterialTheme.colorScheme.background,
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
            containerColor = MaterialTheme.colorScheme.background,
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

    if (showMainWebApp) {
        MainMiniAppDialog(
            session = session,
            providerDialogId = providerDialogId,
            title = dialog?.title ?: "Mini app",
            onDismiss = { showMainWebApp = false }
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

    // Attachments are reviewed in a blocking modal: the chat stays dimmed and
    // the composer can't be used until the message is sent or discarded.
    if (pendingAttachments.isNotEmpty()) {
        AttachmentReviewDialog(
            attachments = pendingAttachments,
            caption = input,
            isSending = isSending,
            isMobile = isMobileLayout,
            session = session,
            onCaptionChange = { value -> updateInput(value, TextRange(value.length)) },
            onInsertEmoji = { fallback, emojiId -> appendToComposer(fallback, emojiId) },
            onAddImages = { filePicker.pickImages() },
            onAddFiles = { filePicker.pickFiles() },
            onMove = { from, to -> pendingAttachments = pendingAttachments.moved(from, to) },
            onRemove = { index ->
                pendingAttachments = pendingAttachments.filterIndexed { itemIndex, _ ->
                    itemIndex != index
                }
            },
            onDiscard = {
                pendingAttachments = emptyList()
                clearComposer()
                showAttachmentMenu = false
            },
            onSend = onSend
        )
    }
}
