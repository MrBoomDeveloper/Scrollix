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
import androidx.compose.ui.geometry.Rect
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
internal fun DialogChatLayout(
    ui: DialogScreenState,
    accountId: String,
    providerDialogId: String,
    onOpenProfile: () -> Unit,
    onOpenUserProfile: (Long) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onBack: () -> Unit,
    isTwoPaneWindow: Boolean,
    session: AccountSession?,
    dialogs: List<Dialog>,
    folders: List<AccountDialogFolder>,
    isConnected: Boolean,
    allSessions: Map<String, AccountSession>,
    history: MessageHistory,
    messages: List<ChatMessage>,
    displayMessages: List<ChatMessage>,
    threadRailItems: List<ChatMessage>,
    messagesById: Map<Long, ChatMessage>,
    gapRowId: String?,
    dialog: Dialog?,
    settings: AppSettings,
    appWallpaper: ImageBitmap?,
    wallpaperImage: ImageBitmap?,
    scope: CoroutineScope,
    clipboard: ClipboardManager,
    mediaRecorder: MediaRecordingController,
    searchQuery: TextFieldState,
    composerInset: Dp,
    emojiPickerAnchor: MutableState<Rect?>,
    showHoverPicker: MutableState<Boolean>,
    emojiButtonInteraction: MutableInteractionSource,
    messageListState: DialogMessageListState,
    listState: LazyListState,
    filePicker: FilePicker,
    isMobileLayout: Boolean,
    doubleTapReaction: MessageReactionOption?,
    visibleMessages: List<ChatMessage>,
    insertIntoComposer: (String, Long?) -> Unit,
    addAttachments: (List<PickedFile>, Boolean) -> Unit,
    deleteMessages: (LongArray) -> Unit,
    requestDelete: (LongArray) -> Unit,
    forwardMessages: (LongArray, String) -> Unit,
    pinMessage: (ChatMessage, Boolean) -> Unit,
    applyReaction: (ChatMessage, MessageReactionOption) -> Unit,
    send: () -> Unit,
    cancelPendingMessage: (Long) -> Unit,
    sendRecording: (RecordedMedia) -> Unit,
    stopRecording: () -> Unit,
    cancelRecording: () -> Unit,
    beginRecording: (MediaRecordingKind) -> Unit,
) = with(ui) {
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
            Row(
                modifier = Modifier
                    .clickable(onClick = onOpenProfile)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if(!isTwoPaneWindow) {
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
                    SharedProfileElement(
                        key = "profile:$accountId:$providerDialogId"
                    ) {
                        AvatarImage(
                            avatar = dialog?.avatar ?: Avatar.fromName(dialog?.title ?: "Dialog"),
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

                    dialog?.let {
                        if(it.isSavedMessages) {
                            return@let
                        }
                    
                        val typingText = it.typingText
                        // A stale "online" is misleading while the transport is
                        // reconnecting, so the connection state wins over it.
                        var statusText = if (!isConnected) {
                            "Connecting..."
                        } else {
                            typingText ?: it.statusText
                        }
                        val memberCount = it.memberCount
                    
                        if(it.kind == DialogKind.Bot && typingText == null) {
                            statusText = null
                        }

                        Text(
                            text = statusText ?: when (it.kind) {
                                DialogKind.Direct -> if (it.isSavedMessages) {
                                    "Saved Messages"
                                } else {
                                    "Direct message"
                                }
                                DialogKind.Group -> memberCount?.let { count ->
                                    "${formatCount(count)} ${if (count == 1) "member" else "members"}"
                                } ?: "Group"

                                DialogKind.Channel -> memberCount?.let { count ->
                                    "${formatCount(count)} ${if (count == 1) "subscriber" else "subscribers"}"
                                } ?: "Channel"

                                DialogKind.Bot -> "Bot"
                                DialogKind.Secret -> "Secret chat"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                            color = if (!isConnected || typingText != null || statusText == "online") {
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

                // Forums keep every conversation in a topic of its own, so the
                // chat header opens the topic list instead of mixing them.
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
                    dialog?.kind == DialogKind.Direct &&
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceContainer
            )

            val activeLiveLocation = displayMessages.lastOrNull { message ->
                message.isOutgoing && message.location?.isLive == true
            }

            activeLiveLocation?.let { liveMessage ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .clickable {
                            searchOpen = false
                            searchQuery.clearText()
                            scrollRequest = ScrollRequest.Message(liveMessage.messageId)
                        }
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sharing live location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Tap to open the message",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    TextButton(
                        onClick = {
                            val currentSession = session

                            if (currentSession != null) {
                                scope.launch {
                                    currentSession.stopLiveLocation(
                                        providerDialogId = providerDialogId,
                                        messageId = liveMessage.messageId
                                    ).onFailure { failure ->
                                        error = failure.message
                                            ?: "Failed to stop sharing the location"
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Stop")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }

            dialog?.pinnedMessageId?.let { pinnedId ->
                val pinnedMessage = messages.firstOrNull { it.messageId == pinnedId }
                    ?: loadedPinnedMessage?.takeIf { it.messageId == pinnedId }
                val preview = pinnedMessage?.text?.takeIf { it.isNotBlank() }
                    ?: pinnedMessage?.media?.fileName
                    ?: "Pinned message"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .clickable {
                            searchOpen = false
                            searchQuery.clearText()
                            scrollRequest = ScrollRequest.Message(pinnedId)
                        }
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pinned message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { showUnpinConfirmation = true }) {
                        Icon(
                            tint = MaterialTheme.colorScheme.secondary,
                            imageVector = close_icon,
                            contentDescription = "Unpin message"
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
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
                    
                        placeholder = {
                            Text("Search in chat")
                        }
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
                val wallpaper = dialog?.wallpaper
                val customWallpaper = wallpaperImage ?: appWallpaper
                val hasFill = wallpaper != null && wallpaper.colors.isNotEmpty()

                // Pattern wallpapers are drawn over the gradient fill with the
                // intensity from the server, photo wallpapers are drawn as is.
                if (hasFill) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(wallpaperBrush(wallpaper))
                    )
                } else if (customWallpaper == null) {
                    Image(
                        modifier = Modifier.matchParentSize(),
                        painter = painterResource(Res.drawable.wallpaper),
                        contentScale = ContentScale.Crop,
                        contentDescription = null
                    )
                }

                if (customWallpaper != null) {
                    val isPattern = wallpaper?.isPattern == true

                    Image(
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(
                                if (isPattern) {
                                    (wallpaper.intensity / 100f).coerceIn(0f, 1f)
                                } else {
                                    1f
                                }
                            ),
                        bitmap = customWallpaper,
                        contentScale = ContentScale.Crop,
                        contentDescription = null
                    )
                }
            
                Column(Modifier.fillMaxSize()) {
                    val selectedMessageCount = displayMessages.count { message ->
                        message.allMessageIds.any { id -> id in selectedMessageIds }
                    }
                    // Only loaded rows can be selected: history is paged, so
                    // "all" always means everything currently in the list.
                    val allLoadedSelected = displayMessages.isNotEmpty() &&
                        displayMessages.all { message ->
                            message.allMessageIds.all { id -> id in selectedMessageIds }
                        }

                    AnimatedVisibility(visible = selectedMessageIds.isNotEmpty()) {
                        val selectedMessages = displayMessages.filter { message ->
                            message.allMessageIds.any { id -> id in selectedMessageIds }
                        }
                        val canForwardSelection = selectedMessages.isNotEmpty() &&
                            selectedMessages.all { message -> message.canBeForwarded }
                        val canDeleteSelection = selectedMessages.isNotEmpty() &&
                            selectedMessages.all { message ->
                                message.canBeDeleted || dialog?.canDeleteMessages == true
                            }
                        val copyText = selectedMessageText(selectedMessages)

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

                            if (copyText.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(copyText))
                                        selectedMessageIds = emptySet()
                                    }
                                ) {
                                    Text("Copy")
                                }
                            }

                            if (canForwardSelection) {
                                TextButton(
                                    onClick = {
                                        forwardingMessageIds = selectedMessageIds.toLongArray()
                                    }
                                ) {
                                    Text("Forward")
                                }
                            }

                            if (canDeleteSelection) {
                                TextButton(
                                    onClick = {
                                        requestDelete(selectedMessageIds.toLongArray())
                                    }
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
            onRequestDelete = { ids -> requestDelete(ids) },
            onPinMessage = { message, pinned -> pinMessage(message, pinned) },
            onApplyReaction = { message, option -> applyReaction(message, option) },
            onCancelPendingMessage = { id -> cancelPendingMessage(id) },
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
                    insertIntoComposer = insertIntoComposer,
                    addAttachments = addAttachments,
                    stopRecording = stopRecording,
                    cancelRecording = cancelRecording,
                    beginRecording = beginRecording,
                    send = send,
                    sendRecording = sendRecording
                )
            },
        )

                }
            }
        }
}
