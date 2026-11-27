package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatMessageThread
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.features.home.key

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Shows the replies of a message thread. Channel comments are returned from
 * the discussion chat, so the composer sends to [ChatMessageThread.chatId]
 * while still replying to the same root message.
 */
@Composable
fun MessageThreadDialog(
    rootMessage: ChatMessage,
    session: AccountSession,
    onDismiss: () -> Unit
) {
    var thread by remember(rootMessage.messageId) { mutableStateOf<ChatMessageThread?>(null) }
    var isLoading by remember(rootMessage.messageId) { mutableStateOf(true) }
    var error by remember(rootMessage.messageId) { mutableStateOf<String?>(null) }
    var input by remember(rootMessage.messageId) { mutableStateOf("") }
    var isSending by remember(rootMessage.messageId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            isLoading = thread == null
            error = null

            session.loadMessageThread(
                providerDialogId = rootMessage.providerDialogId,
                messageId = rootMessage.messageId
            ).onSuccess { loaded ->
                thread = loaded
            }.onFailure { failure ->
                error = failure.message ?: "Failed to load the thread"
            }

            isLoading = false
        }
    }

    LaunchedEffect(rootMessage.messageId) {
        reload()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable {},
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .fillMaxHeight(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Thread",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "${thread?.replyCount ?: rootMessage.replyCount} " +
                                    if ((thread?.replyCount ?: rootMessage.replyCount) == 1) {
                                        "reply"
                                    } else {
                                        "replies"
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }

                    error?.let { message ->
                        Text(
                            modifier = Modifier.padding(vertical = 4.dp),
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isLoading && thread == null -> CircularProgressIndicator(
                                modifier = Modifier.size(28.dp)
                            )

                            thread == null -> Text(
                                text = "No replies yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = thread?.messages.orEmpty(),
                                    key = { message -> message.rowId }
                                ) { message ->
                                    ThreadMessageRow(message)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.size(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Reply to thread…") },
                            maxLines = 4,
                            enabled = !isSending
                        )

                        TextButton(
                            enabled = input.isNotBlank() && !isSending,
                            onClick = {
                                val text = input.trim()
                                if (text.isEmpty()) return@TextButton

                                val targetChat = thread?.chatId
                                    ?: rootMessage.providerDialogId

                                scope.launch {
                                    isSending = true
                                    error = null

                                    session.sendMessageToThread(
                                        threadChatId = targetChat,
                                        rootMessageId = rootMessage.messageId,
                                        text = text
                                    ).onSuccess {
                                        input = ""
                                        // Load after the server accepted the
                                        // reply so the new message is included.
                                        session.loadMessageThread(
                                            providerDialogId = rootMessage.providerDialogId,
                                            messageId = rootMessage.messageId
                                        ).onSuccess { loaded -> thread = loaded }
                                    }.onFailure { failure ->
                                        error = failure.message ?: "Failed to send the reply"
                                    }

                                    isSending = false
                                }
                            }
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadMessageRow(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (message.isOutgoing) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!message.isOutgoing) {
                Text(
                    text = message.senderName ?: "Unknown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = message.text.ifBlank {
                    message.media?.fileName
                        ?: message.media?.let { media -> media.kind.name }
                        ?: "Attachment"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = threadTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun threadTime(timestampMillis: Long): String {
    val time = Instant.fromEpochMilliseconds(timestampMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    return "${time.hour.toString().padStart(2, '0')}:" +
        time.minute.toString().padStart(2, '0')
}
