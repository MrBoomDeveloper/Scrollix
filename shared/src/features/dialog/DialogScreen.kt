package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun DialogScreen(
    accountId: String,
    providerDialogId: String,
    onBack: () -> Unit
) {
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

    LaunchedEffect(session, providerDialogId) {
        error = null
        isLoading = true

        if (session == null) {
            error = "Account session not found"
            isLoading = false
            return@LaunchedEffect
        }

        session.loadMessages(providerDialogId)
            .onFailure { error = it.message ?: "Failed to load messages" }
        isLoading = false
    }

    LaunchedEffect(messages.size, isLoading) {
        if (!isLoading && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
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
            IconButton(onClick = onBack) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = arrow_back_icon,
                    contentDescription = "Back"
                )
            }

            AvatarImage(
                avatar = dialog?.avatar ?: Avatar.fromName(dialog?.title ?: "Dialog"),
                size = 40.dp
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
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
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.messageId }
                        ) { message ->
                            MessageBubble(
                                message = message,
                                dialogKind = dialog?.kind
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
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message") },
                maxLines = 4
            )

            Button(
                modifier = Modifier.padding(start = 8.dp),
                enabled = input.isNotBlank() && !isSending,
                onClick = ::send
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    dialogKind: DialogKind?
) {
    val showSender = !message.isOutgoing &&
        dialogKind != DialogKind.Direct &&
        dialogKind != DialogKind.Secret &&
        !message.senderName.isNullOrBlank()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            color = if (message.isOutgoing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (showSender) {
                    Text(
                        text = message.senderName.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = message.text,
                    color = if (message.isOutgoing) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Text(
                    modifier = Modifier.align(Alignment.End),
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isOutgoing) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }
        }
    }
}
