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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import com.mrboomdev.scrollix.isDesktopPlatform
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogFolder
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessagePollInfo
import com.mrboomdev.scrollix.data.model.MessagePollOptionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.UndiscoveredMessagesMessageId
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.groupMessageAlbums
import com.mrboomdev.scrollix.data.outbox.OutboxAttachment
import com.mrboomdev.scrollix.data.outbox.OutboxEntryKind
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DialogScrollPosition
import com.mrboomdev.scrollix.data.storage.DialogScrollPositionStore
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
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
import com.mrboomdev.scrollix.ui.components.CustomEmojiMessageText
import com.mrboomdev.scrollix.ui.components.EmojiPicker
import com.mrboomdev.scrollix.ui.components.EmojiPickerDialog
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.FilePicker
import com.mrboomdev.scrollix.ui.components.FolderEmojiImage
import com.mrboomdev.scrollix.ui.components.ForumTopicsDialog
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.LocationPickerDialog
import com.mrboomdev.scrollix.ui.components.MainMiniAppDialog
import com.mrboomdev.scrollix.ui.components.MediaRecordingController
import com.mrboomdev.scrollix.ui.components.MediaRecordingKind
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.components.MessageDatePill
import com.mrboomdev.scrollix.ui.components.MessageGallery
import com.mrboomdev.scrollix.ui.components.MessageThreadDialog
import com.mrboomdev.scrollix.ui.components.MessageThreadRail
import com.mrboomdev.scrollix.ui.components.MiniAppDialog
import com.mrboomdev.scrollix.ui.components.PickedFile
import com.mrboomdev.scrollix.ui.components.PlatformBackHandler
import com.mrboomdev.scrollix.ui.components.PollCreateDialog
import com.mrboomdev.scrollix.ui.components.RecordedMedia
import com.mrboomdev.scrollix.ui.components.RecordedMediaDialog
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.components.SpoilerOverlay
import com.mrboomdev.scrollix.ui.components.StickerSetDialog
import com.mrboomdev.scrollix.ui.components.StickerSetTarget
import com.mrboomdev.scrollix.ui.components.VideoNotePreview
import com.mrboomdev.scrollix.ui.components.VoiceMessagePlayer
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.components.loadingBlur
import com.mrboomdev.scrollix.ui.components.mediaSpoilerBlur
import com.mrboomdev.scrollix.ui.components.messageDate
import com.mrboomdev.scrollix.ui.components.readClipboardImages
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.components.rememberMediaRecorder
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon
import com.mrboomdev.scrollix.ui.navigation.SharedProfileElement
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun BoxScope.DialogComposer(
    ui: DialogScreenState,
    providerDialogId: String,
    session: AccountSession?,
    allSessions: Map<String, AccountSession>,
    dialog: Dialog?,
    scope: CoroutineScope,
    mediaRecorder: MediaRecordingController,
    filePicker: FilePicker,
    emojiPickerAnchor: MutableState<Rect?>,
    emojiButtonInteraction: MutableInteractionSource,
    showHoverPicker: MutableState<Boolean>,
    insertIntoComposer: (String, Long?) -> Unit,
    addAttachments: (List<PickedFile>, Boolean) -> Unit,
    stopRecording: () -> Unit,
    cancelRecording: () -> Unit,
    beginRecording: (MediaRecordingKind) -> Unit,
    send: () -> Unit,
    sendRecording: (RecordedMedia) -> Unit
) = with(ui) {
    val inputFocusRequester = remember { FocusRequester() }

    // Desktop opens a chat ready for typing; mobile keeps its keyboard hidden
    // until the field is tapped.
    LaunchedEffect(providerDialogId) {
        if (!isDesktopPlatform()) return@LaunchedEffect

        withFrameNanos { }
        runCatching { inputFocusRequester.requestFocus() }
    }

            Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { composerHeightPx = it.height }
                ) {
            error?.let { message ->
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            editingMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "Editing: ${message.text.take(40)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    TextButton(
                        onClick = {
                            editingMessage = null
                            input = ""
                            inputSelection = TextRange.Zero
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }

            replyingTo?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "Replying to: " +
                            message.text.ifBlank { "message" }.take(40),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    TextButton(onClick = { replyingTo = null }) {
                        Text("Cancel")
                    }
                }
            }

            val emojiPickerContent: @Composable (Modifier) -> Unit = { pickerModifier ->
                if (session != null) {
                    EmojiPicker(
                        modifier = pickerModifier,
                        session = session,
                        onEmojiSelected = { emoji ->
                            insertIntoComposer(emoji, null)
                        },
                        onCustomEmojiSelected = { emojiId, fallback ->
                            insertIntoComposer(fallback, emojiId)
                        },
                        onStickerSelected = { sticker ->
                            showEmojiPicker = false
                            showHoverPicker.value = false

                            scope.launch {
                                session.sendSticker(providerDialogId, sticker)
                                    .onSuccess {
                                        scrollRequest = ScrollRequest.Latest
                                    }
                                    .onFailure {
                                        error = it.message ?: "Failed to send the sticker"
                                    }
                            }
                        },
                        otherSessions = allSessions.values.filter { other ->
                            other.accountId != session.accountId
                        },
                        onCrossProviderStickerSelected = { source, sticker ->
                            showEmojiPicker = false
                            showHoverPicker.value = false

                            scope.launch {
                                val bytes = source.loadMediaBytes(
                                    fileId = sticker.stickerFileId ?: sticker.fileId,
                                    uniqueId = sticker.stickerUniqueId
                                        ?: sticker.fileUniqueId
                                )

                                if (bytes == null) {
                                    error = "Could not load the sticker"
                                } else {
                                    session.sendStickerBytes(
                                        providerDialogId = providerDialogId,
                                        bytes = bytes,
                                        format = sticker.stickerFormat,
                                        width = sticker.width,
                                        height = sticker.height
                                    ).onSuccess {
                                        scrollRequest = ScrollRequest.Latest
                                    }.onFailure {
                                        error = it.message ?: "Failed to send the sticker"
                                    }
                                }
                            }
                        },
                        onAnimationSelected = { animation ->
                            showEmojiPicker = false
                            showHoverPicker.value = false

                            scope.launch {
                                session.sendAnimation(providerDialogId, animation)
                                    .onSuccess {
                                        scrollRequest = ScrollRequest.Latest
                                    }
                                    .onFailure {
                                        error = it.message ?: "Failed to send the GIF"
                                    }
                            }
                        },
                        onCrossProviderAnimationSelected = { source, animation ->
                            showEmojiPicker = false
                            showHoverPicker.value = false

                            scope.launch {
                                val bytes = source.loadMediaBytes(
                                    fileId = animation.fileId,
                                    uniqueId = animation.fileUniqueId
                                )

                                if (bytes == null) {
                                    error = "Could not load the GIF"
                                } else {
                                    session.sendAnimationBytes(
                                        providerDialogId = providerDialogId,
                                        bytes = bytes,
                                        mimeType = animation.mimeType,
                                        width = animation.width,
                                        height = animation.height,
                                        durationSeconds = animation.durationSeconds
                                    ).onSuccess {
                                        scrollRequest = ScrollRequest.Latest
                                    }.onFailure {
                                        error = it.message ?: "Failed to send the GIF"
                                    }
                                }
                            }
                        }
                    )
                }
            }

            if (dialog?.hasMainWebApp == true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {

                    Button(
                        onClick = { showMainWebApp = true },
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 6.dp
                        )
                    ) {
                        Text("Open")
                    }
                }
            }

            if (dialog?.canSendMessages == false) {
                NotSendableNotice()
            } else Column {
                // The composer slides away while recording, leaving a slim
                // bar with the elapsed time and a pause action.
                AnimatedVisibility(
                    visible = !isRecording,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                ComposerInputRow(
                    input = input,
                    inputSelection = inputSelection,
                    inputCustomEmojis = inputCustomEmojis,
                    inputEmojiData = inputEmojiData,
                    inputEmojiAdaptive = inputEmojiAdaptive,
                    isSending = isSending,
                    canAttach = editingMessage == null,
                    hasAttachments = pendingAttachments.isNotEmpty(),
                    showAttachmentMenu = showAttachmentMenu,
                    showEmojiPicker = showEmojiPicker,
                    videoMessageMode = videoMessageMode,
                    isRecording = isRecording,
                    emojiButtonInteraction = emojiButtonInteraction,
                    onAttachmentMenuChange = { expanded ->
                        showAttachmentMenu = expanded
                    },
                    onPickImages = { filePicker.pickImages() },
                    onPickFiles = { filePicker.pickFiles() },
                    onAddPoll = { showPollDialog = true },
                    onAddLocation = { showLocationDialog = true },
                    onEmojiAnchorChanged = { anchor -> emojiPickerAnchor.value = anchor },
                    onToggleEmojiPicker = {
                        showEmojiPicker = !showEmojiPicker
                    },
                    onInputChange = { value, selection ->
                        inputCustomEmojis = adjustCustomEmojiSpans(
                            oldText = input,
                            newText = value,
                            spans = inputCustomEmojis
                        )

                        input = value
                        inputSelection = selection
                    },
                    onPasteImages = {
                        val images = readClipboardImages()

                        if (images.isEmpty()) {
                            false
                        } else {
                            addAttachments(images, true)
                            true
                        }
                    },
                    onToggleVideoMessageMode = {
                        videoMessageMode = !videoMessageMode
                    },
                    onBeginRecording = { kind -> beginRecording(kind) },
                    onSend = send,
                    focusRequester = inputFocusRequester,
                    autoFocus = isDesktopPlatform()
                )
                }

                AnimatedVisibility(
                    visible = isRecording,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val seconds = recordingElapsedMs / 1000L

                        if (recordingKind == MediaRecordingKind.VideoNote) {
                            // Live circle preview of what the note captures.
                            VideoNotePreview(
                                controller = mediaRecorder,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                            )
                        }

                        Text(
                            text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.weight(1f))

                        TextButton(onClick = { cancelRecording() }) {
                            Text("Cancel")
                        }

                        FilledIconButton(
                            enabled = recordingActive && !isSendingRecording,
                            onClick = {
                                // Send straight from the recording bar; the
                                // review dialog only appears when sending
                                // fails and the note has to be retried.
                                stopRecording()
                                recordedMedia?.let { recorded ->
                                    sendRecording(recorded)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = send_icon,
                                contentDescription = "Send"
                            )
                        }
                    }
                }
            }

            // The emoji, sticker and GIF picker is part of the composer
            // layout on every screen size: it grows the composer instead
            // of floating over the chat and covering it.
            if (showEmojiPicker && session != null) {
                emojiPickerContent(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                )
            }
                }
}
