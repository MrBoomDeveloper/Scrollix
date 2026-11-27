package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatTopic
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.features.home.key
import com.mrboomdev.scrollix.ui.icons.add_icon
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    var query by remember(providerDialogId) { mutableStateOf("") }
    var creating by remember(providerDialogId) { mutableStateOf(false) }
    var renaming by remember(providerDialogId) { mutableStateOf<ChatTopic?>(null) }
    var titleInput by remember(providerDialogId) { mutableStateOf("") }
    var actionError by remember(providerDialogId) { mutableStateOf<String?>(null) }
    var busyTopicId by remember(providerDialogId) { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        isLoading = true
        error = null

        session.loadTopics(providerDialogId, query.trim())
            .onSuccess { loaded -> topics = loaded }
            .onFailure { failure ->
                error = failure.message ?: "Failed to load topics"
            }

        isLoading = false
    }

    // Topic search runs on the server. The pause keeps typing from sending a
    // request per keystroke, and clearing the field reloads the full list.
    LaunchedEffect(providerDialogId, session, query) {
        if (query.isNotBlank()) {
            kotlinx.coroutines.delay(300)
        }

        reload()
    }

    // Every management action reloads the list on success, so a rename or a
    // new topic appears without closing and reopening the dialog. Failures
    // stay in the dialog next to the field that caused them.
    fun runTopicAction(
        topicId: Long,
        onSuccess: () -> Unit = {},
        block: suspend () -> Result<Unit>
    ) {
        if (busyTopicId != null) return

        busyTopicId = topicId
        actionError = null

        scope.launch {
            block()
                .onSuccess {
                    onSuccess()
                    reload()
                }
                .onFailure { failure ->
                    actionError = failure.message ?: "Failed to update the topic"
                }

            busyTopicId = null
        }
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
                    query = query,
                    onQueryChange = { query = it },
                    actionError = actionError,
                    canManage = session.supportsTopicManagement,
                    busyTopicId = busyTopicId,
                    onSelect = { selected = it },
                    onCreate = {
                        titleInput = ""
                        actionError = null
                        creating = true
                    },
                    onRename = { topic ->
                        titleInput = topic.title
                        actionError = null
                        renaming = topic
                    },
                    onToggleClosed = { topic ->
                        runTopicAction(topic.id) {
                            session.setForumTopicClosed(
                                providerDialogId = providerDialogId,
                                topicId = topic.id,
                                closed = !topic.isClosed
                            )
                        }
                    },
                    onTogglePinned = { topic ->
                        runTopicAction(topic.id) {
                            session.setForumTopicPinned(
                                providerDialogId = providerDialogId,
                                topicId = topic.id,
                                pinned = !topic.isPinned
                            )
                        }
                    },
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

    if (creating) {
        TopicTitleDialog(
            title = "New topic",
            confirmLabel = "Create",
            value = titleInput,
            isBusy = busyTopicId != null,
            error = actionError,
            onValueChange = { titleInput = it },
            onConfirm = {
                val name = titleInput.trim()

                if (name.isNotEmpty()) {
                    runTopicAction(
                        topicId = 0L,
                        onSuccess = {
                            creating = false
                            titleInput = ""
                        }
                    ) {
                        session.createForumTopic(providerDialogId, name).map { }
                    }
                }
            },
            onDismiss = {
                if (busyTopicId == null) {
                    creating = false
                    actionError = null
                }
            }
        )
    }

    renaming?.let { topic ->
        TopicTitleDialog(
            title = "Rename topic",
            confirmLabel = "Save",
            value = titleInput,
            isBusy = busyTopicId != null,
            error = actionError,
            onValueChange = { titleInput = it },
            onConfirm = {
                val name = titleInput.trim()

                if (name.isNotEmpty()) {
                    runTopicAction(
                        topicId = topic.id,
                        onSuccess = {
                            renaming = null
                            titleInput = ""
                        }
                    ) {
                        session.editForumTopicTitle(providerDialogId, topic.id, name)
                    }
                }
            },
            onDismiss = {
                if (busyTopicId == null) {
                    renaming = null
                    actionError = null
                }
            }
        )
    }
}

@Composable
private fun TopicsList(
    title: String,
    topics: List<ChatTopic>,
    isLoading: Boolean,
    error: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    actionError: String?,
    canManage: Boolean,
    busyTopicId: Long?,
    onSelect: (ChatTopic) -> Unit,
    onCreate: () -> Unit,
    onRename: (ChatTopic) -> Unit,
    onToggleClosed: (ChatTopic) -> Unit,
    onTogglePinned: (ChatTopic) -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "$title · Topics",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (canManage) {
                TextButton(onClick = onCreate) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = add_icon,
                        contentDescription = null
                    )

                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = "New"
                    )
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search topics") },
            singleLine = true
        )

        actionError?.let { message ->
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

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
                    text = error ?: if (query.isNotBlank()) {
                        "No topics match \"$query\"."
                    } else {
                        "This chat has no topics yet."
                    },
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

                        // The General topic is a fixed chat thread: Telegram
                        // rejects renaming, closing and pinning it, so those
                        // actions are not offered at all.
                        if (canManage && !topic.isGeneral) {
                            if (busyTopicId == topic.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Box {
                                    var expanded by remember(topic.id) {
                                        mutableStateOf(false)
                                    }

                                    IconButton(onClick = { expanded = true }) {
                                        Icon(
                                            imageVector = more_vertical_icon,
                                            contentDescription = "Topic actions"
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                expanded = false
                                                onRename(topic)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (topic.isClosed) {
                                                        "Reopen"
                                                    } else {
                                                        "Close"
                                                    }
                                                )
                                            },
                                            onClick = {
                                                expanded = false
                                                onToggleClosed(topic)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (topic.isPinned) {
                                                        "Unpin"
                                                    } else {
                                                        "Pin"
                                                    }
                                                )
                                            },
                                            onClick = {
                                                expanded = false
                                                onTogglePinned(topic)
                                            }
                                        )
                                    }
                                }
                            }
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

@Composable
private fun TopicTitleDialog(
    title: String,
    confirmLabel: String,
    value: String,
    isBusy: Boolean,
    error: String?,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Topic name") },
                    singleLine = true
                )

                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank() && !isBusy,
                onClick = onConfirm
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTopicTime(timestamp: Long): String {
    val time = Instant.fromEpochSeconds(timestamp / 1000)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val hour = time.hour.toString().padStart(2, '0')
    val minute = time.minute.toString().padStart(2, '0')

    return "$hour:$minute"
}
