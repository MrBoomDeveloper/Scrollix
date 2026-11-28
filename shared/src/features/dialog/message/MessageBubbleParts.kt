package com.mrboomdev.scrollix.features.dialog.message

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiAsset
import com.mrboomdev.scrollix.data.model.MessageLinkPreview
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.features.dialog.formatAutoDeleteRemaining
import com.mrboomdev.scrollix.features.home.FolderEmojiImage
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.check_icon
import com.mrboomdev.scrollix.ui.icons.done_all_icon
import com.mrboomdev.scrollix.ui.icons.schedule_icon
import com.mrboomdev.scrollix.ui.icons.share_icon
import com.mrboomdev.scrollix.ui.icons.visibility_icon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun formatMessageDateTime(timestamp: Long): String {
    val time = Instant.fromEpochSeconds(timestamp / 1000)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val day = time.dayOfMonth.toString().padStart(2, '0')
    val month = time.monthNumber.toString().padStart(2, '0')
    val hour = time.hour.toString().padStart(2, '0')
    val minute = time.minute.toString().padStart(2, '0')

    return "$day.$month.${time.year}, $hour:$minute"
}

@Composable
internal fun MessageFooter(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
    isChannel: Boolean = false,
    customEmojiData: Map<Long, ByteArray> = emptyMap(),
    customEmojiAdaptive: Set<Long> = emptySet(),
    session: AccountSession? = null,
    onReaction: ((MessageReactionOption) -> Unit)? = null,
    /** Opens the list of accounts behind a reaction; long-press on a chip. */
    onShowReactions: ((MessageReactionInfo) -> Unit)? = null,
    onRetrySend: (() -> Unit)? = null
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
                onReaction = onReaction,
                onShowReactions = onShowReactions
            )
        }
        
        Spacer(Modifier.weight(1f))

        MessageStatusRow(
            message = message,
            onMedia = onMedia,
            isChannel = isChannel,
            onRetrySend = onRetrySend
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageReactionsRow(
    modifier: Modifier = Modifier,
    reactions: List<MessageReactionInfo>,
    isOutgoing: Boolean,
    onMedia: Boolean,
    showAvatars: Boolean = false,
    customEmojiData: Map<Long, ByteArray> = emptyMap(),
    customEmojiAdaptive: Set<Long> = emptySet(),
    session: AccountSession? = null,
    onReaction: ((MessageReactionOption) -> Unit)? = null,
    onShowReactions: ((MessageReactionInfo) -> Unit)? = null
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
                modifier = when {
                    onReaction != null || onShowReactions != null -> Modifier.combinedClickable(
                        onClick = {
                            onReaction?.invoke(
                                MessageReactionOption(
                                    emoji = reaction.emoji,
                                    customEmojiId = reaction.customEmojiId
                                )
                            )
                        },
                        onLongClick = { onShowReactions?.invoke(reaction) }
                    )

                    else -> Modifier
                },
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
internal fun ReactionGlyph(
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
internal fun MessageStatusRow(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
    isChannel: Boolean = false,
    onRetrySend: (() -> Unit)? = null
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
                    MessageSendState.Sending -> {
                        val upload = message.uploadProgress

                        if (upload != null) {
                            Text(
                                style = MaterialTheme.typography.bodySmall,
                                color = if (onMedia) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                text = "${(upload * 100).toInt()}%"
                            )
                        } else {
                            Icon(
                                modifier = Modifier.size(14.dp),
                                tint = if (onMedia) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                imageVector = schedule_icon,
                                contentDescription = "Sending"
                            )
                        }
                    }

                    MessageSendState.Failed -> Text(
                        modifier = if (onRetrySend != null) {
                            Modifier.clickable(onClick = onRetrySend)
                        } else {
                            Modifier
                        },
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

/** Link preview card shown inside the message bubble. */
@Composable
internal fun LinkPreviewCard(
    preview: MessageLinkPreview,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (com.mrboomdev.scrollix.DeepLinkHandler.isTelegramLink(preview.url)) {
                    com.mrboomdev.scrollix.DeepLinkHandler.publish(preview.url)
                } else {
                    runCatching { uriHandler.openUri(preview.url) }
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            val source = preview.siteName.ifBlank { preview.displayUrl }

            if (source.isNotBlank()) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (preview.title.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = preview.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (preview.description.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = preview.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun MessageMenuTimestamps(
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

@Composable
internal fun ReplyQuote(
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

/**
 * Countdown of a message that deletes itself. The label is recomputed every
 * second from the absolute deadline, not decremented, so a paused app or a
 * clock change never leaves a wrong number on screen. When the deadline is
 * reached the row disappears; the provider then removes the message itself
 * and the history update drops the bubble.
 */
@Composable
internal fun AutoDeleteCountdown(
    autoDeleteAt: Long,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    val now = remember(autoDeleteAt) {
        mutableStateOf(com.mrboomdev.scrollix.data.storage.currentTimeMillis())
    }

    LaunchedEffect(autoDeleteAt) {
        while (true) {
            val current = com.mrboomdev.scrollix.data.storage.currentTimeMillis()
            now.value = current
            if (current >= autoDeleteAt) break
            kotlinx.coroutines.delay(1000)
        }
    }

    val label = formatAutoDeleteRemaining(autoDeleteAt - now.value) ?: return

    Text(
        modifier = modifier,
        text = "Deletes in $label",
        style = MaterialTheme.typography.labelSmall,
        color = if (isOutgoing) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.secondary
        }
    )
}
