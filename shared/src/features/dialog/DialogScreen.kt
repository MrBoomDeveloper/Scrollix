package com.mrboomdev.scrollix.features.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.input.key.key
import com.mrboomdev.scrollix.moved
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mrboomdev.scrollix.ui.components.MediaRecordingKind
import com.mrboomdev.scrollix.ui.components.RecordedMedia
import com.mrboomdev.scrollix.ui.components.RecordedMediaDialog
import com.mrboomdev.scrollix.ui.components.rememberMediaRecorder
import kotlinx.coroutines.delay
import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.outbox.OutboxAttachment
import com.mrboomdev.scrollix.data.outbox.OutboxEntryKind
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.UndiscoveredMessagesMessageId
import com.mrboomdev.scrollix.data.model.groupMessageAlbums
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DialogScrollPositionStore
import com.mrboomdev.scrollix.resources.Res
import com.mrboomdev.scrollix.resources.wallpaper
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.ChatAnimationPreview
import com.mrboomdev.scrollix.ui.components.ChatSticker
import com.mrboomdev.scrollix.ui.components.ChatVideoPreview
import com.mrboomdev.scrollix.ui.components.EmojiPicker
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.MessageGallery
import com.mrboomdev.scrollix.ui.components.MessageDatePill
import com.mrboomdev.scrollix.ui.components.ForumTopicsDialog
import com.mrboomdev.scrollix.ui.components.MessageThreadDialog
import com.mrboomdev.scrollix.ui.components.VideoNotePreview
import com.mrboomdev.scrollix.ui.components.MessageThreadRail
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.components.PlatformBackHandler
import com.mrboomdev.scrollix.ui.navigation.SharedProfileElement
import com.mrboomdev.scrollix.ui.components.PickedFile
import com.mrboomdev.scrollix.ui.components.ComposerInputRow
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.components.VoiceMessagePlayer
import com.mrboomdev.scrollix.ui.components.messageDate
import com.mrboomdev.scrollix.ui.components.readClipboardImages
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.openDialer
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.close_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DialogScreen(
    accountId: String,
    providerDialogId: String,
    initialMessageId: Long? = null,
    onOpenProfile: () -> Unit = {},
    onOpenUserProfile: (Long) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val isTwoPaneWindow = isTwoPaneLayout()
    val session = remember(accountId) { ApiManager.session(accountId) }
    val ui = remember(providerDialogId) { DialogScreenState() }

    with(ui) {
        val fallbackDialogs = remember { MutableStateFlow<List<Dialog>>(emptyList()) }
        val fallbackHistory = remember {
            MutableStateFlow(
                MessageHistory(
                    hasMoreOlder = false,
                    isLoadingInitial = false
                )
            )
        }

        val dialogs by (session?.dialogs ?: fallbackDialogs).collectAsState()
        val folders by ApiManager.folders.collectAsState()
        val connectionFlow = remember(session) {
            session?.isConnected ?: MutableStateFlow(true)
        }
        val isConnected by connectionFlow.collectAsState()
        val allSessions by ApiManager.sessions.collectAsState()
        val historyFlow = remember(session, providerDialogId) {
            session?.messageHistory(providerDialogId) ?: fallbackHistory
        }
        val history by historyFlow.collectAsState()
        val messages = history.messages
        // Queued text messages come from the persisted outbox, so a message sent
        // offline still has a row after switching chats or restarting the app.
        // Each entry keeps a negative local id that sorts after server ids.
        val outboxEntries by OutboxManager.entries.collectAsState()
        val pendingMessages = remember(
            outboxEntries,
            accountId,
            providerDialogId
        ) {
            outboxEntries
                .filter { entry ->
                    entry.accountId == accountId &&
                        entry.providerDialogId == providerDialogId &&
                        (
                            entry.kind == OutboxEntryKind.Text ||
                                entry.kind == OutboxEntryKind.Files
                            )
                }
                .map { entry ->
                    ChatMessage(
                        providerId = entry.providerId,
                        accountId = entry.accountId,
                        providerDialogId = entry.providerDialogId,
                        messageId = entry.localMessageId,
                        senderId = 0,
                        text = entry.pendingText,
                        timestamp = entry.createdAt,
                        isOutgoing = true,
                        customEmojis = entry.customEmojis,
                        replyTo = entry.replyToMessageId?.let { messageId ->
                            MessageReply(messageId = messageId)
                        },
                        sendState = if (entry.state == OutboxEntryState.Failed) {
                            MessageSendState.Failed
                        } else {
                            MessageSendState.Sending
                        }
                    )
                }
        }
        // After a jump to the first message the window is split: the beginning is
        // loaded, the previous window is retained as the far end, and one explicit
        // undiscovered row marks the range that is still missing. Grouping each
        // block separately keeps the placeholder exactly between them.
        val gapMessage = remember(
            messages,
            history.newerMessages,
            history.hasUnloadedGap
        ) {
            if (!history.hasUnloadedGap || history.newerMessages.isEmpty()) {
                null
            } else {
                val headNewest = messages.lastOrNull()
                val tailOldest = history.newerMessages.firstOrNull()

                ChatMessage(
                    providerId = messages.firstOrNull()?.providerId ?: "",
                    accountId = accountId,
                    providerDialogId = providerDialogId,
                    messageId = UndiscoveredMessagesMessageId,
                    senderId = 0,
                    text = "",
                    timestamp = if (headNewest != null && tailOldest != null) {
                        (headNewest.timestamp + tailOldest.timestamp) / 2
                    } else 0L,
                    isOutgoing = false,
                    isService = true
                )
            }
        }
        val displayMessages = remember(
            messages,
            pendingMessages,
            history.newerMessages,
            gapMessage
        ) {
            val head = messages.groupMessageAlbums()

            // While the middle is undiscovered the far end is not rendered at
            // all: the list ends at the placeholder, so a scroll can never jump
            // over the unloaded range into messages that happen to be loaded.
            // Filling the gap lazily merges the tail back in order.
            if (gapMessage != null) {
                head + gapMessage
            } else {
                head + (history.newerMessages + pendingMessages).groupMessageAlbums()
            }
        }
        // Thread roots of the loaded window, shown on the rail at the right edge.
        val threadRailItems = remember(messages, history.newerMessages) {
            (messages + history.newerMessages).filter { message -> message.replyCount > 0 }
        }
        // Reply lookups used to scan the whole window for every visible row.
        val messagesById = remember(messages, history.newerMessages) {
            (messages + history.newerMessages).associateBy { it.messageId }
        }
        val gapRowId = gapMessage?.rowId
        val dialog = dialogs.firstOrNull { it.providerDialogId == providerDialogId }
        val settings by AppSettingsStore.state.collectAsState()
        val appWallpaper = remember(settings.wallpaperUpdatedAt) {
            mutableStateOf<ImageBitmap?>(null)
        }

        val wallpaperImage = remember { mutableStateOf(WallpaperImageCache.last) }
        DialogWallpaperEffects(settings, dialog, session, appWallpaper, wallpaperImage)

        // Starts with the last decoded wallpaper, so even a fresh composition for
        // a new dialog doesn't flash the default one while the image loads.


        val scope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current
        val clipboard = LocalClipboardManager.current

        // The caret lives beside the text so an inserted emoji can move it past
        // the inserted run instead of leaving it in front of the sticker.
        val mediaRecorder = rememberMediaRecorder()

        // Navigation is frozen for the whole recording: leaving the chat would
        // release the recorder and silently drop the note being held.
        LaunchedEffect(isRecording) {
            DialogRecordingState.setRecording(isRecording)
        }

        DisposableEffect(Unit) {
            onDispose { DialogRecordingState.setRecording(false) }
        }

        PlatformBackHandler(enabled = isRecording) {
            // Consume back while a note is being recorded.
        }

        fun stopRecording() {
            if (!isRecording) return

            isRecording = false
            recordedMedia = if (recordingActive) mediaRecorder.stop() else null
            recordingActive = false
            recordingElapsedMs = 0L
        }

        fun cancelRecording() {
            if (!isRecording) return

            isRecording = false
            recordingActive = false
            mediaRecorder.cancel()
            recordedMedia = null
            recordingElapsedMs = 0L
        }

        fun beginRecording(kind: MediaRecordingKind) {
            if (isRecording) return

            recordingError = null
            recordedMedia = null
            recordingKind = kind
            recordingStartedAt = System.currentTimeMillis()
            recordingElapsedMs = 0L
            isRecording = true
        }

        // Starting is deferred by a frame (and a short pause for video) so the
        // circular preview has attached its surface before MediaRecorder prepares
        // the camera. Without it the preview would stay black.
        LaunchedEffect(isRecording, recordingKind) {
            if (!isRecording || recordingActive) return@LaunchedEffect

            if (recordingKind == MediaRecordingKind.VideoNote) {
                withFrameNanos { }
                delay(150L)
                if (!isRecording) return@LaunchedEffect
            }

            recordingStartedAt = System.currentTimeMillis()
            recordingElapsedMs = 0L
            recordingActive = mediaRecorder.start(recordingKind)

            if (!recordingActive) {
                isRecording = false
                recordingError = if (recordingKind == MediaRecordingKind.VideoNote) {
                    "Microphone or camera permission is required"
                } else {
                    "Microphone permission is required"
                }
            }
        }

        LaunchedEffect(isRecording, recordingKind, recordingStartedAt) {
            if (!isRecording) return@LaunchedEffect

            while (isRecording) {
                recordingElapsedMs = System.currentTimeMillis() - recordingStartedAt

                if (recordingKind == MediaRecordingKind.VideoNote &&
                    recordingElapsedMs >= 60_000L
                ) {
                    stopRecording()
                    break
                }

                delay(100L)
            }
        }

        // Drafts reset to an empty string programmatically, which never reaches
        // onValueChange, so their custom emoji spans are dropped here.
        LaunchedEffect(input) {
            if (input.isEmpty() && inputCustomEmojis.isNotEmpty()) {
                inputCustomEmojis = emptyList()
            }
        }

        // The composer renders custom emoji as inline stickers, so their assets
        // have to be loaded as soon as they are inserted into the draft.
        LaunchedEffect(session, inputCustomEmojis) {
            val currentSession = session ?: return@LaunchedEffect
            val missing = inputCustomEmojis
                .map { span -> span.emojiId }
                .distinct()
                .filter { id -> id !in inputEmojiData }

            if (missing.isEmpty()) return@LaunchedEffect

            val loaded = missing.mapNotNull { id ->
                currentSession.loadCustomEmoji(id)?.let { asset -> id to asset }
            }.toMap()

            if (loaded.isNotEmpty()) {
                inputEmojiData = inputEmojiData + loaded.mapValues { (_, asset) -> asset.data }
                inputEmojiAdaptive = inputEmojiAdaptive + loaded
                    .filterValues { asset -> asset.needsRepainting }
                    .keys
            }
        }
        /**
         * Inserts text at the caret and leaves the caret after it. Custom emoji
         * runs are tracked, so a sticker inserted mid-sentence stays where it was
         * put while the user keeps typing after it.
         */
        fun insertIntoComposer(text: String, emojiId: Long? = null) {
            val start = inputSelection.start.coerceIn(0, input.length)
            val end = inputSelection.end.coerceIn(start, input.length)
            val updated = input.replaceRange(start, end, text)

            inputCustomEmojis = adjustCustomEmojiSpans(
                oldText = input,
                newText = updated,
                spans = inputCustomEmojis
            ) + listOfNotNull(
                emojiId?.let { id ->
                    CustomEmojiSpan(
                        start = start,
                        end = start + text.length,
                        emojiId = id
                    )
                }
            )

            input = updated
            inputSelection = TextRange(start + text.length)
        }

        val searchQuery = rememberTextFieldState()
        // Only messages that this device sent may play the entrance animation,
        // and each one may play it exactly once. Both sets live outside the
        // LazyColumn so a row that is disposed and recomposed cannot replay it.
        val composerInset = with(LocalDensity.current) { composerHeightPx.toDp() }

        // The composer text is stored on Telegram's servers as a draft, so it is
        // restored when the chat is reopened on this or any other device.

        LaunchedEffect(session, providerDialogId) {
            val currentSession = session ?: return@LaunchedEffect

            currentSession.loadDraft(providerDialogId).onSuccess { draft ->
                hadServerDraft = draft.text.isNotEmpty() || draft.customEmojis.isNotEmpty()

                if (input.isEmpty() && editingMessage == null) {
                    input = draft.text
                    inputCustomEmojis = draft.customEmojis
                    inputSelection = TextRange(draft.text.length)
                }
            }

            draftRestored = true
        }

        LaunchedEffect(input, inputCustomEmojis, session, providerDialogId, editingMessage, draftRestored) {
            if (!draftRestored || editingMessage != null) return@LaunchedEffect

            // Never clear an unreadable draft just because the composer is empty.
            if (input.isEmpty() && inputCustomEmojis.isEmpty() && !hadServerDraft) {
                return@LaunchedEffect
            }

            // Debounce so a draft write doesn't race every keystroke.
            delay(600.milliseconds)
            session?.saveDraft(providerDialogId, input, inputCustomEmojis)
            hadServerDraft = input.isNotEmpty() || inputCustomEmojis.isNotEmpty()
        }
        val emojiPickerAnchor = remember { mutableStateOf<Rect?>(null) }
        val showHoverPicker = remember { mutableStateOf(false) }
        val emojiButtonInteraction = remember { MutableInteractionSource() }
        val emojiPickerInteraction = remember { MutableInteractionSource() }
        val isEmojiButtonHovered by emojiButtonInteraction.collectIsHoveredAsState()
        val isEmojiPickerHovered by emojiPickerInteraction.collectIsHoveredAsState()

        LaunchedEffect(isTwoPaneWindow, isEmojiButtonHovered, isEmojiPickerHovered) {
            if (!isTwoPaneWindow) {
                showHoverPicker.value = false
                return@LaunchedEffect
            }

            if (isEmojiButtonHovered || isEmojiPickerHovered) {
                showHoverPicker.value = true
            } else {
                // Give the pointer time to travel from the button into the popup.
                delay(200.milliseconds)
                showHoverPicker.value = false
            }
        }

        // The unread boundary is captured once per opened dialog. Marking the
        // messages read must not remove the header while the dialog stays open.

        LaunchedEffect(providerDialogId, dialog?.unreadCount) {
            if (unreadAtOpen == null && dialog != null) {
                unreadAtOpen = dialog.unreadCount
            }
        }

        LaunchedEffect(providerDialogId, unreadAtOpen, messages.size) {
            if (unreadAnchorResolved) return@LaunchedEffect

            val unread = unreadAtOpen ?: return@LaunchedEffect
            if (messages.isEmpty()) return@LaunchedEffect

            unreadAnchorResolved = true
            if (unread <= 0) return@LaunchedEffect

            // The unread tail starts this many messages before the newest one.
            val start = (messages.size - unread).coerceIn(0, messages.lastIndex)
            val anchorIndex = (start until messages.size).firstOrNull { index ->
                !messages[index].isOutgoing
            } ?: start

            // Keep the raw id: the album base can change when an earlier member
            // loads, but the raw id stays inside allMessageIds of the row.
            unreadAnchorMessageId = messages[anchorIndex].messageId
        }

        val messageListState = rememberDialogMessageListState(
            providerDialogId = providerDialogId,
            history = history,
            displayMessages = displayMessages,
            initialMessageId = initialMessageId,
            savedPosition = remember(accountId, providerDialogId) {
                DialogScrollPositionStore.get(accountId, providerDialogId)
            },
            trackPosition = searchQuery.text.isBlank(),
            loadOlder = {
                session?.loadOlderMessages(providerDialogId)
                    ?: Result.failure(IllegalStateException("Account session not found"))
            },
            loadBeginning = {
                session?.jumpToBeginning(providerDialogId)
                    ?: Result.failure(IllegalStateException("Account session not found"))
            },
            loadUntilLoaded = { messageId ->
                session?.loadMessagesUntil(providerDialogId, messageId)
                    ?: Result.failure(IllegalStateException("Account session not found"))
            },
            loadAround = { messageId ->
                session?.loadMessagesAround(providerDialogId, messageId)
                    ?: Result.failure(IllegalStateException("Account session not found"))
            },
            loadLatest = {
                session?.loadMessages(providerDialogId)
                    ?: Result.failure(IllegalStateException("Account session not found"))
            },
            onPositionChanged = { position ->
                DialogScrollPositionStore.save(accountId, providerDialogId, position)
            },
            onError = { throwable ->
                error = throwable.message ?: "Failed to load messages"
            }
        )
        // Search results and history have different item counts and keys. Sharing
        // one LazyListState applied the history index to the search list and vice
        // versa, which is what made switching modes jump.
        val searchListState = rememberLazyListState()
        val listState = if (searchQuery.text.isBlank()) {
            messageListState.listState
        } else {
            searchListState
        }

        fun addAttachments(picked: List<PickedFile>, asPhoto: Boolean) {
            if (picked.isEmpty() || editingMessage != null) return

            val free = (MaxAlbumAttachments - pendingAttachments.size).coerceAtLeast(0)
            if (free == 0) {
                error = "You can attach up to $MaxAlbumAttachments files"
                return
            }

            pendingAttachments = pendingAttachments + picked
                .take(free)
                .map { file -> PendingAttachment(file = file, asPhoto = asPhoto) }

            if (picked.size > free) {
                error = "Only $MaxAlbumAttachments files can be sent at once"
            }
        }

        val filePicker = rememberFilePicker(
            onImagesPicked = { picked -> addAttachments(picked, asPhoto = true) },
            onFilesPicked = { picked -> addAttachments(picked, asPhoto = false) }
        )

        fun deleteMessages(messageIds: LongArray) {
            if (session == null || messageIds.isEmpty()) return

            // Negative ids are queued local rows that never reached the server.
            // Cancel them instead of asking the provider to delete them.
            messageIds.filter { id -> id < 0 }.forEach { id ->
                OutboxManager.cancelByLocalMessageId(id)
            }

            val serverIds = messageIds.filter { id -> id > 0 }.toLongArray()
            if (serverIds.isEmpty()) {
                selectedMessageIds = selectedMessageIds - messageIds.toSet()
                return
            }

            val currentSession = session

            scope.launch {
                currentSession.deleteMessages(providerDialogId, serverIds)
                    .onSuccess {
                        selectedMessageIds = selectedMessageIds - messageIds.toSet()
                    }
                    .onFailure { failure ->
                        val exception = failure.asSessionException()

                        if (exception.retryable) {
                            // The server is unreachable; keep the deletion and
                            // deliver it from the outbox when the account is back.
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

        /** Every delete is confirmed first, because it reaches the other side. */
        fun requestDelete(messageIds: LongArray) {
            if (messageIds.isEmpty()) return

            deleteConfirmationIds = messageIds
        }

        fun forwardMessages(messageIds: LongArray, targetDialogId: String) {
            val currentSession = session ?: return

            scope.launch {
                currentSession.forwardMessages(
                    providerDialogId = providerDialogId,
                    messageIds = messageIds,
                    targetDialogId = targetDialogId
                ).onSuccess {
                    selectedMessageIds = emptySet()
                }.onFailure {
                    error = it.message ?: "Failed to forward messages"
                }
            }
        }

        fun pinMessage(message: ChatMessage, isPinned: Boolean) {
            if (session == null) return

            scope.launch {
                session.setMessagePinned(providerDialogId, message.messageId, isPinned)
                    .onFailure { error = it.message ?: "Failed to pin message" }
            }
        }

        LaunchedEffect(session, providerDialogId) {
            error = null

            if (session == null) {
                error = "Account session not found"
                return@LaunchedEffect
            }

            session.loadMessages(providerDialogId)
                .onFailure { error = it.message ?: "Failed to load messages" }
        }

        LaunchedEffect(session, providerDialogId) {
            session?.loadPinnedMessageId(providerDialogId)
        }

        LaunchedEffect(session, providerDialogId, dialog?.pinnedMessageId, messages) {
            val pinnedId = dialog?.pinnedMessageId

            if (pinnedId == null) {
                loadedPinnedMessage = null
                attemptedPinnedMessageId = null
                return@LaunchedEffect
            }

            if (messages.any { message -> pinnedId in message.allMessageIds }) {
                return@LaunchedEffect
            }
            if (loadedPinnedMessage?.messageId == pinnedId) return@LaunchedEffect
            if (attemptedPinnedMessageId == pinnedId) return@LaunchedEffect

            attemptedPinnedMessageId = pinnedId
            loadedPinnedMessage = session?.loadMessage(providerDialogId, pinnedId)?.getOrNull()
        }

        val lastMessageId = messages.lastOrNull()?.messageId

        LaunchedEffect(session, messages) {
            if (session == null) return@LaunchedEffect

            val ids = messages
                .flatMap { message ->
                    message.customEmojis.map { span -> span.emojiId } +
                        message.reactions.mapNotNull { reaction -> reaction.customEmojiId } +
                        message.poll?.options.orEmpty().flatMap { option ->
                            option.customEmojis.map { span -> span.emojiId }
                        }
                }
                .distinct()
                .filter { id -> id !in customEmojiData }

            if (ids.isEmpty()) return@LaunchedEffect

            val loaded = ids.mapNotNull { id ->
                session.loadCustomEmoji(id)?.let { asset -> id to asset }
            }.toMap()

            if (loaded.isNotEmpty()) {
                customEmojiData = customEmojiData + loaded.mapValues { (_, asset) -> asset.data }
                customEmojiAdaptive = customEmojiAdaptive + loaded
                    .filterValues { asset -> asset.needsRepainting }
                    .keys
            }
        }

        LaunchedEffect(session, providerDialogId, searchQuery.text) {
            val query = searchQuery.text.trim()

            if (query.isEmpty() || session == null) {
                searchResults = emptyList()
                isSearchPending = false
                return@LaunchedEffect
            }

            isSearchPending = true
            delay(300.milliseconds)
            searchResults = session.searchMessages(providerDialogId, query.toString())
                .getOrDefault(emptyList())
            isSearchPending = false
        }

        val isMobileLayout = currentAdaptiveLayoutInfo().size < AdaptiveLayoutSize.EXPANDED
        val doubleTapReaction = remember(
            settings.doubleTapReaction,
            settings.quickReactions,
            settings.pinnedCustomReactions
        ) {
            val preferred = settings.doubleTapReaction

            if (preferred != null) {
                MessageReactionOption(
                    emoji = preferred.emoji,
                    customEmojiId = preferred.customEmojiId
                )
            } else {
                settings.quickReactions.firstOrNull()?.let { emoji ->
                    MessageReactionOption(emoji)
                }
            }
        }

        val visibleMessages = remember(displayMessages, searchResults, searchQuery.text) {
            if (searchQuery.text.isBlank()) displayMessages else searchResults.groupMessageAlbums()
        }

        LaunchedEffect(session, providerDialogId, visibleMessages, messages, loadedReplies) {
            val currentSession = session ?: return@LaunchedEffect

            val missingIds = visibleMessages
                .mapNotNull { message -> message.replyTo?.messageId }
                .filter { id ->
                    messages.none { it.messageId == id } &&
                        id !in loadedReplies &&
                        id !in attemptedReplyIds
                }
                .distinct()

            if (missingIds.isEmpty()) return@LaunchedEffect

            attemptedReplyIds = attemptedReplyIds + missingIds

            val loaded = missingIds.mapNotNull { id ->
                currentSession.loadMessage(providerDialogId, id).getOrNull()
                    ?.let { message -> id to message }
            }.toMap()

            if (loaded.isNotEmpty()) {
                loadedReplies = loadedReplies + loaded
            }
        }

        // All scrolling intents go through one queue. Waiting for the search
        // field to be cleared here guarantees that the LazyColumn already shows
        // the full history again before a jump starts animating.
        //
        // The jump runs in the screen scope on purpose: clearing the request below
        // restarts this effect, and a restart cancels its body — which would stop
        // the jump animation on its first frame and make the buttons do nothing.
        LaunchedEffect(scrollRequest, searchQuery.text) {
            val request = scrollRequest ?: return@LaunchedEffect
            if (searchQuery.text.isNotBlank()) return@LaunchedEffect

            scrollRequest = null

            scope.launch {
                when (request) {
                    is ScrollRequest.Message -> {
                        if (!messageListState.jumpToMessage(request.messageId)) {
                            error = "Couldn't load the message"
                        }
                    }

                    ScrollRequest.Latest -> messageListState.jumpToLatest()
                    ScrollRequest.First -> messageListState.jumpToFirstMessage()
                }
            }
        }

        LaunchedEffect(lastMessageId) {
            if (messages.isEmpty()) return@LaunchedEffect

            session?.markMessagesRead(
                providerDialogId = providerDialogId,
                messageIds = messages
                    .takeLast(50)
                    .map { it.messageId }
                    .toLongArray()
            )
        }

        // A pending outgoing message is by definition one this device sent;
        // messages from another device arrive already marked as sent.
        LaunchedEffect(messages) {
            val pending = messages
                .filter { message ->
                    message.isOutgoing && message.sendState == MessageSendState.Sending
                }
                .flatMap { message -> message.allMessageIds }

            if (pending.isNotEmpty()) {
                locallySentMessageIds = (locallySentMessageIds + pending)
                    .toList()
                    .takeLast(200)
                    .toSet()
            }
        }

        fun applyReaction(message: ChatMessage, option: MessageReactionOption) {
            applyMessageReaction(message, option, session, providerDialogId, scope)
        }

        fun send() {
            sendMessage(session, providerDialogId, scope, keyboardController)
        }

        /** Drops a queued message that has not reached the server yet. */
        fun cancelPendingMessage(messageId: Long) {
            cancelQueuedMessage(messageId)
        }

        fun sendRecording(recording: RecordedMedia) {
            sendRecordedMedia(recording, session, providerDialogId, scope, mediaRecorder)
        }

        DialogScreenOverlays(
            ui = ui,
            accountId = accountId,
            session = session,
            dialog = dialog,
            providerDialogId = providerDialogId,
            settings = settings,
            folders = folders,
            dialogs = dialogs,
            scope = scope,
            mediaRecorder = mediaRecorder,
            filePicker = filePicker,
            isMobileLayout = isMobileLayout,
            onSend = ::send,
            onSendRecording = ::sendRecording,
            onDeleteMessages = ::deleteMessages,
            onForwardMessages = ::forwardMessages
        )

        DialogChatLayout(
            ui = ui,
            accountId = accountId,
            providerDialogId = providerDialogId,
            onOpenProfile = onOpenProfile,
            onOpenUserProfile = onOpenUserProfile,
            onOpenHashtag = onOpenHashtag,
            onBack = onBack,
            isTwoPaneWindow = isTwoPaneWindow,
            session = session,
            dialogs = dialogs,
            folders = folders,
            isConnected = isConnected,
            allSessions = allSessions,
            history = history,
            messages = messages,
            displayMessages = displayMessages,
            threadRailItems = threadRailItems,
            messagesById = messagesById,
            gapRowId = gapRowId,
            dialog = dialog,
            settings = settings,
            appWallpaper = appWallpaper.value,
            wallpaperImage = wallpaperImage.value,
            scope = scope,
            clipboard = clipboard,
            mediaRecorder = mediaRecorder,
            searchQuery = searchQuery,
            composerInset = composerInset,
            emojiPickerAnchor = emojiPickerAnchor,
            showHoverPicker = showHoverPicker,
            emojiButtonInteraction = emojiButtonInteraction,
            messageListState = messageListState,
            listState = listState,
            filePicker = filePicker,
            isMobileLayout = isMobileLayout,
            doubleTapReaction = doubleTapReaction,
            visibleMessages = visibleMessages,
            insertIntoComposer = ::insertIntoComposer,
            addAttachments = ::addAttachments,
            deleteMessages = ::deleteMessages,
            requestDelete = ::requestDelete,
            forwardMessages = ::forwardMessages,
            pinMessage = ::pinMessage,
            applyReaction = ::applyReaction,
            send = ::send,
            cancelPendingMessage = ::cancelPendingMessage,
            sendRecording = ::sendRecording,
            stopRecording = ::stopRecording,
            cancelRecording = ::cancelRecording,
            beginRecording = ::beginRecording,
        )
    }
}