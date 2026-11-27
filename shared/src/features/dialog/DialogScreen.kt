package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.openDialer
import com.mrboomdev.scrollix.platform.PlatformBackHandler
import com.mrboomdev.scrollix.platform.files.rememberFilePicker
import com.mrboomdev.scrollix.platform.media.MediaRecordingKind
import com.mrboomdev.scrollix.platform.media.rememberMediaRecorder
import com.mrboomdev.scrollix.data.storage.DialogScrollPositionStore
import com.mrboomdev.scrollix.features.home.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The chat screen: header, banners, search, message list, composer and every
 * dialog the chat opens. Its state and every mutation live in
 * [DialogScreenViewModel]; this file only lays them out and forwards gestures.
 */
@Composable
internal fun DialogScreen(
    accountId: String,
    providerDialogId: String,
    initialMessageId: Long? = null,
    onOpenProfile: () -> Unit = {},
    onOpenUserProfile: (Long) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: DialogScreenViewModel = viewModel(key = "$accountId:$providerDialogId") {
        DialogScreenViewModel(accountId = accountId, providerDialogId = providerDialogId)
    }
) {
    val isTwoPaneWindow = isTwoPaneLayout()

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val mediaRecorder = rememberMediaRecorder()
    val filePicker = rememberFilePicker(
        onImagesPicked = { picked -> viewModel.addAttachments(picked, asPhoto = true) },
        onFilesPicked = { picked -> viewModel.addAttachments(picked, asPhoto = false) }
    )

    val composerInset = with(LocalDensity.current) { viewModel.composerHeightPx.toDp() }
    val emojiPickerAnchor = remember { mutableStateOf<Rect?>(null) }
    val showHoverPicker = remember { mutableStateOf(false) }
    val emojiButtonInteraction = remember { MutableInteractionSource() }
    val emojiPickerInteraction = remember { MutableInteractionSource() }
    val isEmojiButtonHovered by emojiButtonInteraction.collectIsHoveredAsState()
    val isEmojiPickerHovered by emojiPickerInteraction.collectIsHoveredAsState()

    val isMobileLayout = currentAdaptiveLayoutInfo().size < AdaptiveLayoutSize.EXPANDED

    LaunchedEffect(viewModel) {
        viewModel.begin()
    }

    // Leaving the screen must not leave the microphone running.
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelRecording() }
    }

    // Navigation is frozen for the whole recording.
    PlatformBackHandler(enabled = viewModel.isRecording) {
        // Consume back while a note is being recorded.
    }

    // Starting is deferred by a frame (and a short pause for video) so the
    // circular preview has attached its surface before MediaRecorder prepares
    // the camera. Without it the preview would stay black.
    LaunchedEffect(viewModel.isRecording, viewModel.recordingKind) {
        if (!viewModel.isRecording) return@LaunchedEffect
        if (viewModel.recordingKind == MediaRecordingKind.VideoNote) withFrameNanos { }
        viewModel.startRecording(mediaRecorder)
    }

    val messageListState = rememberDialogMessageListState(
        providerDialogId = providerDialogId,
        history = viewModel.history,
        displayMessages = viewModel.displayMessages,
        initialMessageId = initialMessageId,
        savedPosition = remember(accountId, providerDialogId) {
            DialogScrollPositionStore.get(accountId, providerDialogId)
        },
        trackPosition = viewModel.searchQuery.text.isBlank(),
        loadOlder = { viewModel.loadOlderMessages() },
        loadBeginning = { viewModel.loadBeginning() },
        loadUntilLoaded = { messageId -> viewModel.loadUntilLoaded(messageId) },
        loadAround = { messageId -> viewModel.loadAround(messageId) },
        loadLatest = { viewModel.loadLatest() },
        onPositionChanged = { position ->
            DialogScrollPositionStore.save(accountId, providerDialogId, position)
        },
        onError = { throwable ->
            viewModel.error = throwable.message ?: "Failed to load messages"
        }
    )

    // Search results and history have different item counts and keys. Sharing
    // one LazyListState applied the history index to the search list and vice
    // versa, which is what made switching modes jump.
    val searchListState = rememberLazyListState()
    val listState = if (viewModel.searchQuery.text.isBlank()) {
        messageListState.listState
    } else {
        searchListState
    }

    // All scrolling intents go through one queue. Waiting for the search field
    // to be cleared guarantees that the LazyColumn already shows the full
    // history again before a jump starts animating.
    //
    // The jump runs in the screen scope on purpose: clearing the request
    // restarts this effect, and a restart cancels its body — which would stop
    // the jump animation on its first frame and make the buttons do nothing.
    LaunchedEffect(viewModel.scrollRequest, viewModel.searchQuery.text) {
        if (viewModel.searchQuery.text.isNotBlank()) return@LaunchedEffect

        val request = viewModel.takeScrollRequest() ?: return@LaunchedEffect

        scope.launch {
            when (request) {
                is ScrollRequest.Message -> {
                    if (!messageListState.jumpToMessage(request.messageId)) {
                        viewModel.error = "Couldn't load the message"
                    }
                }

                ScrollRequest.Latest -> messageListState.jumpToLatest()
                ScrollRequest.First -> messageListState.jumpToFirstMessage()
            }
        }
    }

    // The picker may float over the chat on the wide layout; give the pointer
    // time to travel from the button into the popup before closing it.
    LaunchedEffect(isTwoPaneWindow, isEmojiButtonHovered, isEmojiPickerHovered) {
        if (!isTwoPaneWindow) {
            showHoverPicker.value = false
            return@LaunchedEffect
        }

        if (isEmojiButtonHovered || isEmojiPickerHovered) {
            showHoverPicker.value = true
        } else {
            delay(200.milliseconds)
            showHoverPicker.value = false
        }
    }

    with(viewModel) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .imePadding()
        ) {
            DialogHeader(
                accountId = accountId,
                providerDialogId = providerDialogId,
                isTwoPaneWindow = isTwoPaneWindow,
                isMobileLayout = isMobileLayout,
                onOpenProfile = onOpenProfile,
                onBack = onBack,
                ui = viewModel
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            activeLiveLocation?.let { liveMessage ->
                LiveLocationBanner(
                    onOpen = {
                        searchOpen = false
                        searchQuery.clearText()
                        scrollRequest = ScrollRequest.Message(liveMessage.messageId)
                    },
                    onStop = { stopLiveLocation(liveMessage.messageId) }
                )
            }

            dialog?.pinnedMessageId?.let { pinnedId ->
                PinnedMessageBanner(
                    preview = pinnedPreview,
                    onOpen = {
                        searchOpen = false
                        searchQuery.clearText()
                        scrollRequest = ScrollRequest.Message(pinnedId)
                    },
                    onUnpin = { showUnpinConfirmation = true }
                )
            }

            DialogSearchBar(ui = viewModel)

            // Translating a chat fans out into one request per message; the
            // row shows how far it got and lets the user stop early.
            translationProgress?.takeIf { isTranslatingChat }?.let { progress ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "Translating ${progress.done} of ${progress.total}…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    TextButton(onClick = { cancelChatTranslation() }) {
                        Text("Cancel")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                DialogChatBackground(
                    wallpaper = dialog?.wallpaper,
                    wallpaperImage = wallpaperImage,
                    appWallpaper = appWallpaper
                )

                Column(Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = selectedMessageIds.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = "$selectedMessageCount selected",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (selectionCopyText.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(selectionCopyText))
                                        selectedMessageIds = emptySet()
                                    }
                                ) {
                                    Text("Copy")
                                }
                            }

                            if (canForwardSelection) {
                                TextButton(
                                    onClick = {
                                        openForwardPicker(selectedMessageIds.toLongArray())
                                    }
                                ) {
                                    Text("Forward")
                                }
                            }

                            if (canDeleteSelection) {
                                TextButton(
                                    onClick = { requestDelete(selectedMessageIds.toLongArray()) }
                                ) {
                                    Text("Delete")
                                }
                            }

                            IconButton(onClick = { selectedMessageIds = emptySet() }) {
                                Icon(
                                    imageVector = close_icon,
                                    contentDescription = "Cancel selection"
                                )
                            }
                        }
                    }

                    DialogMessageArea(
                        ui = viewModel,
                        providerDialogId = providerDialogId,
                        onOpenUserProfile = onOpenUserProfile,
                        onOpenHashtag = onOpenHashtag,
                        session = session,
                        history = history,
                        messages = messages,
                        dialog = dialog,
                        settings = settings,
                        scope = scope,
                        searchQuery = searchQuery,
                        composerInset = composerInset,
                        messageListState = messageListState,
                        listState = listState,
                        threadRailItems = threadRailItems,
                        messagesById = messagesById,
                        gapRowId = gapRowId,
                        isMobileLayout = isMobileLayout,
                        doubleTapReaction = doubleTapReaction,
                        visibleMessages = visibleMessages,
                        onRequestDelete = viewModel::requestDelete,
                        onPinMessage = viewModel::pinMessage,
                        onApplyReaction = viewModel::applyMessageReaction,
                        onCancelPendingMessage = viewModel::cancelPendingMessage,
                        composer = {
                            DialogComposer(
                                ui = viewModel,
                                providerDialogId = providerDialogId,
                                session = session,
                                allSessions = allSessions,
                                dialog = dialog,
                                scope = scope,
                                mediaRecorder = mediaRecorder,
                                filePicker = filePicker,
                                emojiPickerAnchor = emojiPickerAnchor,
                                showHoverPicker = showHoverPicker,
                                emojiButtonInteraction = emojiButtonInteraction,
                                insertIntoComposer = viewModel::insertIntoComposer,
                                addAttachments = viewModel::addAttachments,
                                stopRecording = viewModel::stopRecording,
                                cancelRecording = viewModel::cancelRecording,
                                beginRecording = viewModel::beginRecording,
                                send = { viewModel.sendMessage(keyboardController) },
                                sendRecording = viewModel::sendRecordedMedia
                            )
                        }
                    )
                }
            }
        }

        DialogChatOverlays(
            ui = viewModel,
            providerDialogId = providerDialogId,
            isMobileLayout = isMobileLayout,
            mediaRecorder = mediaRecorder,
            filePicker = filePicker,
            onSend = { viewModel.sendMessage(keyboardController) }
        )
    }
}

@Composable
private fun DialogHeader(
    accountId: String,
    providerDialogId: String,
    isTwoPaneWindow: Boolean,
    isMobileLayout: Boolean,
    onOpenProfile: () -> Unit,
    onBack: () -> Unit,
    ui: DialogScreenViewModel
) = with(ui) {
    Row(
        modifier = Modifier
            .clickable(onClick = onOpenProfile)
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isTwoPaneWindow) {
            IconButton(onClick = { if (!isRecording) onBack() }) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = arrow_back_icon,
                    contentDescription = "Back"
                )
            }
        }

        if (dialog?.isSavedMessages == true) {
            SavedMessagesAvatar(size = 40.dp)
        } else {
            AvatarImage(
                avatar = dialog?.avatar ?: com.mrboomdev.scrollix.data.model.Avatar.fromName(
                    dialog?.title ?: "Dialog"
                ),
                size = 40.dp
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = dialog?.title ?: "Dialog",
                    style = MaterialTheme.typography.titleMedium,
                    lineHeight = MaterialTheme.typography.titleMedium.fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (dialog?.isVerified == true) {
                    Icon(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                        imageVector = verified_icon,
                        contentDescription = "Verified"
                    )
                }

                dialog?.emojiStatusId?.let { emojiId ->
                    EmojiStatusIcon(
                        modifier = Modifier.padding(start = 4.dp),
                        emojiId = emojiId,
                        session = session
                    )
                }
            }

            headerStatus?.let { status ->
                Text(
                    text = status.text,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                    color = if (status.accent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }
        }

        IconButton(
            onClick = {
                searchOpen = !searchOpen

                if (!searchOpen) {
                    searchQuery.clearText()
                }
            }
        ) {
            Icon(
                tint = MaterialTheme.colorScheme.secondary,
                imageVector = search_icon,
                contentDescription = "Search in chat"
            )
        }

        // Forums keep every conversation in a topic of its own, so the chat
        // header opens the topic list instead of mixing them.
        if (dialog?.isForum == true) {
            IconButton(onClick = { showTopics = true }) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = chat_bubble_icon,
                    contentDescription = "Topics"
                )
            }
        }

        val callNumber = dialog?.phoneNumber

        // Saved Messages carries the user's own number, but it is not a chat
        // that can be called.
        if (
            isMobileLayout &&
            dialog?.isSavedMessages != true &&
            dialog?.kind == com.mrboomdev.scrollix.data.model.DialogKind.Direct &&
            !callNumber.isNullOrBlank()
        ) {
            IconButton(onClick = { openDialer(callNumber) }) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = call_icon,
                    contentDescription = "Call"
                )
            }
        }

        Box {
            IconButton(onClick = { showMoreMenu = true }) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = more_vertical_icon,
                    contentDescription = "Chat options"
                )
            }

            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                shape = RoundedCornerShape(16.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Scroll to first message") },
                    onClick = {
                        showMoreMenu = false
                        scrollRequest = ScrollRequest.First
                    }
                )

                if (supportsMarkAsUnread && dialog?.isSavedMessages != true) {
                    val isMarkedUnread = dialog?.isMarkedAsUnread == true

                    DropdownMenuItem(
                        text = {
                            Text(if (isMarkedUnread) "Mark as read" else "Mark as unread")
                        },
                        onClick = {
                            showMoreMenu = false
                            markDialogUnread(!isMarkedUnread)
                        }
                    )
                }

                if (session?.supportsScheduling == true && dialog?.isSavedMessages != true) {
                    DropdownMenuItem(
                        text = { Text("Scheduled messages") },
                        onClick = {
                            showMoreMenu = false
                            showScheduledMessages = true
                        }
                    )
                }

                if (
                    session?.supportsMessageAutoDelete == true &&
                    dialog?.isSavedMessages != true
                ) {
                    DropdownMenuItem(
                        text = { Text("Auto-delete messages") },
                        onClick = {
                            showMoreMenu = false
                            showAutoDeleteTime = true
                        }
                    )
                }

                if (session?.supportsTranslation == true) {
                    DropdownMenuItem(
                        text = { Text("Translate chat") },
                        onClick = {
                            showMoreMenu = false
                            showTranslationLanguage = true
                        }
                    )
                }

                if (hasTranslations) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (allTranslationsHidden) {
                                    "Show translations"
                                } else {
                                    "Hide translations"
                                }
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            setTranslationsVisible(allTranslationsHidden)
                        }
                    )
                }
            }
        }
    }
}
