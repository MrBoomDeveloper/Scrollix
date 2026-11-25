package com.mrboomdev.scrollix.features.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.UndiscoveredMessagesMessageId
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.groupMessageAlbums
import com.mrboomdev.scrollix.data.outbox.OutboxAttachment
import com.mrboomdev.scrollix.data.outbox.OutboxEntryKind
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DialogScrollPositionStore
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.moved
import com.mrboomdev.scrollix.openDialer
import com.mrboomdev.scrollix.resources.Res
import com.mrboomdev.scrollix.resources.wallpaper
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.ChatAnimationPreview
import com.mrboomdev.scrollix.ui.components.ChatSticker
import com.mrboomdev.scrollix.ui.components.ChatVideoPreview
import com.mrboomdev.scrollix.ui.components.ComposerInputRow
import com.mrboomdev.scrollix.ui.components.EmojiPicker
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.ForumTopicsDialog
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.MediaRecordingController
import com.mrboomdev.scrollix.ui.components.MediaRecordingKind
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.components.MessageDatePill
import com.mrboomdev.scrollix.ui.components.MessageGallery
import com.mrboomdev.scrollix.ui.components.MessageThreadDialog
import com.mrboomdev.scrollix.ui.components.MessageThreadRail
import com.mrboomdev.scrollix.ui.components.PickedFile
import com.mrboomdev.scrollix.ui.components.PlatformBackHandler
import com.mrboomdev.scrollix.ui.components.RecordedMedia
import com.mrboomdev.scrollix.ui.components.RecordedMediaDialog
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.components.VideoNotePreview
import com.mrboomdev.scrollix.ui.components.VoiceMessagePlayer
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.components.messageDate
import com.mrboomdev.scrollix.ui.components.readClipboardImages
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.components.rememberMediaRecorder
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon
import com.mrboomdev.scrollix.ui.navigation.SharedProfileElement
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

internal fun DialogScreenState.applyMessageReaction(
    message: ChatMessage,
    option: MessageReactionOption,
    session: AccountSession?,
    providerDialogId: String,
    scope: CoroutineScope
) {
            val currentSession = session ?: return
            val isChosen = message.reactions.any { reaction ->
                reaction.isChosen && if (option.customEmojiId != null) {
                    reaction.customEmojiId == option.customEmojiId
                } else {
                    reaction.customEmojiId == null && reaction.emoji == option.emoji
                }
            }

            scope.launch {
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

internal fun DialogScreenState.sendMessage(
    session: AccountSession?,
    providerDialogId: String,
    scope: CoroutineScope,
    keyboardController: SoftwareKeyboardController?
) {
            val text = input.trim()
            val attachments = pendingAttachments
            val currentSession = session ?: return
            if (text.isEmpty() && attachments.isEmpty()) return
            if (editingMessage != null && attachments.isNotEmpty()) return

            keyboardController?.hide()

            val editing = editingMessage

            // Editing keeps its flow, and attachments keep the blocking review
            // modal; only a plain text message is shown optimistically.
            if (editing != null || attachments.isNotEmpty()) {
                scope.launch {
                    isSending = true
                    error = null

                    if (editing != null) {
                        val entry = OutboxManager.enqueueEdit(
                            providerId = currentSession.providerId,
                            accountId = currentSession.accountId,
                            providerDialogId = providerDialogId,
                            messageId = editing.messageId,
                            text = text,
                            customEmojis = inputCustomEmojis
                        )

                        input = ""
                        inputSelection = TextRange.Zero
                        inputCustomEmojis = emptyList()
                        editingMessage = null

                        OutboxManager.sendNow(entry.id).onFailure { failure ->
                            error = failure.message ?: "Failed to edit message"
                        }
                    } else {
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
                            replyToMessageId = replyingTo?.messageId,
                            customEmojis = inputCustomEmojis
                        )

                        input = ""
                        inputSelection = TextRange.Zero
                        inputCustomEmojis = emptyList()
                        pendingAttachments = emptyList()
                        replyingTo = null
                        scrollRequest = ScrollRequest.Latest

                        OutboxManager.sendNow(entry.id).onFailure { failure ->
                            error = failure.message ?: "Failed to send attachments"
                        }
                    }

                    isSending = false
                }

                return
            }

            // The outbox shows the row immediately and owns the retry loop, so
            // the composer is free as soon as the entry is persisted. Retryable
            // failures (offline, timeout, flood wait) stay queued and are retried
            // with backoff; only a permanent provider error marks the row failed.
            val spans = inputCustomEmojis
            val replyToMessageId = replyingTo?.messageId

            input = ""
            inputSelection = TextRange.Zero
            inputCustomEmojis = emptyList()
            replyingTo = null
            error = null
            scrollRequest = ScrollRequest.Latest

            scope.launch {
                val entry = OutboxManager.enqueue(
                    providerId = currentSession.providerId,
                    accountId = currentSession.accountId,
                    providerDialogId = providerDialogId,
                    text = text,
                    replyToMessageId = replyToMessageId,
                    customEmojis = spans
                )

                OutboxManager.sendNow(entry.id).onFailure { failure ->
                    error = failure.message ?: "Failed to send message"
                }
            }
}

/** Drops a queued message that has not reached the server yet. */
internal fun cancelQueuedMessage(messageId: Long) {
    OutboxManager.cancelByLocalMessageId(messageId)
}

internal fun DialogScreenState.sendRecordedMedia(
    recording: RecordedMedia,
    session: AccountSession?,
    providerDialogId: String,
    scope: CoroutineScope,
    mediaRecorder: MediaRecordingController
) {
    val currentSession = session ?: return
    if (isSendingRecording) return

    isSendingRecording = true
    recordingError = null

    scope.launch {
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
            mediaRecorder.release(recording)
            recordedMedia = null
            scrollRequest = ScrollRequest.Latest
        }.onFailure { failure ->
            // Keep the review dialog open so the note can be retried.
            recordingError = failure.message ?: "Failed to send the recording"
            recordedMedia = recording
        }
    }
}
