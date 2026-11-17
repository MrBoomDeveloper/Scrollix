package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.DialogKind
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class MessageBubbleConnection {
    NONE,
    BOTH,
    TOP,
    BOTTOM
}

@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    dialogKind: DialogKind,
    connection: MessageBubbleConnection,
    message: ChatMessage
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if(message.isOutgoing) {
            Spacer(Modifier.weight(1f))
        } else {
//            if(message.authorAvatar != null || (message.authorName?.length ?: 0) > 0) {
//                Avatar(
//                    modifier = Modifier
//                        .size(32.dp)
//                        .align(Alignment.Bottom),
//                    imageUrl = message.authorAvatar,
//                    fallbackText = message.authorName?.get(0)?.toString() ?: "?"
//                )
//            }
        }

        Surface(
            shape = when(connection) {
                MessageBubbleConnection.NONE -> RoundedCornerShape(32.dp)
                
                MessageBubbleConnection.BOTTOM -> RoundedCornerShape(
                    topStart = if(message.isOutgoing) 32.dp else 4.dp,
                    topEnd = if(message.isOutgoing) 4.dp else 32.dp,
                    bottomEnd = 32.dp,
                    bottomStart = 32.dp
                )
                
                MessageBubbleConnection.TOP -> RoundedCornerShape(
                    topStart = 32.dp,
                    topEnd = 32.dp,
                    bottomEnd = if(message.isOutgoing) 4.dp else 32.dp,
                    bottomStart = if(message.isOutgoing) 32.dp else 4.dp
                )
                
                MessageBubbleConnection.BOTH -> RoundedCornerShape(
                    topStart = if(message.isOutgoing) 32.dp else 4.dp,
                    topEnd = if(message.isOutgoing) 4.dp else 32.dp,
                    bottomEnd = if(message.isOutgoing) 4.dp else 32.dp,
                    bottomStart = if(message.isOutgoing) 32.dp else 4.dp
                )
            },

            color = if(message.isOutgoing) {
                MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                message.senderName?.also { name ->
                    if(message.isOutgoing || dialogKind.isPeer) return@also

                    Text(
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        text = name
                    )
                }

                if(message.text.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            modifier = Modifier.padding(bottom = 2.dp),
                            text = message.text
                        )
                    }
                }

//                if(message.attachments.isNotEmpty()) {
//                    Column(
//                        modifier = Modifier.padding(vertical = 4.dp),
//                        verticalArrangement = Arrangement.spacedBy(4.dp)
//                    ) {
//                        for(attachment in message.attachments) {
//                            Surface(
//                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
//                                shape = RoundedCornerShape(16.dp),
//                                onClick = {
//                                    uriHandler.openUri(attachment.url)
//                                }
//                            ) {
//                                if(attachment.type == DBMessage.Attachment.Type.IMAGE) {
//                                    val aspectRatio = if(attachment.width != null && attachment.height != null) {
//                                        attachment.width.toFloat() / attachment.height.toFloat()
//                                    } else null
//
//                                    AsyncImage(
//                                        modifier = Modifier
//                                            .clip(RoundedCornerShape(16.dp))
//                                            .fillMaxWidth()
//                                            .let { if(aspectRatio != null) it.aspectRatio(aspectRatio) else it },
//                                        model = attachment.url,
//                                        contentDescription = null
//                                    )
//
//                                    return@Surface
//                                }
//
//                                Row(
//                                    modifier = Modifier.padding(
//                                        start = 8.dp,
//                                        top = 6.dp,
//                                        end = 16.dp,
//                                        bottom = 8.dp
//                                    ),
//
//                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                                ) {
//                                    Box(
//                                        modifier = modifier
//                                            .padding(top = 4.dp)
//                                            .clip(RoundedCornerShape(8.dp))
//                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
//                                            .size(24.dp)
//                                    ) {
//                                        Icon(
//                                            modifier = Modifier
//                                                .fillMaxSize()
//                                                .padding(4.dp),
//                                            painter = painterResource(Res.drawable.ic_draft_outlined),
//                                            contentDescription = null
//                                        )
//                                    }
//
//                                    Column(
//                                        modifier = Modifier.padding(top = 0.dp),
//                                        verticalArrangement = Arrangement.spacedBy(2.dp)
//                                    ) {
//                                        Text(
//                                            style = MaterialTheme.typography.bodyMedium,
//                                            text = attachment.name
//                                        )
//
//                                        Text(
//                                            style = MaterialTheme.typography.bodySmall,
//                                            text = HumanReadable.fileSize(attachment.size)
//                                        )
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }

                Row(
                    modifier = Modifier
                        .align(if(message.isOutgoing) Alignment.End else Alignment.Start)
                        .height(16.dp)
                        .offset(x = if(message.isOutgoing) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
//                    if(message.state != DBMessage.State.FAILURE) {
                        Text(
                            style = MaterialTheme.typography.bodySmall,
                            text = Instant.fromEpochSeconds(message.timestamp / 1000).toLocalDateTime(TimeZone.currentSystemDefault()).let {
                                "${if(it.hour > 9) it.hour else "0" + it.hour}:${if(it.minute > 9) it.minute else "0" + it.minute}"
                            }
                        )
//                    }

                    if(message.isOutgoing) {
//                        when(message.state) {
//                            DBMessage.State.LOADING -> {
//                                CircularProgressIndicator(
//                                    modifier = Modifier.size(14.dp),
//                                    strokeWidth = 2.dp,
//                                    color = MaterialTheme.colorScheme.onPrimary
//                                )
//                            }
//
//                            DBMessage.State.SENT -> {
//                                Icon(
//                                    modifier = Modifier.size(16.dp),
//                                    tint = MaterialTheme.colorScheme.onPrimary,
//                                    painter = painterResource(Res.drawable.ic_check),
//                                    contentDescription = null
//                                )
//                            }
//
//                            DBMessage.State.RECEIVED -> {
//                                Icon(
//                                    modifier = Modifier.size(16.dp),
//                                    tint = MaterialTheme.colorScheme.onPrimary,
//                                    painter = painterResource(Res.drawable.ic_done_all),
//                                    contentDescription = null
//                                )
//                            }
//
//                            DBMessage.State.FAILURE -> {
//                                Text(
//                                    style = MaterialTheme.typography.bodySmall,
//                                    text = "Failed to send"
//                                )
//
//                                Icon(
//                                    modifier = Modifier.size(14.dp),
//                                    tint = MaterialTheme.colorScheme.onPrimary,
//                                    painter = painterResource(Res.drawable.ic_error_outlined),
//                                    contentDescription = null
//                                )
//                            }
//                        }
                    }
                }
            }
        }

        if(!message.isOutgoing) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.widthIn(64.dp))
        }
    }
}