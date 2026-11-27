package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.ChatTopic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A forward cannot name a forum chat without a topic: the provider needs the
 * thread id, otherwise the messages land in the chat's general thread. This
 * dialog runs between picking the chat and the actual forward.
 */
@Composable
internal fun ForwardTopicDialog(
    accountId: String,
    providerDialogId: String,
    title: String,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit
) {
    var topics by remember(accountId, providerDialogId) {
        mutableStateOf<List<ChatTopic>>(emptyList())
    }

    var isLoading by remember(accountId, providerDialogId) { mutableStateOf(true) }

    var error by remember(accountId, providerDialogId) { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId, providerDialogId) {
        isLoading = true
        error = null

        val session = ApiManager.session(accountId)

        if (session == null) {
            error = "This account is no longer available"
        } else {
            session.loadTopics(providerDialogId, "")
                .onSuccess { loaded -> topics = loaded }
                .onFailure { failure ->
                    error = failure.message ?: "Failed to load topics"
                }
        }

        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    text = "Forward to a topic in $title",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                )

                when {
                    isLoading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.mrboomdev.scrollix.ui.components.LoadingContent(
                            text = "Loading topics"
                        )
                    }

                    topics.isEmpty() -> Text(
                        modifier = Modifier.padding(16.dp),
                        text = error ?: "This chat has no topics to forward into.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(topics, key = { topic -> topic.id }) { topic ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(topic.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
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

                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                        }
                    }
                }
            }
        }
    }
}
