package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.moved
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
import com.mrboomdev.scrollix.ui.components.StickerSetDialog
import com.mrboomdev.scrollix.ui.components.StickerSetTarget
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon
import com.mrboomdev.scrollix.ui.navigation.SharedProfileElement

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
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
    val ui = viewModel

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val mediaRecorder = rememberMediaRecorder()
    val filePicker = rememberFilePicker(
        onImagesPicked = { picked -> ui.addAttachments(picked, asPhoto = true) },
        onFilesPicked = { picked -> ui.addAttachments(picked, asPhoto = false) }
    )

    val composerInset = with(LocalDensity.current) { ui.composerHeightPx.toDp() }
    val emojiPickerAnchor = remember { mutableStateOf<Rect?>(null) }
    val showHoverPicker = remember { mutableStateOf(false) }
    val emojiButtonInteraction = remember { MutableInteractionSource() }
    val emojiPickerInteraction = remember { MutableInteractionSource() }
    val isEmojiButtonHovered by emojiButtonInteraction.collectIsHoveredAsState()
    val isEmojiPickerHovered by emojiPickerInteraction.collectIsHoveredAsState()

    val isMobileLayout = currentAdaptiveLayoutInfo().size < AdaptiveLayoutSize.EXPANDED

    LaunchedEffect(ui) {
        ui.begin()
    }

    // Leaving the screen must not leave the microphone running.
    DisposableEffect(ui) {
        onDispose { ui.cancelRecording() }
    }

    // Navigation is frozen for the whole recording.
    PlatformBackHandler(enabled = ui.isRecording) {
        // Consume back while a note is being recorded.
    }

    // Starting is deferred by a frame (and a short pause for video) so the
    // circular preview has attached its surface before MediaRecorder prepares
    // the camera. Without it the preview would stay black.
    LaunchedEffect(ui.isRecording, ui.recordingKind) {
        if (!ui.isRecording) return@LaunchedEffect
        if (ui.recordingKind == MediaRecordingKind.VideoNote) withFrameNanos { }
        ui.startRecording(mediaRecorder)
    }

    val messageListState = rememberDialogMessageListState(
        providerDialogId = providerDialogId,
        history = ui.history,
        displayMessages = ui.displayMessages,
        initialMessageId = initialMessageId,
        savedPosition = remember(accountId, providerDialogId) {
            DialogScrollPositionStore.get(accountId, providerDialogId)
        },
        trackPosition = ui.searchQuery.text.isBlank(),
        loadOlder = { ui.loadOlderMessages() },
        loadBeginning = { ui.loadBeginning() },
        loadUntilLoaded = { messageId -> ui.loadUntilLoaded(messageId) },
        loadAround = { messageId -> ui.loadAround(messageId) },
        loadLatest = { ui.loadLatest() },
        onPositionChanged = { position ->
            DialogScrollPositionStore.save(accountId, providerDialogId, position)
        },
        onError = { throwable ->
            ui.error = throwable.message ?: "Failed to load messages"
        }
    )

    // Search results and history have different item counts and keys. Sharing
    // one LazyListState applied the history index to the search list and vice
    // versa, which is what made switching modes jump.
    val searchListState = rememberLazyListState()
    val listState = if (ui.searchQuery.text.isBlank()) {
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
    LaunchedEffect(ui.scrollRequest, ui.searchQuery.text) {
        if (ui.searchQuery.text.isNotBlank()) return@LaunchedEffect

        val request = ui.takeScrollRequest() ?: return@LaunchedEffect

        scope.launch {
            when (request) {
                is ScrollRequest.Message -> {
                    if (!messageListState.jumpToMessage(request.messageId)) {
                        ui.error = "Couldn't load the message"
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

    with(ui) {
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
                ui = ui
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

            AnimatedVisibility(visible = searchOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = Dp.Unspecified),
                        state = searchQuery,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        contentPadding = PaddingValues(),
                        placeholder = { Text("Search in chat") }
                    )

                    if (searchQuery.text.isNotBlank()) {
                        Text(
                            text = if (isSearchPending) {
                                "Searching..."
                            } else {
                                "${visibleMessages.size}\nresults"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    IconButton(
                        onClick = {
                            searchOpen = false
                            searchQuery.clearText()
                        }
                    ) {
                        Icon(
                            imageVector = close_icon,
                            contentDescription = null
                        )
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

                            IconButton(
                                onClick = {
                                    selectedMessageIds = if (allLoadedSelected) {
                                        emptySet()
                                    } else {
                                        displayMessages
                                            .flatMap { message -> message.allMessageIds }
                                            .toSet()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = select_check_box_icon,
                                    contentDescription = if (allLoadedSelected) {
                                        "Clear selection"
                                    } else {
                                        "Select all loaded messages"
                                    }
                                )
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
                        ui = ui,
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
                        onRequestDelete = ui::requestDelete,
                        onPinMessage = ui::pinMessage,
                        onApplyReaction = ui::applyMessageReaction,
                        onCancelPendingMessage = ui::cancelPendingMessage,
                        composer = {
                            DialogComposer(
                                ui = ui,
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
                                insertIntoComposer = ui::insertIntoComposer,
                                addAttachments = ui::addAttachments,
                                stopRecording = ui::stopRecording,
                                cancelRecording = ui::cancelRecording,
                                beginRecording = ui::beginRecording,
                                send = { ui.sendMessage(keyboardController) },
                                sendRecording = ui::sendRecordedMedia
                            )
                        }
                    )
                }
            }
        }

        DialogChatOverlays(
            ui = ui,
            providerDialogId = providerDialogId,
            isMobileLayout = isMobileLayout,
            mediaRecorder = mediaRecorder,
            filePicker = filePicker,
            onSend = { ui.sendMessage(keyboardController) }
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
            SharedProfileElement(key = "profile:$accountId:$providerDialogId") {
                AvatarImage(
                    avatar = dialog?.avatar ?: com.mrboomdev.scrollix.data.model.Avatar.fromName(
                        dialog?.title ?: "Dialog"
                    ),
                    size = 40.dp
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SharedProfileElement(
                    key = "profile-name:$accountId:$providerDialogId",
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = dialog?.title ?: "Dialog",
                        style = MaterialTheme.typography.titleMedium,
                        lineHeight = MaterialTheme.typography.titleMedium.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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

        if (
            isMobileLayout &&
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
                    contentDescription = null
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
            }
        }
    }
}

@Composable
private fun DialogChatOverlays(
    ui: DialogScreenViewModel,
    providerDialogId: String,
    isMobileLayout: Boolean,
    mediaRecorder: com.mrboomdev.scrollix.platform.media.MediaRecordingController,
    filePicker: com.mrboomdev.scrollix.platform.files.FilePicker,
    onSend: () -> Unit
) = with(ui) {
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
                                            avatar = target.avatar ?: com.mrboomdev.scrollix.data.model.Avatar.fromName(
                                                target.title
                                            ),
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
                        deleteMessages(messageIds)
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
