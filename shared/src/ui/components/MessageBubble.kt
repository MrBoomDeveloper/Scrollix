package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiAsset
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DefaultQuickReactions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.mrboomdev.scrollix.ui.icons.check_icon
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
import com.mrboomdev.scrollix.ui.icons.share_icon
import com.mrboomdev.scrollix.ui.icons.visibility_icon
import io.github.alexzhirkevich.qrose.SquareShape
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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
    onDoubleTap: (() -> Unit)? = null,
    onOpenSender: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onToggleSelection: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onReaction: ((MessageReactionOption) -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onCancelSend: (() -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
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

                            if (message.richBlocks.isNotEmpty()) {
                                SelectionContainer {
                                    RichMessageContent(
                                        modifier = Modifier.padding(bottom = 2.dp),
                                        blocks = message.richBlocks,
                                        isOutgoing = message.isOutgoing,
                                        onMentionClick = onMentionClick,
                                        onPhoneClick = onPhoneClick,
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
                                        trailingContent = if (isFooterInline) {
                                            {
                                                MessageStatusRow(
                                                    modifier = Modifier.padding(start = 6.dp),
                                                    message = message,
                                                    isChannel = dialogKind == DialogKind.Channel
                                                )
                                            }
                                        } else {
                                            null
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
                            if(onReply != null) {
                                action(
                                    text = "Reply",
                                    icon = reply_icon,
                                    onClick = onReply
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

@Composable
private fun MessageMenuTimestamps(
    readTimestamp: Long?
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        readTimestamp?.let { timestamp ->
            Text(
                text = "Read ${formatMessageDateTime(timestamp)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatMessageDateTime(timestamp: Long): String {
    val time = Instant.fromEpochSeconds(timestamp / 1000)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val day = time.dayOfMonth.toString().padStart(2, '0')
    val month = time.monthNumber.toString().padStart(2, '0')
    val hour = time.hour.toString().padStart(2, '0')
    val minute = time.minute.toString().padStart(2, '0')

    return "$day.$month.${time.year}, $hour:$minute"
}

@Composable
private fun ReactionPickerRow(
    message: ChatMessage,
    data: Map<Long, ByteArray>,
    adaptiveEmojis: Set<Long> = emptySet(),
    session: AccountSession?,
    onReaction: (MessageReactionOption) -> Unit
) {
    var expanded by remember(message.messageId) { mutableStateOf(false) }
    var available by remember(message.messageId) {
        mutableStateOf<List<MessageReactionOption>>(emptyList())
    }
    var packs by remember(message.messageId) {
        mutableStateOf<List<StickerPack>>(emptyList())
    }
    var packItems by remember(message.messageId) {
        mutableStateOf<Map<Long, List<MessageMedia>>>(emptyMap())
    }

    val scope = rememberCoroutineScope()
    val settings by AppSettingsStore.state.collectAsState()

    LaunchedEffect(expanded, session, message.messageId) {
        if (!expanded) return@LaunchedEffect

        val currentSession = session ?: return@LaunchedEffect

        if (available.isEmpty()) {
            available = currentSession
                .loadAvailableReactions(message.providerDialogId, message.messageId)
                .getOrDefault(emptyList())
        }

        if (packs.isEmpty()) {
            val loadedPacks = currentSession
                .loadStickerPacks(StickerPackKind.CustomEmoji)
                .getOrDefault(emptyList())

            packs = loadedPacks
            packItems = coroutineScope {
                loadedPacks.map { pack ->
                    async {
                        pack.id to currentSession.loadStickerSet(pack.id)
                            .getOrNull()
                            ?.stickers
                            .orEmpty()
                    }
                }.awaitAll().toMap()
            }
        }
    }

    val quick = remember(
        settings.quickReactions,
        settings.pinnedCustomReactions
    ) {
        val pinned = settings.quickReactions.map(::MessageReactionOption) +
            settings.pinnedCustomReactions.mapNotNull { custom ->
                custom.customEmojiId?.let { id ->
                    MessageReactionOption(emoji = custom.emoji, customEmojiId = id)
                }
            }

        pinned.takeIf { it.isNotEmpty() }
            ?: DefaultQuickReactions.map(::MessageReactionOption)
    }

    val entries = remember(quick, available, packs, packItems) {
        buildList {
            add(
                ReactionMenuEntry.Available(
                    options = (quick + available).distinctBy { it.key }
                )
            )

            packs.forEach { pack ->
                add(ReactionMenuEntry.PackHeader(pack))

                packItems[pack.id].orEmpty().chunked(8).forEach { row ->
                    add(ReactionMenuEntry.PackRow(stickers = row))
                }
            }
        }
    }

    val packJumps = remember(packs) {
        packs.associate { pack -> pack.id to BringIntoViewRequester() }
    }

    fun isChosen(option: MessageReactionOption): Boolean {
        return message.reactions.any { reaction ->
            reaction.isChosen && if (option.customEmojiId != null) {
                reaction.customEmojiId == option.customEmojiId
            } else {
                reaction.customEmojiId == null && reaction.emoji == option.emoji
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (!expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                quick.forEach { option ->
                    ReactionPickerButton(
                        option = option,
                        chosen = isChosen(option),
                        data = data,
                        adaptiveEmojis = adaptiveEmojis,
                        session = session,
                        onClick = { onReaction(option) }
                    )
                }

                TextButton(onClick = { expanded = true }) {
                    Text("+")
                }
            }
        } else {
            // Menu content is measured with intrinsics, which lazy lists can't
            // provide, so everything is laid out plainly and the menu scrolls.
            if (packs.isNotEmpty()) {
                // A quick jump bar keeps every pack one tap away instead of
                // making the user scroll the whole collection.
                packs.chunked(6).forEach { rowPacks ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowPacks.forEach { pack ->
                            val cover = packItems[pack.id]?.firstOrNull()

                            TextButton(
                                onClick = {
                                    packJumps[pack.id]?.let { requester ->
                                        scope.launch { requester.bringIntoView() }
                                    }
                                }
                            ) {
                                if (cover != null && cover.stickerId != null) {
                                    ReactionGlyph(
                                        reaction = MessageReactionInfo(
                                            emoji = cover.stickerEmoji ?: "",
                                            count = 0,
                                            customEmojiId = cover.stickerId
                                        ),
                                        data = data,
                                        adaptiveEmojis = adaptiveEmojis,
                                        color = LocalContentColor.current,
                                        session = session
                                    )
                                }

                                Text(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .widthIn(max = 120.dp),
                                    text = pack.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is ReactionMenuEntry.Available -> FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            maxItemsInEachRow = 8
                        ) {
                            entry.options.forEach { option ->
                                ReactionPickerButton(
                                    option = option,
                                    chosen = isChosen(option),
                                    data = data,
                                    adaptiveEmojis = adaptiveEmojis,
                                    session = session,
                                    onClick = { onReaction(option) }
                                )
                            }
                        }

                        is ReactionMenuEntry.PackHeader -> Text(
                            modifier = Modifier
                                .then(
                                    packJumps[entry.pack.id]?.let { requester ->
                                        Modifier.bringIntoViewRequester(requester)
                                    } ?: Modifier
                                )
                                .padding(top = 6.dp, bottom = 2.dp),
                            text = entry.pack.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        is ReactionMenuEntry.PackRow -> FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            maxItemsInEachRow = 8
                        ) {
                            entry.stickers.forEach { sticker ->
                                val emojiId = sticker.stickerId
                                val emoji = sticker.stickerEmoji

                                val option = when {
                                    emojiId != null -> MessageReactionOption(
                                        emoji = emoji ?: "",
                                        customEmojiId = emojiId
                                    )

                                    !emoji.isNullOrBlank() -> MessageReactionOption(emoji)
                                    else -> null
                                } ?: return@forEach

                                ReactionPickerButton(
                                    option = option,
                                    chosen = isChosen(option),
                                    data = data,
                                    adaptiveEmojis = adaptiveEmojis,
                                    session = session,
                                    onClick = { onReaction(option) }
                                )
                            }
                        }
                    }
                }
            }

            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = { expanded = false }
            ) {
                Text("Less")
            }
        }
    }
}

private sealed interface ReactionMenuEntry {
    data class Available(
        val options: List<MessageReactionOption>
    ) : ReactionMenuEntry

    data class PackHeader(
        val pack: StickerPack
    ) : ReactionMenuEntry

    data class PackRow(
        val stickers: List<MessageMedia>
    ) : ReactionMenuEntry
}

@Composable
private fun ReactionPickerButton(
    option: MessageReactionOption,
    chosen: Boolean,
    data: Map<Long, ByteArray>,
    adaptiveEmojis: Set<Long> = emptySet(),
    session: AccountSession?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (chosen) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        } else {
            Color.Transparent
        }
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            ReactionGlyph(
                reaction = MessageReactionInfo(
                    emoji = option.emoji,
                    count = 0,
                    customEmojiId = option.customEmojiId
                ),
                data = data,
                adaptiveEmojis = adaptiveEmojis,
                color = LocalContentColor.current,
                session = session
            )
        }
    }
}

@Composable
private fun ReplyQuote(
    modifier: Modifier = Modifier,
    reply: MessageReply,
    thumbnail: (@Composable () -> Unit)? = null,
    isOutgoing: Boolean,
    onClick: (() -> Unit)? = null
) {
    val accent = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.12f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .height(IntrinsicSize.Min)
    ) {
        Spacer(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )

        if (thumbnail != null) {
            Box(
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                thumbnail()
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            reply.senderName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = reply.text.ifBlank { "Message" },
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MessageFooter(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
    isChannel: Boolean = false,
    customEmojiData: Map<Long, ByteArray> = emptyMap(),
    customEmojiAdaptive: Set<Long> = emptySet(),
    session: AccountSession? = null,
    onReaction: ((MessageReactionOption) -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp, if (message.reactions.isNotEmpty()) {
            Alignment.Start
        } else Alignment.End),
        verticalAlignment = Alignment.Bottom
    ) {
        if (message.reactions.isNotEmpty()) {
            MessageReactionsRow(
                modifier = Modifier.padding(top = 4.dp),
                reactions = message.reactions,
                isOutgoing = message.isOutgoing,
                onMedia = onMedia,
                showAvatars = !isChannel && message.reactions.sumOf { it.count } < 4,
                customEmojiData = customEmojiData,
                customEmojiAdaptive = customEmojiAdaptive,
                session = session,
                onReaction = onReaction
            )
        }
        
        Spacer(Modifier.weight(1f))

        MessageStatusRow(
            message = message,
            onMedia = onMedia,
            isChannel = isChannel
        )
    }
}

@Composable
private fun MessageReactionsRow(
    modifier: Modifier = Modifier,
    reactions: List<MessageReactionInfo>,
    isOutgoing: Boolean,
    onMedia: Boolean,
    showAvatars: Boolean = false,
    customEmojiData: Map<Long, ByteArray> = emptyMap(),
    customEmojiAdaptive: Set<Long> = emptySet(),
    session: AccountSession? = null,
    onReaction: ((MessageReactionOption) -> Unit)? = null
) {
    val accent = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val textColor = when {
        onMedia -> Color.White
        isOutgoing -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Row(
        modifier = modifier.then(if(onMedia) Modifier else {
            Modifier.padding(end = 8.dp)
        }),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        reactions.take(6).forEach { reaction ->
            val chipColor = if (onMedia) Color.Black else accent
            val chipAlpha = if (onMedia) {
                if (reaction.isChosen) 0.55f else 0.35f
            } else {
                if (reaction.isChosen) 0.28f else 0.12f
            }

            Surface(
                modifier = if (onReaction != null) {
                    Modifier.clickable {
                        onReaction(
                            MessageReactionOption(
                                emoji = reaction.emoji,
                                customEmojiId = reaction.customEmojiId
                            )
                        )
                    }
                } else Modifier,
                shape = RoundedCornerShape(12.dp),
                color = if(isOutgoing) {
                    chipColor.copy(alpha = chipAlpha)
                } else MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReactionGlyph(
                        reaction = reaction,
                        data = customEmojiData,
                        adaptiveEmojis = customEmojiAdaptive,
                        color = textColor,
                        session = session
                    )

                    if (showAvatars && reaction.reactorAvatars.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-4).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            reaction.reactorAvatars
                                .take(reaction.count)
                                .forEach { avatar ->
                                    AvatarImage(avatar = avatar, size = 16.dp)
                                }
                        }
                    } else {
                        Text(
                            text = reaction.count.toString(),
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionGlyph(
    reaction: MessageReactionInfo,
    data: Map<Long, ByteArray>,
    adaptiveEmojis: Set<Long> = emptySet(),
    color: Color,
    session: AccountSession?
) {
    val emojiId = reaction.customEmojiId
    val cached = emojiId?.let(data::get)
    var loaded by remember(emojiId) { mutableStateOf<CustomEmojiAsset?>(null) }
    var loadFinished by remember(emojiId) { mutableStateOf(false) }

    // The dialog preloads emoji for every message, but a reaction can arrive on its own,
    // so the glyph also fetches its asset when nothing has been provided yet.
    LaunchedEffect(emojiId, session) {
        if (emojiId != null && cached == null && loaded == null) {
            loadFinished = false
            loaded = session?.loadCustomEmoji(emojiId)
            loadFinished = true
        }
    }

    val bytes = cached ?: loaded?.data
    val needsRepainting = if (cached != null) {
        emojiId != null && emojiId in adaptiveEmojis
    } else {
        loaded?.needsRepainting == true
    }

    if (emojiId != null) {
        if (bytes != null) {
            FolderEmojiImage(
                emoji = remember(emojiId, bytes, needsRepainting) {
                    customEmojiModel(emojiId, bytes, needsRepainting)
                },
                animate = true,
                size = 20.dp,
                color = Color.Unspecified,
                contentModifier = Modifier.size(20.dp)
            )
        } else if (!loadFinished) {
            // Keep the chip size stable while the custom emoji sticker is being fetched.
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = color.copy(alpha = 0.6f)
                )
            }
        } else {
            Text(
                text = "•",
                color = color,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        Text(
            text = reaction.emoji,
            color = color,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MessageStatusRow(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
    isChannel: Boolean = false
) {
    val contentColor = if (onMedia) Color.White else Color.Unspecified

    Row(
        modifier = modifier
            .then(
                if (onMedia) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isChannel) {
            if (message.viewCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = visibility_icon,
                        contentDescription = null
                    )

                    Text(
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        text = message.viewCount.toString()
                    )
                }
            }
        }

        if (message.shareCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = share_icon,
                    contentDescription = null
                )

                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    text = message.shareCount.toString()
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                text = Instant.fromEpochSeconds(message.timestamp / 1000)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .let { time ->
                        "${if(time.hour > 9) time.hour else "0" + time.hour}:" +
                                "${if(time.minute > 9) time.minute else "0" + time.minute}"
                    }
            )

            if(message.isOutgoing) {
                when (message.sendState) {
                    MessageSendState.Sending -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = if(onMedia) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )

                    MessageSendState.Failed -> Text(
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        text = "Not sent"
                    )

                    MessageSendState.Sent -> Icon(
                        modifier = Modifier.size(14.dp),
                        tint = when {
                            onMedia -> Color.White.copy(alpha = if(message.isRead) 1f else 0.6f)
                            message.isRead -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        },
                        imageVector = if(message.isRead) done_all_icon else check_icon,
                        contentDescription = if(message.isRead) "Read" else "Sent"
                    )
                }
            }
        }
    }
}

private val LinkRegex = Regex(
    """(https?://[^\s]+|www\.[^\s]+)|(?<![\w@])@[A-Za-z][A-Za-z0-9_]{2,31}|(?<![\w+])\+?\d[\d\s().-]{7,}\d(?!\w)""",
    RegexOption.IGNORE_CASE
)

internal fun AnnotatedString.Builder.appendTextWithLinks(
    text: String,
    onMentionClick: ((String) -> Unit)? = null,
    onPhoneClick: ((String) -> Unit)? = null
) {
    var index = 0

    LinkRegex.findAll(text).forEach { match ->
        val linkText = match.value
        val isMention = linkText.startsWith("@")
        val isUrl = linkText.startsWith("http", ignoreCase = true) ||
            linkText.startsWith("www.", ignoreCase = true)
        val isPhone = !isMention && !isUrl

        // Mentions and phone numbers are only rendered as links when someone
        // can react to them.
        if (isMention && onMentionClick == null) return@forEach
        if (isPhone && onPhoneClick == null) return@forEach

        if (match.range.first > index) {
            append(text.substring(index, match.range.first))
        }

        val styles = TextLinkStyles(
            style = SpanStyle(textDecoration = TextDecoration.Underline)
        )

        when {
            isMention -> {
                val username = linkText.removePrefix("@")

                withLink(
                    LinkAnnotation.Clickable(
                        tag = "mention:$username",
                        styles = styles,
                        linkInteractionListener = { onMentionClick?.invoke(username) }
                    )
                ) {
                    append(linkText)
                }
            }

            isUrl -> {
                val url = if (linkText.startsWith("http", ignoreCase = true)) {
                    linkText
                } else {
                    "https://$linkText"
                }

                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = styles
                    )
                ) {
                    append(linkText)
                }
            }

            else -> {
                val phone = linkText.filter { character ->
                    character.isDigit() || character == '+'
                }

                withLink(
                    LinkAnnotation.Clickable(
                        tag = "phone:$phone",
                        styles = styles,
                        linkInteractionListener = { onPhoneClick?.invoke(linkText) }
                    )
                ) {
                    append(linkText)
                }
            }
        }

        index = match.range.last + 1
    }

    if (index < text.length) {
        append(text.substring(index))
    }
}

private fun formatCount(count: Int): String {
    return count.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}
