package com.mrboomdev.scrollix.features.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.key
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.data.model.UndiscoveredMessagesMessageId
import com.mrboomdev.scrollix.ui.components.ChatAnimationPreview
import com.mrboomdev.scrollix.ui.components.ChatSticker
import com.mrboomdev.scrollix.ui.components.ChatVideoPreview
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.MessageGallery
import com.mrboomdev.scrollix.ui.components.MessageDatePill
import com.mrboomdev.scrollix.ui.components.MessageThreadRail
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.components.VoiceMessagePlayer
import com.mrboomdev.scrollix.ui.components.messageDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettings
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun ColumnScope.DialogMessageArea(
    ui: DialogScreenState,
    providerDialogId: String,
    onOpenUserProfile: (Long) -> Unit,
    onOpenHashtag: (String) -> Unit,
    session: AccountSession?,
    history: MessageHistory,
    messages: List<ChatMessage>,
    dialog: Dialog?,
    settings: AppSettings,
    scope: CoroutineScope,
    searchQuery: TextFieldState,
    composerInset: Dp,
    messageListState: DialogMessageListState,
    listState: LazyListState,
    threadRailItems: List<ChatMessage>,
    messagesById: Map<Long, ChatMessage>,
    gapRowId: String?,
    isMobileLayout: Boolean,
    doubleTapReaction: MessageReactionOption?,
    visibleMessages: List<ChatMessage>,
    onRequestDelete: (LongArray) -> Unit,
    onPinMessage: (ChatMessage, Boolean) -> Unit,
    onApplyReaction: (ChatMessage, MessageReactionOption) -> Unit,
    onCancelPendingMessage: (Long) -> Unit,
    composer: @Composable BoxScope.() -> Unit = {},
) = with(ui) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // The undiscovered row loads the next page of the gap as
                    // soon as it becomes visible, so scrolling into it pages
                    // the middle lazily instead of fetching the whole chat.
                    LaunchedEffect(
                        providerDialogId,
                        history.hasUnloadedGap,
                        history.messages.size,
                        history.newerMessages.size,
                        messageListState.isReady,
                        gapRowId
                    ) {
                        val gapId = gapRowId ?: return@LaunchedEffect
                        if (!history.hasUnloadedGap || !messageListState.isReady) {
                            return@LaunchedEffect
                        }

                        snapshotFlow {
                            messageListState.listState.layoutInfo.visibleItemsInfo.any { info ->
                                info.key == gapId
                            }
                        }
                            .distinctUntilChanged()
                            .collect { visible ->
                                if (!visible || isLoadingGap) return@collect

                                isLoadingGap = true

                                try {
                                    session?.loadNewerMessages(providerDialogId)
                                } finally {
                                    isLoadingGap = false
                                }
                            }
                    }

                    // The composer grows after its first measurement (a reply
                    // banner, the emoji picker, the keyboard inset). Re-anchor
                    // to the newest row so the chat does not end above the
                    // bottom when that happens.
                    LaunchedEffect(composerInset, messageListState.isReady) {
                        if (!messageListState.isReady) return@LaunchedEffect
                        if (!messageListState.isAtBottom) return@LaunchedEffect

                        withFrameNanos { }

                        val last = listState.layoutInfo.totalItemsCount - 1
                        if (last >= 0) listState.scrollToItem(last, Int.MAX_VALUE)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        history.isLoadingInitial -> {
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

                        // The list is revealed only after it has been scrolled
                        // to its restored anchor, so it never flashes the wrong
                        // part of the history while switching dialogs.
                        !messageListState.isReady -> {
                            LoadingContent(
                                modifier = Modifier.align(Alignment.Center),
                                text = "Restoring position..."
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    top = 8.dp,
                                    end = 8.dp,
                                    bottom = composerInset
                                ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(
                                    items = visibleMessages,
                                    key = { _, it -> it.rowId }
                                ) { index, message ->
                                    if (message.messageId == UndiscoveredMessagesMessageId) {
                                        UndiscoveredMessagesRow(
                                            isLoading = isLoadingGap,
                                            onClick = {
                                                if (!isLoadingGap) {
                                                    scope.launch {
                                                        isLoadingGap = true

                                                        try {
                                                            session?.loadNewerMessages(
                                                                providerDialogId
                                                            )
                                                        } finally {
                                                            isLoadingGap = false
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                        return@itemsIndexed
                                    }

                                    if (unreadAnchorMessageId?.let { it in message.allMessageIds } == true) {
                                        UnreadMessagesHeader()
                                    }

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
                                    val shouldAnimate =
                                        !settings.reduceMotion &&
                                            message.messageId in locallySentMessageIds &&
                                            message.messageId == visibleMessages.lastOrNull()?.messageId &&
                                            message.messageId !in animatedMessageIds
                                    val mediaItems = message.allMedia
                                    val media = mediaItems.firstOrNull()
                                    val reply = replyFor(message, messagesById, loadedReplies)
                                    val replyMedia = reply?.media
                                        ?.takeIf { it.kind == MessageMediaKind.Photo }
                                    val replyThumbnail: (@Composable () -> Unit)? =
                                        replyMedia?.let { replyImage ->
                                            { ReplyImageThumbnail(replyImage, session) }
                                        }

                                    val poll = message.poll
                                    val mediaContent: (@Composable () -> Unit)? = when {
                                        poll != null -> {
                                            {
                                                MessagePollCard(
                                                    poll = poll,
                                                    isOutgoing = message.isOutgoing,
                                                    session = session,
                                                    customEmojiData = customEmojiData,
                                                    customEmojiAdaptive = customEmojiAdaptive,
                                                    onOptionClick = { option ->
                                                        if (session != null) {
                                                            scope.launch {
                                                                val optionIds = if (poll.allowsMultipleAnswers) {
                                                                    val chosen = poll.options
                                                                        .filter { it.isChosen }
                                                                        .map { it.id }

                                                                    if (option.isChosen) {
                                                                        chosen - option.id
                                                                    } else {
                                                                        chosen + option.id
                                                                    }
                                                                } else {
                                                                    listOf(option.id)
                                                                }

                                                                session.votePoll(
                                                                    providerDialogId = providerDialogId,
                                                                    messageId = message.messageId,
                                                                    optionIds = optionIds
                                                                ).onFailure { throwable ->
                                                                    error = throwable.message ?: "Failed to vote"
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onRetractVote = {
                                                        if (session != null) {
                                                            scope.launch {
                                                                session.votePoll(
                                                                    providerDialogId = providerDialogId,
                                                                    messageId = message.messageId,
                                                                    optionIds = emptyList()
                                                                ).onFailure { throwable ->
                                                                    error = throwable.message ?: "Failed to retract vote"
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        mediaItems.size > 1 &&
                                            mediaItems.all { item -> item.isGalleryMedia() } -> {
                                            {
                                                MessageGallery(
                                                    media = mediaItems,
                                                    session = session
                                                ) { index ->
                                                    MediaViewer.showGallery(
                                                        media = mediaItems,
                                                        initialIndex = index,
                                                        session = session,
                                                        caption = message.text
                                                    )
                                                }
                                            }
                                        }
                                        mediaItems.size > 1 -> {
                                            {
                                                // A media album can contain documents.
                                                // Those must render as file cards, not
                                                // be sent through the image gallery.
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    for (item in mediaItems) {
                                                        when (item.kind) {
                                                            MessageMediaKind.Photo -> MessageImagePreview(
                                                                media = item,
                                                                session = session,
                                                                fillWidth = false
                                                            ) {
                                                                MediaViewer.showImage(item, session, caption = message.text)
                                                            }

                                                            MessageMediaKind.Video -> ChatVideoPreview(
                                                                media = item,
                                                                session = session
                                                            ) {
                                                                MediaViewer.showVideo(item, session, caption = message.text)
                                                            }

                                                            MessageMediaKind.Animation -> ChatAnimationPreview(
                                                                media = item,
                                                                session = session
                                                            ) {
                                                                MediaViewer.showVideo(item, session, caption = message.text)
                                                            }

                                                            MessageMediaKind.VideoNote -> ChatVideoPreview(
                                                                media = item,
                                                                session = session,
                                                                isRound = true
                                                            ) {
                                                                MediaViewer.showVideo(item, session, caption = message.text)
                                                            }

                                                            MessageMediaKind.VoiceNote,
                                                            MessageMediaKind.Audio -> VoiceMessagePlayer(
                                                                media = item,
                                                                session = session
                                                            )

                                                            MessageMediaKind.File -> MessageFileCard(
                                                                media = item,
                                                                session = session,
                                                                isOutgoing = message.isOutgoing
                                                            )

                                                            MessageMediaKind.Sticker -> ChatSticker(
                                                                media = item,
                                                                session = session,
                                                                onClick = {
                                                                    item.stickerSetId?.let { setId ->
                                                                        openedStickerSetId = setId
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        media == null -> null
                                        media.kind == MessageMediaKind.Photo -> {
                                            {
                                                MessageImagePreview(
                                                    media = media,
                                                    session = session,
                                                    fillWidth = message.text.isNotBlank()
                                                ) {
                                                    MediaViewer.showImage(
                                                        media = media,
                                                        session = session,
                                                        caption = message.text
                                                    )
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.Sticker -> {
                                            {
                                                ChatSticker(
                                                    media = media,
                                                    session = session,
                                                    onClick = {
                                                        media.stickerSetId?.let { setId ->
                                                            openedStickerSetId = setId
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        media.kind == MessageMediaKind.Animation -> {
                                            {
                                                ChatAnimationPreview(media, session) {
                                                    MediaViewer.showVideo(
                                                        media = media,
                                                        session = session,
                                                        caption = message.text
                                                    )
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.Video -> {
                                            {
                                                ChatVideoPreview(media, session) {
                                                    MediaViewer.showVideo(
                                                        media = media,
                                                        session = session,
                                                        caption = message.text
                                                    )
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.VideoNote -> {
                                            {
                                                ChatVideoPreview(media, session, isRound = true) {
                                                    MediaViewer.showVideo(
                                                        media = media,
                                                        session = session,
                                                        caption = message.text
                                                    )
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

                                    // Rows of the same sender within a day sit
                                    // flush against each other; the list adds
                                    // no extra gap between connected bubbles.
                                    val isConnectedToPrevious =
                                        previousDate == date &&
                                            previousMessage?.senderId == message.senderId

                                    Column(
                                        modifier = Modifier.padding(
                                            top = if (isConnectedToPrevious) 0.dp else 6.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (previousDate != date) {
                                            MessageDatePill(date)
                                        }

                                        // The entrance is composed only for a
                                        // message this device just sent. Every
                                        // other row renders without transition
                                        // state, so scrolling and paging cannot
                                        // replay an old message's animation.
                                        val bubbleContent: @Composable () -> Unit = {
                                        MessageBubble(
                                            dialogKind = dialog?.kind ?: DialogKind.Direct,
                                            message = message,
                                            mediaContent = mediaContent,
                                            richMedia = { richBlockMedia ->
                                                RichBlockMediaPreview(richBlockMedia, session)
                                            },
                                            customEmojiData = customEmojiData,
                                            customEmojiAdaptive = customEmojiAdaptive,
                                            session = session,
                                            reply = reply,
                                            replyThumbnail = replyThumbnail,
                                            isSelectionMode = selectedMessageIds.isNotEmpty(),
                                            isSelected = message.allMessageIds.any { id ->
                                                id in selectedMessageIds
                                            },
                                            isPinned = dialog?.pinnedMessageId?.let { id ->
                                                id in message.allMessageIds
                                            } == true,
                                            onOpenStickerSet = { setId ->
                                                openedStickerSetId = setId
                                            },
                                            onOpenWebApp = { button ->
                                                webAppButton = button
                                            },
                                            onDoubleTap = if (
                                                isMobileLayout && doubleTapReaction != null
                                            ) {
                                                {
                                                    onApplyReaction(message, doubleTapReaction)
                                                }
                                            } else {
                                                null
                                            },
                                            onToggleSelection = {
                                                val ids = message.allMessageIds.toSet()
                                                selectedMessageIds =
                                                    if (ids.any { id -> id in selectedMessageIds }) {
                                                        selectedMessageIds - ids
                                                    } else {
                                                        selectedMessageIds + ids
                                                    }
                                            },
                                            onReply = if (dialog?.canSendMessages != false) {
                                                { replyingTo = message }
                                            } else {
                                                null
                                            },
                                            onOpenThread = if (message.replyCount > 0) {
                                                { threadRoot = message }
                                            } else {
                                                null
                                            },
                                            onForward = if (message.canBeForwarded) {
                                                {
                                                    forwardingMessageIds =
                                                        message.allMessageIds.toLongArray()
                                                }
                                            } else {
                                                null
                                            },
                                            onDelete = if (
                                                message.messageId < 0 ||
                                                message.canBeDeleted ||
                                                dialog?.canDeleteMessages == true
                                            ) {
                                                {
                                                    if (message.messageId < 0) {
                                                        onCancelPendingMessage(message.messageId)
                                                    } else {
                                                        onRequestDelete(
                                                            message.allMessageIds.toLongArray()
                                                        )
                                                    }
                                                }
                                            } else {
                                                null
                                            },
                                            onPin = if (dialog?.canPinMessages != false) {
                                                {
                                                    onPinMessage(
                                                        message,
                                                        dialog?.pinnedMessageId?.let { id ->
                                                            id !in message.allMessageIds
                                                        } != false
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            onSelect = {
                                                selectedMessageIds = message.allMessageIds.toSet()
                                            },
                                            onCancelSend = {
                                                if (message.messageId < 0) {
                                                    onCancelPendingMessage(message.messageId)
                                                } else {
                                                    onRequestDelete(
                                                        message.allMessageIds.toLongArray()
                                                    )
                                                }
                                            },
                                            onRetrySend = if (
                                                message.messageId < 0 &&
                                                message.sendState == MessageSendState.Failed
                                            ) {
                                                {
                                                    OutboxManager.retryByLocalMessageId(
                                                        message.messageId
                                                    )
                                                }
                                            } else {
                                                null
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
                                                message.text.isNotBlank() &&
                                                !message.isService
                                            ) {
                                                {
                                                    editingMessage = message
                                                    pendingAttachments = emptyList()
                                                    input = message.text
                                                    inputSelection = TextRange(message.text.length)
                                                    inputCustomEmojis = message.customEmojis
                                                }
                                            } else {
                                                null
                                            },

                                            onReaction = { option ->
                                                val isChosen = message.reactions.any { reaction ->
                                                    reaction.isChosen && if (
                                                        option.customEmojiId != null
                                                    ) {
                                                        reaction.customEmojiId == option.customEmojiId
                                                    } else {
                                                        reaction.customEmojiId == null &&
                                                            reaction.emoji == option.emoji
                                                    }
                                                }

                                                scope.launch {
                                                    if (isChosen) {
                                                        session?.removeMessageReaction(
                                                            providerDialogId = providerDialogId,
                                                            messageId = message.messageId,
                                                            emoji = option.emoji,
                                                            customEmojiId = option.customEmojiId
                                                        )
                                                    } else {
                                                        session?.addMessageReaction(
                                                            providerDialogId = providerDialogId,
                                                            messageId = message.messageId,
                                                            emoji = option.emoji,
                                                            customEmojiId = option.customEmojiId
                                                        )
                                                    }
                                                }
                                            },

                                            onHashtagClick = onOpenHashtag,
                                            onMentionClick = { username ->
                                                scope.launch {
                                                    session?.resolveUsername(username)
                                                        ?.onSuccess { targetUserId ->
                                                            onOpenUserProfile(targetUserId)
                                                        }
                                                }
                                            },

                                            onReplyClick = { replyMessageId ->
                                                searchOpen = false
                                                searchQuery.clearText()
                                                scrollRequest =
                                                    ScrollRequest.Message(replyMessageId)
                                            },

                                            connection = run {
                                                val previousSameSender =
                                                    previousDate == date &&
                                                        previousMessage.senderId == message.senderId
                                                val nextSameSender =
                                                    nextDate == date &&
                                                        nextMessage.senderId == message.senderId

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

                                        if (message.isService) {
                                            ServiceMessagePill(message.text)
                                        } else {
                                        if (shouldAnimate) {
                                            val appearanceState = remember(message.messageId) {
                                                MutableTransitionState(false)
                                                    .apply { targetState = true }
                                            }

                                            LaunchedEffect(message.messageId) {
                                                animatedMessageIds =
                                                    animatedMessageIds + message.messageId
                                            }

                                            AnimatedVisibility(
                                                visibleState = appearanceState,
                                                enter = slideInVertically(
                                                    initialOffsetY = { height -> height }
                                                ) + fadeIn()
                                            ) {
                                                bubbleContent()
                                            }
                                        } else {
                                            bubbleContent()
                                        }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MessageListOverlays(
                        isLoadingMore = history.isLoadingOlder,
                        showNewMessages = messageListState.hasNewMessages,
                        isAtBottom = messageListState.isAtBottom,
                        bottomInset = composerInset,
                        onScrollToLatest = { scrollRequest = ScrollRequest.Latest }
                    )

                    if (searchQuery.text.isBlank()) {
                        MessageThreadRail(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, bottom = composerInset + 8.dp),
                            threads = threadRailItems,
                            selectedMessageId = threadRoot?.messageId,
                            onOpen = { root -> threadRoot = root }
                        )
                    }

                    composer()
                }
        }
}
