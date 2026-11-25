package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.done_all_icon
import com.mrboomdev.scrollix.onSecondaryClick
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.content_copy_icon
import com.mrboomdev.scrollix.ui.icons.delete_icon
import com.mrboomdev.scrollix.ui.icons.edit_icon
import com.mrboomdev.scrollix.ui.icons.forward_icon
import com.mrboomdev.scrollix.ui.icons.keep_icon
import com.mrboomdev.scrollix.ui.icons.keep_off_icon
import com.mrboomdev.scrollix.ui.icons.reply_icon
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon

enum class MessageBubbleConnection {
    NONE,
    BOTH,
    TOP,
    BOTTOM
}

@OptIn(ExperimentalFlexBoxApi::class)
@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    dialogKind: DialogKind,
    connection: MessageBubbleConnection,
    message: ChatMessage,
    mediaContent: (@Composable () -> Unit)? = null,
    richMedia: (@Composable (MessageMedia) -> Unit)? = null,
    customEmojiData: Map<Long, ByteArray> = emptyMap(),
    customEmojiAdaptive: Set<Long> = emptySet(),
    session: AccountSession? = null,
    reply: MessageReply? = null,
    replyThumbnail: (@Composable () -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isPinned: Boolean = false,
    onOpenStickerSet: ((Long) -> Unit)? = null,
    onOpenWebApp: ((MessageButton) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onOpenSender: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onToggleSelection: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    /** Opens the message's reply thread; null when it has no thread. */
    onOpenThread: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onReaction: ((MessageReactionOption) -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onCancelSend: (() -> Unit)? = null,
    /** Retries a queued message that the provider permanently rejected. */
    onRetrySend: (() -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onReplyClick: ((Long) -> Unit)? = null
) {
    val layoutInfo = currentAdaptiveLayoutInfo()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var menuExpanded by remember(message.messageId) { mutableStateOf(false) }
    var readTimestamp by remember(message.messageId) { mutableStateOf<Long?>(null) }
    var usedEmojiPacks by remember(message.messageId) {
        mutableStateOf<List<StickerPack>>(emptyList())
    }

    // Mention avatars are resolved live through the session: cached message
    // avatars are bytes from the cache and would go stale.
    var mentionAvatars by remember(message.messageId) {
        mutableStateOf<Map<String, Avatar>>(emptyMap())
    }

    LaunchedEffect(session, message.messageId, message.text) {
        mentionAvatars = if (session == null || '@' !in message.text) {
            emptyMap()
        } else {
            session.loadMentionAvatars(message.text)
        }
    }

    // The menu lists packs that actually occur in this message: sticker media
    // sets plus the sets of every custom emoji used in the text.
    LaunchedEffect(menuExpanded, session, message.messageId) {
        if (!menuExpanded || session == null) return@LaunchedEffect

        val customSetIds = session
            .loadCustomEmojiSetIds(message.customEmojis.map { it.emojiId })
            .values
        val setIds = (
            message.allMedia.mapNotNull { media -> media.stickerSetId } + customSetIds
        ).distinct()

        usedEmojiPacks = setIds.mapNotNull { setId ->
            session.loadStickerSet(setId).getOrNull()?.let { set ->
                StickerPack(
                    id = setId,
                    title = set.title,
                    isInstalled = set.isInstalled
                )
            }
        }
    }

    LaunchedEffect(
        menuExpanded,
        session,
        message.messageId,
        message.isOutgoing,
        message.sendState
    ) {
        if (
            !menuExpanded ||
            !message.isOutgoing ||
            message.sendState != MessageSendState.Sent ||
            session == null
        ) {
            return@LaunchedEffect
        }

        readTimestamp = session
            .loadMessageReadDate(message.providerDialogId, message.messageId)
            .getOrNull()
    }

    // Entering selection mode closes whatever menu was already open; the menu
    // itself cannot be opened again until the selection is cleared.
    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) {
            menuExpanded = false
        }
    }

    val onPhoneClick: (String) -> Unit = { raw ->
        val number = raw.filter { character -> character.isDigit() || character == '+' }

        // Desktop systems may have no dialer registered for tel: links,
        // in that case at least make the number easy to copy.
        runCatching {
            uriHandler.openUri("tel:$number")
        }.onFailure {
            clipboard.setText(AnnotatedString(number))
        }
    }
    
    val primaryMedia = message.allMedia.firstOrNull()
    val isSending = message.sendState == MessageSendState.Sending
    val isMediaOnly = message.forwardedFromName == null &&
        message.replyTo == null && message.text.isBlank() && (
        primaryMedia?.kind == MessageMediaKind.Sticker ||
            primaryMedia?.kind == MessageMediaKind.VideoNote ||
            primaryMedia?.kind == MessageMediaKind.Animation ||
            primaryMedia?.kind == MessageMediaKind.Photo
    ) || (message.replyTo != null && (primaryMedia?.kind == MessageMediaKind.Sticker || primaryMedia?.kind == MessageMediaKind.VideoNote))

    // Messages without reactions put a copy of the footer right after the text,
    // so that short messages don't waste a whole line on it.
    val isFooterInline = message.reactions.isEmpty() &&
        message.text.isNotBlank() &&
        message.richBlocks.isEmpty()

    BoxWithConstraints(modifier = modifier) {
        val maxBubbleWidth = maxWidth * if(layoutInfo.size < AdaptiveLayoutSize.EXPANDED && isMediaOnly) 1f else 0.75f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection?.invoke() }
                )
            }

            if (message.isOutgoing) {
                Spacer(Modifier.weight(1f))
            } else if (!dialogKind.isPeer) {
                AvatarImage(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .then(
                            if (onOpenSender != null) {
                                Modifier.clickable(onClick = onOpenSender)
                            } else {
                                Modifier
                            }
                        ),
                    avatar = message.senderAvatar
                        ?: Avatar.fromName(message.senderName ?: "Group member"),
                    size = 40.dp
                )
            }

            Box(
                modifier = Modifier.widthIn(max = maxBubbleWidth)
            ) {
                val connectedCornerRadius = 4.dp
                val cornerRadius = 24.dp
                
                Surface(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) onToggleSelection?.invoke()
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    onToggleSelection?.invoke()
                                } else {
                                    menuExpanded = true
                                }
                            },
                            onDoubleClick = {
                                if (!isSelectionMode) {
                                    onDoubleTap?.invoke()
                                }
                            }
                        )
                        .onSecondaryClick {
                            if (!isSelectionMode) {
                                menuExpanded = true
                            }
                        },

                    shape = when (connection) {
                        MessageBubbleConnection.NONE -> RoundedCornerShape(cornerRadius)

                        MessageBubbleConnection.BOTTOM -> RoundedCornerShape(
                            topStart = if (message.isOutgoing) cornerRadius else connectedCornerRadius,
                            topEnd = if (message.isOutgoing) connectedCornerRadius else cornerRadius,
                            bottomEnd = cornerRadius,
                            bottomStart = cornerRadius
                        )

                        MessageBubbleConnection.TOP -> RoundedCornerShape(
                            topStart = cornerRadius,
                            topEnd = cornerRadius,
                            bottomEnd = if (message.isOutgoing) connectedCornerRadius else cornerRadius,
                            bottomStart = if (message.isOutgoing) cornerRadius else connectedCornerRadius
                        )

                        MessageBubbleConnection.BOTH -> RoundedCornerShape(
                            topStart = if (message.isOutgoing) cornerRadius else connectedCornerRadius,
                            topEnd = if (message.isOutgoing) connectedCornerRadius else cornerRadius,
                            bottomEnd = if (message.isOutgoing) connectedCornerRadius else cornerRadius,
                            bottomStart = if (message.isOutgoing) cornerRadius else connectedCornerRadius
                        )
                    },

                    color = when {
                        isSelected -> MaterialTheme.colorScheme.secondaryContainer
                        isMediaOnly -> Color.Transparent
                        message.isOutgoing -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if(isMediaOnly && message.isOutgoing) {
                            reply?.let { data ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    CompositionLocalProvider(
                                        LocalContentColor provides MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        ReplyQuote(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary),
                                            reply = data,
                                            thumbnail = replyThumbnail,
                                            isOutgoing = true,
                                            onClick = onReplyClick?.let { click ->
                                                { click(data.messageId) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Column(
                            modifier = Modifier.padding(
                                if (isMediaOnly) {
                                    PaddingValues(0.dp)
                                } else {
                                    PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                }
                            ).width(IntrinsicSize.Max)
                        ) {
                            var isSenderNameShown = false

                            message.forwardedFromName
                                ?.takeIf { it.isNotBlank() }
                                ?.let { name ->
                                    isSenderNameShown = true
                                    val labelColor = if (message.isOutgoing) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                    val nameColor = if (message.isOutgoing) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AvatarImage(
                                            avatar = message.forwardedFromAvatar
                                                ?: Avatar.fromName(name),
                                            size = 20.dp
                                        )

                                        Text(
                                            style = MaterialTheme.typography.bodySmall,
                                            color = labelColor,
                                            text = "Forwarded from"
                                        )

                                        Text(
                                            modifier = Modifier.weight(1f, fill = false),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = nameColor,
                                            text = name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))
                                }

                            message.senderName?.also { name ->
                                if (message.isOutgoing || dialogKind.isPeer || isMediaOnly) return@also
                                isSenderNameShown = true

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        modifier = if (onOpenSender != null) {
                                            Modifier.clickable(onClick = onOpenSender)
                                        } else {
                                            Modifier
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        text = name
                                    )

                                    message.senderEmojiStatusId?.let { emojiId ->
                                        EmojiStatusIcon(
                                            emojiId = emojiId,
                                            session = session,
                                            size = 14.dp
                                        )
                                    }
                                }
                            }

                            if(!isMediaOnly) {
                                reply?.let { data ->
                                    if(isSenderNameShown) {
                                        Spacer(Modifier.height(4.dp))
                                    }

                                    ReplyQuote(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        reply = data,
                                        thumbnail = replyThumbnail,
                                        isOutgoing = message.isOutgoing,
                                        onClick = onReplyClick?.let { click ->
                                            { click(data.messageId) }
                                        }
                                    )
                                }
                            }

                            if (isMediaOnly && mediaContent != null) {
                                Box {
                                    mediaContent()

                                    if(message.reactions.isEmpty()) {
                                        MessageFooter(
                                            message = message,
                                            onMedia = true,
                                            isChannel = dialogKind == DialogKind.Channel,
                                            customEmojiData = customEmojiData,
                                            customEmojiAdaptive = customEmojiAdaptive,
                                            session = session,
                                            onReaction = onReaction,
                                            onRetrySend = onRetrySend,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .then(
                                                    if (message.reactions.isNotEmpty()) {
                                                        Modifier.fillMaxWidth()
                                                    } else {
                                                        Modifier
                                                    }
                                                ).padding(4.dp)
                                        )
                                    }
                                }
                            } else {
                                if(mediaContent != null && isSenderNameShown) {
                                    Spacer(Modifier.height(4.dp))
                                }

                                Box(Modifier.align(if(message.isOutgoing) Alignment.End else Alignment.Start)) {
                                    mediaContent?.invoke()
                                }

                                if(mediaContent != null) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }

                            message.location?.let { location ->
                                LocationMessageCard(
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    location = location,
                                    isOutgoing = message.isOutgoing
                                )
                            }

                            message.linkPreview?.takeIf { it.showAboveText }?.let { preview ->
                                LinkPreviewCard(
                                    preview = preview,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            if (message.richBlocks.isNotEmpty()) {
                                SelectionContainer {
                                    RichMessageContent(
                                        modifier = Modifier.padding(bottom = 2.dp),
                                        blocks = message.richBlocks,
                                        isOutgoing = message.isOutgoing,
                                        onMentionClick = onMentionClick,
                                        onPhoneClick = onPhoneClick,
                                        onHashtagClick = onHashtagClick,
                                        media = richMedia
                                    )
                                }
                            } else if (message.text.isNotBlank()) {
                                SelectionContainer {
                                    CustomEmojiMessageText(
                                        modifier = Modifier.padding(bottom = 2.dp),
                                        lineHeight = 20.sp,
                                        text = message.text,
                                        emojis = message.customEmojis,
                                        data = customEmojiData,
                                        adaptiveEmojis = customEmojiAdaptive,
                                        onMentionClick = onMentionClick,
                                        onPhoneClick = onPhoneClick,
                                        onHashtagClick = onHashtagClick,
                                        mentionAvatars = mentionAvatars,
                                        trailingContent = if (isFooterInline) {
                                            {
                                                MessageStatusRow(
                                                    modifier = Modifier.padding(start = 6.dp),
                                                    message = message,
                                                    isChannel = dialogKind == DialogKind.Channel,
                                                    onRetrySend = onRetrySend
                                                )
                                            }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }

                            message.linkPreview?.takeIf { !it.showAboveText }?.let { preview ->
                                LinkPreviewCard(
                                    preview = preview,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (message.keyboard.isNotEmpty()) {
                                onOpenWebApp?.let { openWebApp ->
                                    MessageKeyboardButtons(
                                        modifier = Modifier.padding(top = 6.dp),
                                        keyboard = message.keyboard,
                                        onOpenWebApp = openWebApp
                                    )
                                }
                            }

                            // Channel posts expose their discussion thread right
                            // under the post instead of hiding it in the action
                            // menu, where most readers never found it.
                            if (
                                dialogKind == DialogKind.Channel &&
                                message.replyCount > 0 &&
                                onOpenThread != null
                            ) {
                                TextButton(
                                    modifier = Modifier.padding(top = 2.dp),
                                    onClick = onOpenThread
                                ) {
                                    Icon(
                                        modifier = Modifier.size(16.dp),
                                        imageVector = reply_icon,
                                        contentDescription = null
                                    )

                                    Text(
                                        modifier = Modifier.padding(start = 6.dp),
                                        text = if (message.replyCount == 1) {
                                            "1 comment"
                                        } else {
                                            "${message.replyCount} comments"
                                        }
                                    )
                                }
                            }

                            if (!isMediaOnly && !isFooterInline) {
                                MessageFooter(
                                    message = message,
                                    isChannel = dialogKind == DialogKind.Channel,
                                    customEmojiData = customEmojiData,
                                    customEmojiAdaptive = customEmojiAdaptive,
                                    session = session,
                                    onReaction = onReaction,
                                    onRetrySend = onRetrySend,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .then(
                                            if (message.reactions.isNotEmpty()) {
                                                Modifier.fillMaxWidth()
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                            
                            if(isMediaOnly && message.reactions.isNotEmpty()) {
                                MessageFooter(
                                    message = message,
                                    onMedia = true,
                                    isChannel = dialogKind == DialogKind.Channel,
                                    customEmojiData = customEmojiData,
                                    customEmojiAdaptive = customEmojiAdaptive,
                                    session = session,
                                    onReaction = onReaction,
                                    onRetrySend = onRetrySend,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .then(
                                            if (message.reactions.isNotEmpty()) {
                                                Modifier.fillMaxWidth()
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                        }

                        if(isMediaOnly && !message.isOutgoing) {
                            reply?.let { data ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    CompositionLocalProvider(
                                        LocalContentColor provides MaterialTheme.colorScheme.onSurface
                                    ) {
                                        ReplyQuote(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainer),
                                            reply = data,
                                            thumbnail = replyThumbnail,
                                            isOutgoing = false,
                                            onClick = onReplyClick?.let { click ->
                                                { click(data.messageId) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // While selecting messages, the bubble is covered by this
                // transparent layer: taps and long presses toggle the
                // selection, and embeds (stickers, polls, photos, files,
                // links) no longer react to input.
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(
                                onClick = { onToggleSelection?.invoke() },
                                onLongClick = { onToggleSelection?.invoke() }
                            )
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (onReaction != null && !isSending) {
                        ReactionPickerRow(
                            message = message,
                            data = customEmojiData,
                            adaptiveEmojis = customEmojiAdaptive,
                            session = session,
                            isChannel = dialogKind == DialogKind.Channel,
                            onReaction = { option ->
                                menuExpanded = false
                                onReaction(option)
                            }
                        )

                        HorizontalDivider()
                    }

                    FlexBox(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        @Composable
                        fun action(
                            text: String, 
                            icon: ImageVector, 
                            onClick: () -> Unit
                        ) {
                            TextButton(
                                shape = RectangleShape,
                                
                                modifier = Modifier.flex { 
                                    grow(1f)
                                },
                                
                                onClick = {
                                    menuExpanded = false
                                    onClick()
                                }
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        modifier = Modifier.size(24.dp),
                                        imageVector = icon,
                                        contentDescription = null
                                    )

                                    Text(
                                        style = MaterialTheme.typography.labelMedium,
                                        text = text
                                    )
                                }
                            }
                        }
                        
                        if (isSending) {
                            if (onCancelSend != null) {
                                action(
                                    text = "Cancel",
                                    icon = close_icon,
                                    onClick = onCancelSend
                                )
                            }
                        } else {
                            if (
                                message.sendState == MessageSendState.Failed &&
                                onRetrySend != null
                            ) {
                                action(
                                    text = "Retry",
                                    icon = done_all_icon,
                                    onClick = onRetrySend
                                )
                            }

                            if(onReply != null) {
                                action(
                                    text = "Reply",
                                    icon = reply_icon,
                                    onClick = onReply
                                )
                            }

                            if (onOpenThread != null) {
                                action(
                                    text = "Thread",
                                    icon = reply_icon,
                                    onClick = onOpenThread
                                )
                            }

                            if(onForward != null) {
                                action(
                                    text = "Forward",
                                    icon = forward_icon,
                                    onClick = onForward
                                )
                            }

                            if (message.text.isNotBlank() && !message.isService) {
                                action(
                                    text = "Copy",
                                    icon = content_copy_icon,
                                    onClick = {
                                        clipboard.setText(AnnotatedString(message.text))
                                    }
                                )
                            }
                        }
                    }
                    
                    @Composable
                    fun action(
                        text: String,
                        icon: ImageVector,
                        onClick: () -> Unit
                    ) {
                        DropdownMenuItem(
                            colors = MenuDefaults.itemColors(
                                leadingIconColor = MaterialTheme.colorScheme.primary,
                                textColor = MaterialTheme.colorScheme.primary
                            ),
                            
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null
                                )
                            },
                            
                            text = {
                                Text(
                                    text = text
                                )
                            },
                            
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                    }

                    if (!isSending) {
                        if (onPin != null) {
                            action(
                                text = if (isPinned) "Unpin" else "Pin",
                                icon = if(isPinned) keep_off_icon else keep_icon,
                                onClick = onPin
                            )
                        }

                        if (onEdit != null) {
                            action(
                                text = "Edit",
                                icon = edit_icon,
                                onClick = onEdit
                            )
                        }

                        if (onSelect != null) {
                            action(
                                text = "Select",
                                icon = select_check_box_icon,
                                onClick = onSelect
                            )
                        }

                        if (onDelete != null) {
                            action(
                                text = "Delete",
                                icon = delete_icon,
                                onClick = onDelete
                            )
                        }
                    }
                    
                    if (usedEmojiPacks.isNotEmpty() && onOpenStickerSet != null) {
                        HorizontalDivider()

                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            text = "Emoji packs used",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        usedEmojiPacks.forEach { pack ->
                            action(
                                text = pack.title,
                                icon = select_check_box_icon,
                                onClick = { onOpenStickerSet.invoke(pack.id) }
                            )
                        }
                    }

                    if(readTimestamp != null) {
                        HorizontalDivider()

                        MessageMenuTimestamps(
                            readTimestamp = readTimestamp
                        )   
                    }
                }
            }

            if (!message.isOutgoing) {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.widthIn(64.dp))
            }
        }
    }
}
