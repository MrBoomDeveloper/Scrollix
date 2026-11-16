package com.mrboomdev.scrollix.features.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test.microphone_icon
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.components.PickedFile
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.icons.add_icon
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.attach_file_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.ui.isTwoPaneLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@Composable
fun DialogScreen(
    accountId: String,
    providerDialogId: String,
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

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var input by remember(providerDialogId) { mutableStateOf("") }
    var error by remember(providerDialogId) { mutableStateOf<String?>(null) }
    var isLoading by remember(providerDialogId) { mutableStateOf(true) }
    var isSending by remember(providerDialogId) { mutableStateOf(false) }
    var isLoadingMore by remember(providerDialogId) { mutableStateOf(false) }
    var canLoadMore by remember(providerDialogId) { mutableStateOf(true) }
    var hasScrolledToBottom by remember(providerDialogId) { mutableStateOf(false) }

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
            ).onFailure {
                error = it.message ?: "Failed to send file"
            }

            isSending = false
        }
    }

    val filePicker = rememberFilePicker(
        onImagePicked = { picked -> sendAttachment(picked, asPhoto = true) },
        onFilePicked = { picked -> sendAttachment(picked, asPhoto = false) }
    )

    LaunchedEffect(session, providerDialogId) {
        error = null
        isLoading = true
        isLoadingMore = false
        canLoadMore = true
        hasScrolledToBottom = false

        if (session == null) {
            error = "Account session not found"
            isLoading = false
            return@LaunchedEffect
        }

        session.loadMessages(providerDialogId)
            .onFailure { error = it.message ?: "Failed to load messages" }
        isLoading = false
    }

    val lastMessageId = messages.lastOrNull()?.messageId
    LaunchedEffect(lastMessageId, isLoading) {
        if (!isLoading) {
            if (messages.isNotEmpty()) {
                val paginationOffset = if (isLoadingMore || !canLoadMore) 1 else 0
                listState.scrollToItem(messages.lastIndex + paginationOffset)
            }
            hasScrolledToBottom = true
        }
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            session?.markMessagesRead(
                providerDialogId = providerDialogId,
                messageIds = messages.map { it.messageId }.toLongArray()
            )
        }
    }

    LaunchedEffect(
        listState,
        session,
        providerDialogId,
        canLoadMore,
        isLoading,
        hasScrolledToBottom
    ) {
        if (session == null || !canLoadMore || !hasScrolledToBottom) return@LaunchedEffect

        snapshotFlow {
            val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisible != null &&
                firstVisible.index <= 1 &&
                listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (!atTop || isLoading || isLoadingMore) return@collect
                if (listState.layoutInfo.totalItemsCount == 0) return@collect

                isLoadingMore = true
                session.loadOlderMessages(providerDialogId)
                    .onSuccess { hasMore -> canLoadMore = hasMore }
                    .onFailure {
                        error = it.message ?: "Failed to load older messages"
                    }
                isLoadingMore = false
            }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || session == null) return

        keyboardController?.hide()
        scope.launch {
            isSending = true
            error = null

            session.sendMessage(providerDialogId, text)
                .onSuccess { input = "" }
                .onFailure { error = it.message ?: "Failed to send message" }

            isSending = false
        }
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                dialog?.let {
                    Text(
                        text = when (it.kind) {
                            DialogKind.Direct -> "Direct message"
                            DialogKind.Group -> "Group"
                            DialogKind.Channel -> "Channel"
                            DialogKind.Bot -> "Bot"
                            DialogKind.Secret -> "Secret chat"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(
                onClick = {
                    
                }
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = search_icon,
                    contentDescription = null
                )
            }

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
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLoadingMore || !canLoadMore) {
                            item(key = "pagination") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "Beginning of chat",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = messages,
                            key = { _, it -> it.messageId }
                        ) { index, message ->
                            MessageBubble(
                                dialogKind = dialog?.kind ?: DialogKind.Direct,
                                message = message,
                                
                                connection = run {
                                    val prevMessage = if(index > 0) messages[index - 1] else null
                                    val nextMessage = if(index < messages.size - 1) messages[index + 1] else null
                                    
                                    if (index > 0) {
                                        if (prevMessage?.senderId == message.senderId) {
                                            if (nextMessage?.senderId == message.senderId) {
                                                return@run MessageBubbleConnection.BOTH
                                            }
                                            
                                            return@run MessageBubbleConnection.BOTTOM
                                        }
                                    }

                                    if (
                                        index < messages.size - 1 &&
                                        messages.size > 1 &&
                                        nextMessage?.senderId == message.senderId
                                    ) { 
                                        return@run MessageBubbleConnection.TOP 
                                    }

                                    return@run MessageBubbleConnection.NONE
                                }
                            )
                        }
                    }
                }
            }
        }

        error?.let { message ->
            Text(
                modifier = Modifier.padding(horizontal = 12.dp),
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp),
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

@Composable
private fun MessageImagePreview(
    media: MessageMedia,
    session: AccountSession?
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

    val aspectRatio = if (media.width > 0 && media.height > 0) {
        media.width.toFloat() / media.height
    } else {
        4f / 3f
    }
    val bitmap = image

    Box(
        modifier = Modifier
            .width(260.dp)
            .aspectRatio(aspectRatio.coerceIn(0.75f, 1.8f))
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
