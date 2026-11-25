package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.key
import com.mrboomdev.scrollix.ui.components.MainMiniAppDialog
import com.mrboomdev.scrollix.ui.components.MiniAppDialog
import com.mrboomdev.scrollix.ui.components.StickerSetDialog
import com.mrboomdev.scrollix.ui.components.StickerSetTarget
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mrboomdev.scrollix.ui.components.RecordedMedia
import com.mrboomdev.scrollix.ui.components.PollCreateDialog
import com.mrboomdev.scrollix.ui.components.LocationPickerDialog
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import kotlinx.coroutines.launch
import com.mrboomdev.scrollix.data.model.AccountDialogFolder
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.ui.components.FilePicker
import com.mrboomdev.scrollix.ui.components.MediaRecordingController
import com.mrboomdev.scrollix.ui.components.RecordedMediaDialog
import kotlinx.coroutines.CoroutineScope
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.moved
import com.mrboomdev.scrollix.ui.components.ForumTopicsDialog
import com.mrboomdev.scrollix.ui.components.MessageThreadDialog
import androidx.compose.ui.text.TextRange

@Composable
internal fun DialogScreenOverlays(
    ui: DialogScreenState,
    accountId: String,
    session: AccountSession?,
    dialog: Dialog?,
    scope: CoroutineScope,
    mediaRecorder: MediaRecordingController,
    filePicker: FilePicker,
    isMobileLayout: Boolean,
    providerDialogId: String,
    settings: AppSettings,
    folders: List<AccountDialogFolder>,
    dialogs: List<Dialog>,
    onSend: () -> Unit,
    onSendRecording: (RecordedMedia) -> Unit,
    onDeleteMessages: (LongArray) -> Unit,
    onForwardMessages: (LongArray, String) -> Unit,
): Unit = with(ui) {
    forwardingMessageIds?.let { messageIds ->
        // Only chats that accept messages can be a forwarding target, so MAX
        // channels the account cannot post into and read-only Telegram chats
        // are not offered.
        val forwardTargets = dialogs.filter { target ->
            target.canSendMessages && target.providerDialogId != providerDialogId
        }

        // The picker mirrors the home tabs: native server folders when that
        // setting is on, otherwise the built-in Direct/Groups/Channels/Bots
        // tabs. Without them a long chat list hides most destinations.
        val useNativeFolders = settings.useNativeFolders
        val forwardFolderOptions = remember(
            folders,
            accountId,
            useNativeFolders,
            forwardTargets
        ) {
            if (useNativeFolders) {
                buildList {
                    add("all" to "All")
                    folders
                        .filter { folder ->
                            folder.account.id == accountId &&
                                forwardTargets.any { target ->
                                    target.accountId == accountId &&
                                        folder.folder.id in target.folderIds
                                }
                        }
                        .forEach { folder ->
                            add(folder.key to folder.folder.name)
                        }
                }
            } else {
                listOf(
                    "all" to "All",
                    "direct" to "DM",
                    "groups" to "Groups",
                    "channels" to "Channels",
                    "bots" to "Bots"
                )
            }
        }
        var forwardFolderKey by remember(messageIds) { mutableStateOf("all") }
        var forwardQuery by remember(messageIds) { mutableStateOf("") }

        // The folder tabs still narrow a search: the query runs first, then the
        // selected folder, so a match in another folder is not offered.
        val filteredTargets = filterForwardTargets(forwardTargets, forwardQuery).filter { target ->
            when {
                forwardFolderKey == "all" -> true
                useNativeFolders -> {
                    val folder = folders.firstOrNull { it.key == forwardFolderKey }
                        ?: return@filter true
                    target.accountId == folder.account.id &&
                        folder.folder.id in target.folderIds
                }
                forwardFolderKey == "direct" ->
                    target.kind == DialogKind.Direct || target.kind == DialogKind.Secret
                forwardFolderKey == "groups" -> target.kind == DialogKind.Group
                forwardFolderKey == "channels" -> target.kind == DialogKind.Channel
                forwardFolderKey == "bots" -> target.kind == DialogKind.Bot
                else -> true
            }
        }

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
                        onValueChange = { value -> forwardQuery = value },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Search chats") },
                        leadingIcon = {
                            Icon(
                                imageVector = search_icon,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (forwardQuery.isNotEmpty()) {
                                IconButton(onClick = { forwardQuery = "" }) {
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
                                    onClick = { forwardFolderKey = key },
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }
                    }

                    if(filteredTargets.isEmpty()) {
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
                            items(count = filteredTargets.size) { index ->
                                val target = filteredTargets[index]

                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            forwardingMessageIds = null
                                            onForwardMessages(messageIds, target.providerDialogId)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if(target.isSavedMessages) {
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
                        "Delete for everyone?"
                    } else {
                        "Delete ${messageIds.size} messages for everyone?"
                    }
                )
            },
            text = {
                Text(
                    "The message disappears for every participant." +
                        " This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmationIds = null
                        onDeleteMessages(messageIds)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmationIds = null }) {
                    Text("Cancel")
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
                        val messageId = dialog?.pinnedMessageId

                        if (session != null && messageId != null) {
                            scope.launch {
                                session.setMessagePinned(
                                    providerDialogId = providerDialogId,
                                    messageId = messageId,
                                    isPinned = false
                                ).onFailure {
                                    error = it.message ?: "Failed to unpin message"
                                }
                            }
                        }
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
                val currentSession = session
                showPollDialog = false

                if (currentSession != null) {
                    val options = draft.options.filter { it.text.isNotBlank() }

                    scope.launch {
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
            }
        )
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            onDismiss = { showLocationDialog = false },
            onSend = { latitude, longitude, livePeriodSeconds ->
                val currentSession = session
                showLocationDialog = false

                if (currentSession != null) {
                    scope.launch {
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
            }
        )
    }

recordedMedia?.let { recording ->
    RecordedMediaDialog(
        recording = recording,
        isSending = isSendingRecording,
        error = recordingError,
        onDiscard = {
            mediaRecorder.release(recording)
            recordedMedia = null
            recordingError = null
        },
        onSend = { onSendRecording(recording) }
    )
}

threadRoot?.let { root ->
    val currentSession = session

    if (currentSession != null) {
        MessageThreadDialog(
            rootMessage = root,
            session = currentSession,
            onDismiss = { threadRoot = null }
        )
    }
}

if (showTopics) {
    val currentSession = session
    val currentDialog = dialog

    if (currentSession != null && currentDialog != null) {
        ForumTopicsDialog(
            session = currentSession,
            providerDialogId = providerDialogId,
            title = currentDialog.title,
            onDismiss = { showTopics = false }
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
        onCaptionChange = { value ->
            inputCustomEmojis = adjustCustomEmojiSpans(
                oldText = input,
                newText = value,
                spans = inputCustomEmojis
            )

            input = value
            inputSelection = TextRange(value.length)
        },
        onInsertEmoji = { fallback, emojiId ->
            val start = input.length
            input += fallback

            if (emojiId != null) {
                inputCustomEmojis = inputCustomEmojis + CustomEmojiSpan(
                    start = start,
                    end = start + fallback.length,
                    emojiId = emojiId
                )
            }
        },
        onAddImages = { filePicker.pickImages() },
        onAddFiles = { filePicker.pickFiles() },
        onMove = { from, to ->
            pendingAttachments = pendingAttachments.moved(from, to)
        },
        onRemove = { index ->
            pendingAttachments = pendingAttachments.filterIndexed {
                    itemIndex,
                    _ -> itemIndex != index
            }
        },
        onDiscard = {
            pendingAttachments = emptyList()
            input = ""
            inputSelection = TextRange.Zero
            inputCustomEmojis = emptyList()
            showAttachmentMenu = false
        },
        onSend = { onSend() }
    )
}

}