package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.ui.icons.check_icon
import com.mrboomdev.scrollix.ui.icons.done_all_icon
import com.mrboomdev.scrollix.ui.onSecondaryClick
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class MessageBubbleConnection {
    NONE,
    BOTH,
    TOP,
    BOTTOM
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    dialogKind: DialogKind,
    connection: MessageBubbleConnection,
    message: ChatMessage,
    mediaContent: (@Composable () -> Unit)? = null,
    customEmojiData: Map<Long, ByteArray> = emptyMap(),
    reply: MessageReply? = null,
    onOpenSender: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember(message.messageId) { mutableStateOf(false) }
    val isMediaOnly = message.replyTo == null && message.text.isBlank() && (
        message.media?.kind == MessageMediaKind.Sticker ||
            message.media?.kind == MessageMediaKind.VideoNote ||
            message.media?.kind == MessageMediaKind.Animation
    )
    val media = mediaContent

    BoxWithConstraints(modifier = modifier) {
        val maxBubbleWidth = maxWidth * 0.75f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                    size = 32.dp
                )
            }

            Box(
                modifier = Modifier.widthIn(max = maxBubbleWidth)
            ) {
                Surface(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuExpanded = true }
                        )
                        .onSecondaryClick { menuExpanded = true },

                    shape = when (connection) {
                        MessageBubbleConnection.NONE -> RoundedCornerShape(32.dp)

                        MessageBubbleConnection.BOTTOM -> RoundedCornerShape(
                            topStart = if (message.isOutgoing) 32.dp else 4.dp,
                            topEnd = if (message.isOutgoing) 4.dp else 32.dp,
                            bottomEnd = 32.dp,
                            bottomStart = 32.dp
                        )

                        MessageBubbleConnection.TOP -> RoundedCornerShape(
                            topStart = 32.dp,
                            topEnd = 32.dp,
                            bottomEnd = if (message.isOutgoing) 4.dp else 32.dp,
                            bottomStart = if (message.isOutgoing) 32.dp else 4.dp
                        )

                        MessageBubbleConnection.BOTH -> RoundedCornerShape(
                            topStart = if (message.isOutgoing) 32.dp else 4.dp,
                            topEnd = if (message.isOutgoing) 4.dp else 32.dp,
                            bottomEnd = if (message.isOutgoing) 4.dp else 32.dp,
                            bottomStart = if (message.isOutgoing) 32.dp else 4.dp
                        )
                    },

                    color = when {
                        isMediaOnly -> Color.Transparent
                        message.isOutgoing -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(
                            if (isMediaOnly) {
                                PaddingValues(0.dp)
                            } else {
                                PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            }
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        message.senderName?.also { name ->
                            if (message.isOutgoing || dialogKind.isPeer) return@also

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
                        }

                        reply?.let { data ->
                            ReplyQuote(reply = data, isOutgoing = message.isOutgoing)
                        }

                        if (isMediaOnly && media != null) {
                            Box {
                                media()

                                MessageFooter(
                                    message = message,
                                    onMedia = true,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                )
                            }
                        } else {
                            media?.invoke()
                        }

                        if (message.text.isNotBlank()) {
                            if (message.customEmojis.isEmpty() || customEmojiData.isEmpty()) {
                                Text(
                                    modifier = Modifier.padding(bottom = 2.dp),
                                    text = message.text
                                )
                            } else {
                                CustomEmojiMessageText(
                                    modifier = Modifier.padding(bottom = 2.dp),
                                    text = message.text,
                                    emojis = message.customEmojis,
                                    data = customEmojiData
                                )
                            }
                        }

                        if (!isMediaOnly) {
                            MessageFooter(
                                message = message,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .then(
                                        if (message.reactions.isNotEmpty()) {
                                            Modifier.fillMaxWidth()
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (onEdit != null) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                    }

                    if (message.text.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Copy text") },
                            onClick = {
                                menuExpanded = false
                                clipboard.setText(AnnotatedString(message.text))
                            }
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
private fun ReplyQuote(
    reply: MessageReply,
    isOutgoing: Boolean
) {
    val accent = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.12f))
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )

        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            reply.senderName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = reply.text.ifBlank { "Message" },
                style = MaterialTheme.typography.bodySmall,
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
    onMedia: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (message.reactions.isNotEmpty()) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.End
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (message.reactions.isNotEmpty()) {
            MessageReactionsRow(
                reactions = message.reactions,
                isOutgoing = message.isOutgoing,
                onMedia = onMedia
            )
        }

        MessageStatusRow(
            message = message,
            onMedia = onMedia
        )
    }
}

@Composable
private fun MessageReactionsRow(
    reactions: List<MessageReactionInfo>,
    isOutgoing: Boolean,
    onMedia: Boolean
) {
    val accent = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val textColor = when {
        onMedia -> Color.White
        isOutgoing -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
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
                shape = RoundedCornerShape(12.dp),
                color = chipColor.copy(alpha = chipAlpha)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reaction.emoji,
                        color = textColor,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = reaction.count.toString(),
                        color = textColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageStatusRow(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false
) {
    val contentColor = if (onMedia) Color.White else Color.Unspecified

    Row(
        modifier = modifier
            .then(
                if (onMedia) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            text = Instant.fromEpochSeconds(message.timestamp / 1000)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .let { time ->
                    "${if (time.hour > 9) time.hour else "0" + time.hour}:" +
                        "${if (time.minute > 9) time.minute else "0" + time.minute}"
                }
        )

        if (message.isOutgoing) {
            when (message.sendState) {
                MessageSendState.Sending -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = if (onMedia) {
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
                        onMedia -> Color.White.copy(alpha = if (message.isRead) 1f else 0.6f)
                        message.isRead -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    },
                    imageVector = if (message.isRead) done_all_icon else check_icon,
                    contentDescription = if (message.isRead) "Read" else "Sent"
                )
            }
        }
    }
}
