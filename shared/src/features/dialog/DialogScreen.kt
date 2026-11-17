package com.mrboomdev.scrollix.features.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.test.microphone_icon
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.resources.Res
import com.mrboomdev.scrollix.resources.wallpaper
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.ChatAnimationPreview
import com.mrboomdev.scrollix.ui.components.ChatSticker
import com.mrboomdev.scrollix.ui.components.ChatVideoPreview
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.MessageDatePill
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.components.PickedFile
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.components.VoiceMessagePlayer
import com.mrboomdev.scrollix.ui.components.messageDate
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.icons.add_icon
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.ui.icons.close_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

private fun wallpaperBrush(wallpaper: ChatWallpaper): Brush {
    val colors = wallpaper.colors.map { color ->
        Color(color or (0xFF shl 24))
    }

    return when (colors.size) {
        0 -> SolidColor(Color.Transparent)
        1 -> SolidColor(colors.first())
        else -> Brush.verticalGradient(colors)
    }
}

@Composable
fun DialogScreen(
    accountId: String,
    providerDialogId: String,
    onOpenProfile: () -> Unit = {},
    onOpenUserProfile: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val isTwoPaneWindow = isTwoPaneLayout()
    val session = remember(accountId) { ApiManager.session(accountId) }
    val fallbackDialogs = remember { MutableStateFlow<List<Dialog>>(emptyList()) }
    val fallbackMessages = remember { MutableStateFlow<List<ChatMessage>>(emptyList()) }

    val dialogs by (session?.dialogs ?: fallbackDialogs).collectAsState()
    val messagesFlow = remember(session, providerDialogId) {
        session?.messages(providerDialogId) ?: fallbackMessages
    }
    val messages by messagesFlow.collectAsState()
    val dialog = dialogs.firstOrNull { it.providerDialogId == providerDialogId }
    val settings by AppSettingsStore.state.collectAsState()
    var appWallpaper by remember(settings.wallpaperUpdatedAt) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(settings.hasCustomWallpaper, settings.wallpaperUpdatedAt) {
        appWallpaper = if (settings.hasCustomWallpaper) {
            val bytes = withContext(Dispatchers.IO) {
                AppSettingsStore.readWallpaper()
            }

            bytes?.let { data ->
                withContext(Dispatchers.Default) {
                    decodeImageBitmap(data)
                }
            }
        } else {
            null
        }
    }
    var wallpaperImage by remember(providerDialogId) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(dialog?.wallpaper, session) {
        val wallpaper = dialog?.wallpaper
        wallpaperImage = null

        if (wallpaper == null) return@LaunchedEffect

        val thumbnailFileId = wallpaper.thumbnailFileId

        if (thumbnailFileId != null) {
            val bytes = session?.loadFile(thumbnailFileId)?.getOrNull()

            if (bytes != null) {
                val image = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }

                if (image != null) wallpaperImage = image
            }
        }

        val fileId = wallpaper.fileId

        if (fileId != null && fileId != thumbnailFileId) {
            val bytes = session?.loadFile(fileId)?.getOrNull()

            if (bytes != null) {
                val image = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }

                // Animated wallpapers (video/TGS) can't be decoded as a bitmap,
                // in that case the thumbnail stays as the wallpaper.
                if (image != null) wallpaperImage = image
            }
        }
    }

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var input by remember(providerDialogId) { mutableStateOf("") }
    var error by remember(providerDialogId) { mutableStateOf<String?>(null) }
    var isLoading by remember(providerDialogId) { mutableStateOf(true) }
    var isSending by remember(providerDialogId) { mutableStateOf(false) }
    var isLoadingMore by remember(providerDialogId) { mutableStateOf(false) }
    var canLoadMore by remember(providerDialogId) { mutableStateOf(true) }
    var initialScrollDone by remember(providerDialogId) { mutableStateOf(false) }
    var previousLastMessageId by remember(providerDialogId) { mutableStateOf<Long?>(null) }
    var showNewMessages by remember(providerDialogId) { mutableStateOf(false) }
    var scrollToLatestRequested by remember(providerDialogId) { mutableStateOf(false) }
    var isAtBottom by remember(providerDialogId) { mutableStateOf(true) }
    var searchOpen by remember(providerDialogId) { mutableStateOf(false) }
    var searchQuery = rememberTextFieldState()
    var editingMessage by remember(providerDialogId) { mutableStateOf<ChatMessage?>(null) }
    var replyingTo by remember(providerDialogId) { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageIds by remember(providerDialogId) { mutableStateOf<Set<Long>>(emptySet()) }
    var forwardingMessageIds by remember(providerDialogId) {
        mutableStateOf<LongArray?>(null)
    }
    var showUnpinConfirmation by remember(providerDialogId) { mutableStateOf(false) }
    var loadedPinnedMessage by remember(providerDialogId) { mutableStateOf<ChatMessage?>(null) }
    var attemptedPinnedMessageId by remember(providerDialogId) { mutableStateOf<Long?>(null) }
    var loadedReplies by remember(providerDialogId) {
        mutableStateOf<Map<Long, ChatMessage>>(emptyMap())
    }
    var attemptedReplyIds by remember(providerDialogId) { mutableStateOf<Set<Long>>(emptySet()) }

    fun sendAttachment(picked: PickedFile, asPhoto: Boolean) {
        if (session == null) return

        keyboardController?.hide()
        scope.launch {
            isSending = true
            error = null

            session.sendFile(
                providerDialogId = providerDialogId,
                path = picked.path,
                asPhoto = asPhoto
            ).onSuccess {
                scrollToLatestRequested = true
            }.onFailure {
                error = it.message ?: "Failed to send file"
            }

            isSending = false
        }
    }

    val filePicker = rememberFilePicker(
        onImagePicked = { picked -> sendAttachment(picked, asPhoto = true) },
        onFilePicked = { picked -> sendAttachment(picked, asPhoto = false) }
    )

    fun deleteMessages(messageIds: LongArray) {
        if (session == null || messageIds.isEmpty()) return

        scope.launch {
            session.deleteMessages(providerDialogId, messageIds)
                .onSuccess {
                    selectedMessageIds = selectedMessageIds - messageIds.toSet()
                }
                .onFailure { error = it.message ?: "Failed to delete messages" }
        }
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
        isLoading = true
        isLoadingMore = false
        canLoadMore = true
        initialScrollDone = false
        previousLastMessageId = null
        showNewMessages = false
        scrollToLatestRequested = false
        isAtBottom = true

        if (session == null) {
            error = "Account session not found"
            isLoading = false
            return@LaunchedEffect
        }

        session.loadMessages(providerDialogId)
            .onFailure { error = it.message ?: "Failed to load messages" }
        isLoading = false
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

        if (messages.any { it.messageId == pinnedId }) return@LaunchedEffect
        if (loadedPinnedMessage?.messageId == pinnedId) return@LaunchedEffect
        if (attemptedPinnedMessageId == pinnedId) return@LaunchedEffect

        attemptedPinnedMessageId = pinnedId
        loadedPinnedMessage = session?.loadMessage(providerDialogId, pinnedId)?.getOrNull()
    }

    val lastMessageId = messages.lastOrNull()?.messageId

    var searchResults by remember(providerDialogId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isSearchPending by remember(providerDialogId) { mutableStateOf(false) }
    var customEmojiData by remember(providerDialogId) {
        mutableStateOf<Map<Long, ByteArray>>(emptyMap())
    }

    LaunchedEffect(session, messages) {
        if (session == null) return@LaunchedEffect

        val ids = messages
            .flatMap { message -> message.customEmojis }
            .map { span -> span.emojiId }
            .distinct()
            .filter { id -> id !in customEmojiData }

        if (ids.isEmpty()) return@LaunchedEffect

        val loaded = ids.mapNotNull { id ->
            session.loadCustomEmoji(id)?.let { bytes -> id to bytes }
        }.toMap()

        if (loaded.isNotEmpty()) {
            customEmojiData = customEmojiData + loaded
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

    val visibleMessages = if (searchQuery.text.isBlank()) messages else searchResults

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

    LaunchedEffect(providerDialogId, isLoading, messages.isNotEmpty()) {
        if (isLoading || messages.isEmpty() || initialScrollDone) return@LaunchedEffect
        if (searchQuery.text.isNotBlank()) return@LaunchedEffect

        listState.scrollToItem(messages.lastIndex)
        previousLastMessageId = messages.lastOrNull()?.messageId
        initialScrollDone = true
    }

    LaunchedEffect(listState, providerDialogId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()

            lastVisible != null && lastVisible.index >= info.totalItemsCount - 1
        }.collect { atBottom ->
            isAtBottom = atBottom

            if (atBottom) {
                showNewMessages = false
            }
        }
    }

    LaunchedEffect(lastMessageId, initialScrollDone, scrollToLatestRequested) {
        if (!initialScrollDone) return@LaunchedEffect
        if (searchQuery.text.isNotBlank()) return@LaunchedEffect

        val newLastMessageId = lastMessageId ?: return@LaunchedEffect

        if (scrollToLatestRequested) {
            scrollToLatestRequested = false
            previousLastMessageId = newLastMessageId
            showNewMessages = false
            listState.scrollToItem(messages.lastIndex)
            return@LaunchedEffect
        }

        val oldLastMessageId = previousLastMessageId
            ?: return@LaunchedEffect

        if (oldLastMessageId == newLastMessageId) return@LaunchedEffect
        previousLastMessageId = newLastMessageId

        val oldLastMessageWasVisible = listState.layoutInfo.visibleItemsInfo
            .any { it.key == oldLastMessageId }

        if (isAtBottom || oldLastMessageWasVisible) {
            showNewMessages = false
            listState.scrollToItem(messages.lastIndex)
        } else {
            showNewMessages = true
        }
    }

    LaunchedEffect(lastMessageId) {
        val newestMessageId = lastMessageId ?: return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect

        session?.markMessagesRead(
            providerDialogId = providerDialogId,
            messageIds = messages
                .takeLast(50)
                .map { it.messageId }
                .toLongArray()
        )
    }

    LaunchedEffect(session, providerDialogId, listState, initialScrollDone) {
        if (session == null || !initialScrollDone) return@LaunchedEffect

        snapshotFlow {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (!atTop || isLoading || isLoadingMore || !canLoadMore) return@collect
                if (searchQuery.text.isNotBlank()) return@collect
                if (listState.layoutInfo.totalItemsCount == 0) return@collect

                var keepLoading = true

                while (keepLoading && !isLoading && canLoadMore) {
                    val itemCountBefore = listState.layoutInfo.totalItemsCount

                    isLoadingMore = true
                    val result = session.loadOlderMessages(providerDialogId)
                    isLoadingMore = false

                    val hasMore = result.getOrNull()

                    if (hasMore == null) {
                        error = result.exceptionOrNull()?.message
                            ?: "Failed to load older messages"
                        break
                    }

                    canLoadMore = hasMore

                    withFrameNanos { }

                    val info = listState.layoutInfo
                    val stillAtTop = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                    val viewportFilled = info.visibleItemsInfo.lastOrNull()?.let { item ->
                        item.offset + item.size >= info.viewportEndOffset
                    } ?: false
                    val itemCountChanged = info.totalItemsCount > itemCountBefore

                    keepLoading = hasMore &&
                        stillAtTop &&
                        !viewportFilled &&
                        itemCountChanged
                }
            }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || session == null) return

        keyboardController?.hide()
        scope.launch {
            isSending = true
            error = null

            val editing = editingMessage

            if (editing != null) {
                session.editMessage(providerDialogId, editing.messageId, text)
                    .onSuccess {
                        input = ""
                        editingMessage = null
                    }
                    .onFailure { error = it.message ?: "Failed to edit message" }
            } else {
                session.sendMessage(
                    providerDialogId = providerDialogId,
                    text = text,
                    replyToMessageId = replyingTo?.messageId
                )
                    .onSuccess {
                        input = ""
                        replyingTo = null
                        scrollToLatestRequested = true
                    }
                    .onFailure { error = it.message ?: "Failed to send message" }
            }

            isSending = false
        }
    }

    forwardingMessageIds?.let { messageIds ->
        AlertDialog(
            onDismissRequest = { forwardingMessageIds = null },
            title = { Text("Forward to") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(dialogs) { _, target ->
                        if (target.providerDialogId == providerDialogId) {
                            return@itemsIndexed
                        }

                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    forwardingMessageIds = null
                                    forwardMessages(messageIds, target.providerDialogId)
                                }
                                .padding(vertical = 12.dp),
                            text = target.title,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { forwardingMessageIds = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnpinConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnpinConfirmation = false },
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
                IconButton(onClick = onBack) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = arrow_back_icon,
                        contentDescription = "Back"
                    )
                }
            }

            AvatarImage(
                avatar = dialog?.avatar ?: Avatar.fromName(dialog?.title ?: "Dialog"),
                size = 40.dp
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = dialog?.title ?: "Dialog",
                    style = MaterialTheme.typography.titleMedium,
                    lineHeight = MaterialTheme.typography.titleMedium.fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                dialog?.let {
                    val statusText = it.statusText
                    val memberCount = it.memberCount

                    Text(
                        text = statusText ?: when (it.kind) {
                            DialogKind.Direct -> "Direct message"
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
                        color = if (statusText == "online") {
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

            if(dialog?.kind == DialogKind.Direct) {
                IconButton(
                    onClick = {

                    }
                ) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = call_icon,
                        contentDescription = null
                    )
                }
            }

            IconButton(
                onClick = {

                }
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = more_vertical_icon,
                    contentDescription = null
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainer
        )

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
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pinned message",
                        style = MaterialTheme.typography.labelSmall,
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
                        .background(wallpaperBrush(wallpaper!!))
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
                            text = "${selectedMessageIds.size} selected",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        TextButton(
                            onClick = {
                                forwardingMessageIds = selectedMessageIds.toLongArray()
                            }
                        ) {
                            Text("Forward")
                        }

                        TextButton(
                            onClick = { deleteMessages(selectedMessageIds.toLongArray()) }
                        ) {
                            Text("Delete")
                        }

                        IconButton(onClick = { selectedMessageIds = emptySet() }) {
                            Icon(
                                imageVector = close_icon,
                                contentDescription = "Cancel selection"
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        isLoading -> {
                            LoadingContent(
                                modifier = Modifier.align(Alignment.Center),
                                text = "Loading messages..."
                            )
                        }

                        messages.isEmpty() -> {
                            Text(
                                modifier = Modifier.align(Alignment.Center),
                                text = "No messages yet",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(
                                    items = visibleMessages,
                                    key = { _, it -> it.messageId }
                                ) { index, message ->
                                    val previousMessage = if (index > 0) {
                                        visibleMessages[index - 1]
                                    } else {
                                        null
                                    }
                                    val nextMessage = if (index < visibleMessages.size - 1) {
                                        visibleMessages[index + 1]
                                    } else {
                                        null
                                    }
                                    val date = messageDate(message.timestamp)
                                    val previousDate = previousMessage?.let {
                                        messageDate(it.timestamp)
                                    }
                                    val nextDate = nextMessage?.let {
                                        messageDate(it.timestamp)
                                    }
                                    val media = message.media
                                    val reply = replyFor(message, messages, loadedReplies)
                                    val replyMedia = reply?.media
                                        ?.takeIf { it.kind == MessageMediaKind.Photo }
                                    val replyThumbnail: (@Composable () -> Unit)? =
                                        replyMedia?.let { replyImage ->
                                            { ReplyImageThumbnail(replyImage, session) }
                                        }

                                    val mediaContent: (@Composable () -> Unit)? = when {
                                        media == null -> null
                                        media.kind == MessageMediaKind.Photo -> {
                                            {
                                                MessageImagePreview(
                                                    media = media,
                                                    session = session,
                                                    fillWidth = message.text.isNotBlank()
                                                ) {
                                                    MediaViewer.showImage(media, session)
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.Sticker -> {
                                            { ChatSticker(media, session) }
                                        }
                                        media.kind == MessageMediaKind.Animation -> {
                                            {
                                                ChatAnimationPreview(media, session) {
                                                    MediaViewer.showVideo(media, session)
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.Video -> {
                                            {
                                                ChatVideoPreview(media, session) {
                                                    MediaViewer.showVideo(media, session)
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.VideoNote -> {
                                            {
                                                ChatVideoPreview(media, session, isRound = true) {
                                                    MediaViewer.showVideo(media, session)
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.VoiceNote ||
                                            media.kind == MessageMediaKind.Audio -> {
                                            { VoiceMessagePlayer(media, session) }
                                        }
                                        media.kind == MessageMediaKind.File -> {
                                            { MessageFileCard(media, session, message.isOutgoing) }
                                        }
                                        else -> null
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (previousDate != date) {
                                            MessageDatePill(date)
                                        }

                                        MessageBubble(
                                            dialogKind = dialog?.kind ?: DialogKind.Direct,
                                            message = message,
                                            mediaContent = mediaContent,
                                            customEmojiData = customEmojiData,
                                            reply = reply,
                                            replyThumbnail = replyThumbnail,
                                            isSelectionMode = selectedMessageIds.isNotEmpty(),
                                            isSelected = message.messageId in selectedMessageIds,
                                            isPinned = dialog?.pinnedMessageId == message.messageId,
                                            onToggleSelection = {
                                                selectedMessageIds =
                                                    if (message.messageId in selectedMessageIds) {
                                                        selectedMessageIds - message.messageId
                                                    } else {
                                                        selectedMessageIds + message.messageId
                                                    }
                                            },
                                            onReply = { replyingTo = message },
                                            onForward = {
                                                forwardingMessageIds = longArrayOf(message.messageId)
                                            },
                                            onDelete = {
                                                deleteMessages(longArrayOf(message.messageId))
                                            },
                                            onPin = {
                                                pinMessage(
                                                    message = message,
                                                    isPinned = dialog?.pinnedMessageId != message.messageId
                                                )
                                            },
                                            onSelect = {
                                                selectedMessageIds = setOf(message.messageId)
                                            },
                                            onOpenSender = if (
                                                !message.isOutgoing &&
                                                !(dialog?.kind ?: DialogKind.Direct).isPeer &&
                                                message.senderId > 0
                                            ) {
                                                { onOpenUserProfile(message.senderId) }
                                            } else {
                                                null
                                            },

                                            onEdit = if (
                                                message.isOutgoing &&
                                                message.text.isNotBlank()
                                            ) {
                                                {
                                                    editingMessage = message
                                                    input = message.text
                                                }
                                            } else {
                                                null
                                            },

                                            connection = run {
                                                val previousSameSender =
                                                    previousDate == date &&
                                                        previousMessage?.senderId == message.senderId
                                                val nextSameSender =
                                                    nextDate == date &&
                                                        nextMessage?.senderId == message.senderId

                                                when {
                                                    previousSameSender && nextSameSender ->
                                                        MessageBubbleConnection.BOTH

                                                    previousSameSender ->
                                                        MessageBubbleConnection.BOTTOM

                                                    nextSameSender ->
                                                        MessageBubbleConnection.TOP

                                                    else -> MessageBubbleConnection.NONE
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    MessageListOverlays(
                        isLoadingMore = isLoadingMore,
                        showNewMessages = showNewMessages,
                        onScrollToLatest = {
                            showNewMessages = false

                            if (messages.isNotEmpty()) {
                                scope.launch {
                                    listState.animateScrollToItem(messages.lastIndex)
                                }
                            }
                        }
                    )

                }

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

                if (dialog?.canSendMessages == false) {
                    NotSendableNotice()
                } else Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(32.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            IconButton(
                                onClick = {
                                    filePicker.pickFile()
                                }
                            ) {
                                Icon(
                                    tint = MaterialTheme.colorScheme.secondary,
                                    imageVector = add_icon,
                                    contentDescription = null
                                )
                            }

                            BasicTextField(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 32.dp),
                                value = input,
                                onValueChange = { input = it },
                                enabled = !isSending,
                                maxLines = 10,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),

                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),

                                decorationBox = { innerTextField ->
                                    innerTextField()

                                    if(input.isEmpty()) {
                                        Text(
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.secondary,
                                            text = "Write a message..."
                                        )
                                    }
                                }
                            )

                            IconButton(
                                onClick = {

                                }
                            ) {
                                Icon(
                                    tint = MaterialTheme.colorScheme.secondary,
                                    imageVector = mood_icon,
                                    contentDescription = null
                                )
                            }

                            AnimatedVisibility(input.isEmpty()) {
                                IconButton(
                                    onClick = {

                                    }
                                ) {
                                    Icon(
                                        tint = MaterialTheme.colorScheme.secondary,
                                        imageVector = microphone_icon,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(input.isNotEmpty()) {
                        FilledIconButton(
                            enabled = input.isNotBlank() && !isSending,
                            onClick = ::send
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )

                                return@FilledIconButton
                            }
                            Icon(
                                imageVector = send_icon,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MessageListOverlays(
    isLoadingMore: Boolean,
    showNewMessages: Boolean,
    onScrollToLatest: () -> Unit
) {
    AnimatedVisibility(
        visible = isLoadingMore,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
    }

    AnimatedVisibility(
        visible = showNewMessages,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(12.dp)
    ) {
        Button(onClick = onScrollToLatest) {
            Text("New messages")
        }
    }
}

private fun replyFor(
    message: ChatMessage,
    allMessages: List<ChatMessage>,
    loadedReplies: Map<Long, ChatMessage> = emptyMap()
): MessageReply? {
    val reply = message.replyTo ?: return null
    val original = allMessages.firstOrNull { it.messageId == reply.messageId }
        ?: loadedReplies[reply.messageId]

    return if (original != null) {
        reply.copy(
            senderName = if (original.isOutgoing) "You" else original.senderName,
            text = original.replySnippet(),
            media = original.media
        )
    } else {
        reply.copy(text = reply.text.ifBlank { "Message" })
    }
}

private fun ChatMessage.replySnippet(): String {
    if (text.isNotBlank()) return text

    val media = media ?: return "Message"

    return when (media.kind) {
        MessageMediaKind.Photo -> "Photo"
        MessageMediaKind.Video -> "Video"
        MessageMediaKind.Animation -> "GIF"
        MessageMediaKind.Sticker -> "Sticker"
        MessageMediaKind.VideoNote -> "Video message"
        MessageMediaKind.VoiceNote -> "Voice message"
        MessageMediaKind.Audio -> media.fileName ?: "Audio"
        MessageMediaKind.File -> media.fileName ?: "File"
    }
}

@Composable
private fun ReplyImageThumbnail(
    media: MessageMedia,
    session: AccountSession?
) {
    var image by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(media.previewFileId, media.minithumbnail, session) {
        image = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        val bytes = media.previewFileId?.let { fileId ->
            session?.loadFile(fileId)?.getOrNull()
        }

        if (bytes != null) {
            image = withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }
    }

    val bitmap = image

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun MessageImagePreview(
    media: MessageMedia,
    session: AccountSession?,
    fillWidth: Boolean,
    onClick: () -> Unit
) {
    var image by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var isLoading by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf(true)
    }

    LaunchedEffect(media.previewFileId, media.minithumbnail, session) {
        image = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }
        isLoading = image == null

        val bytes = media.previewFileId?.let { fileId ->
            session?.loadFile(fileId)?.getOrNull()
        }

        if (bytes != null) {
            image = withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        isLoading = false
    }

    val bitmap = image
    val aspectRatio = bitmap?.let { it.width.toFloat() / it.height }
        ?: if (media.width > 0 && media.height > 0) {
            media.width.toFloat() / media.height
        } else {
            4f / 3f
        }

    Box(
        modifier = Modifier
            .then(
                if (fillWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.width(260.dp)
                }
            )
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = bitmap != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            else -> {
                Text(
                    text = "Image",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun MessageFilePreview(
    media: MessageMedia,
    isOutgoing: Boolean
) {
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isOutgoing) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "FILE",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = media.fileName?.takeIf { it.isNotBlank() } ?: "File",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val size = formatFileSize(media.size)
            if (size.isNotEmpty()) {
                Text(
                    text = size,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor
                )
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return count.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
private fun MessageFileCard(
    media: MessageMedia,
    session: AccountSession?,
    isOutgoing: Boolean
) {
    val scope = rememberCoroutineScope()
    var isDownloading by remember(media.fileId) { mutableStateOf(false) }
    var savedPath by remember(media.fileId) { mutableStateOf<String?>(null) }

    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isOutgoing) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "FILE",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = media.fileName?.takeIf { it.isNotBlank() } ?: "File",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val size = formatFileSize(media.size)
            if (size.isNotEmpty()) {
                Text(
                    text = size,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor
                )
            }
        }

        TextButton(
            enabled = !isDownloading && media.fileId != null,
            onClick = {
                val fileId = media.fileId ?: return@TextButton

                scope.launch {
                    isDownloading = true

                    val bytes = session?.loadFile(fileId)?.getOrNull()

                    if (bytes != null) {
                        savedPath = withContext(Dispatchers.IO) {
                            saveFileToDevice(media.fileName ?: "file", bytes)
                        }
                    }

                    isDownloading = false
                }
            }
        ) {
            Text(
                text = when {
                    savedPath != null -> "Saved"
                    isDownloading -> "Saving..."
                    else -> "Download"
                }
            )
        }
    }
}

@Composable
private fun NotSendableNotice() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = "You can't send messages to this chat",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
