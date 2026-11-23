package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatTopic
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Lists the forum topics of a chat and opens one of them. A topic is a chat of
 * its own on the server: history is fetched with the topic id and messages are
 * sent into the topic instead of the general chat.
 */
@Composable
fun ForumTopicsDialog(
    session: AccountSession,
    providerDialogId: String,
    title: String,
    onDismiss: () -> Unit
) {
    var topics by remember(providerDialogId) { mutableStateOf<List<ChatTopic>>(emptyList()) }
    var isLoading by remember(providerDialogId) { mutableStateOf(true) }
    var error by remember(providerDialogId) { mutableStateOf<String?>(null) }
    var selected by remember(providerDialogId) { mutableStateOf<ChatTopic?>(null) }

    suspend fun reload() {
        isLoading = true
        error = null

        session.loadTopics(providerDialogId)
            .onSuccess { loaded -> topics = loaded }
            .onFailure { failure ->
                error = failure.message ?: "Failed to load topics"
            }

        isLoading = false
    }

    LaunchedEffect(providerDialogId, session) {
        reload()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            val topic = selected

            if (topic == null) {
                TopicsList(
                    title = title,
                    topics = topics,
                    isLoading = isLoading,
                    error = error,
                    onSelect = { selected = it },
                    onRetry = { /* reloaded by the effect below */ }
                )

                LaunchedEffect(error) {
                    if (error != null) reload()
                }
            } else {
                TopicMessages(
                    session = session,
                    providerDialogId = providerDialogId,
                    topic = topic,
                    onBack = { selected = null }
                )
            }
        }
    }
}

@Composable
private fun TopicsList(
    title: String,
    topics: List<ChatTopic>,
    isLoading: Boolean,
    error: String?,
    onSelect: (ChatTopic) -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
            text = "$title · Topics",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        when {
            isLoading && topics.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            topics.isEmpty() -> Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = error ?: "This chat has no topics yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )

                if (error != null) {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(topics, key = { topic -> topic.id }) { topic ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(topic) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = topic.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (topic.lastMessageText.isNotBlank()) {
                                Text(
                                    text = topic.lastMessageText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (topic.unreadCount > 0) {
                            Text(
                                text = topic.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        if (topic.isClosed) {
                            Text(
                                text = "Closed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                }
            }
        }
    }
}

@Composable
private fun TopicMessages(
    session: AccountSession,
    providerDialogId: String,
    topic: ChatTopic,
    onBack: () -> Unit
) {
    var messages by remember(topic.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember(topic.id) { mutableStateOf(true) }
    var error by remember(topic.id) { mutableStateOf<String?>(null) }
    var input by remember(topic.id) { mutableStateOf("") }
    var isSending by remember(topic.id) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun load() {
        isLoading = true

        session.loadTopicMessages(providerDialogId, topic.id)
            .onSuccess { loaded ->
                messages = loaded
                error = null
            }
            .onFailure { failure ->
                error = failure.message ?: "Failed to load this topic"
            }

        isLoading = false
    }

    LaunchedEffect(topic.id) {
        load()
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || isSending) return

        isSending = true

        scope.launch {
            session.sendTopicMessage(providerDialogId, topic.id, text)
                .onSuccess {
                    input = ""
                    load()
                }
                .onFailure { failure ->
                    error = failure.message ?: "Failed to send the message"
                }

            isSending = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = arrow_back_icon,
                    contentDescription = "Back to topics"
                )
            }

            Text(
                modifier = Modifier.weight(1f),
                text = topic.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isLoading && messages.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )

                messages.isEmpty() -> Text(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    text = error ?: "No messages in this topic yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { message -> message.messageId }) { message ->
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = message.senderName ?: "Message",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = formatTopicTime(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (message.text.isNotBlank()) {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        error.takeIf { messages.isNotEmpty() }?.let { message ->
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = input,
                onValueChange = { value -> input = value },
                placeholder = { Text("Message") },
                maxLines = 4
            )

            TextButton(
                enabled = input.isNotBlank() && !isSending,
                onClick = { send() }
            ) {
                Text("Send")
            }
        }
    }
}

private fun formatTopicTime(timestamp: Long): String {
    val time = Instant.fromEpochSeconds(timestamp / 1000)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val hour = time.hour.toString().padStart(2, '0')
    val minute = time.minute.toString().padStart(2, '0')

    return "$hour:$minute"
}
