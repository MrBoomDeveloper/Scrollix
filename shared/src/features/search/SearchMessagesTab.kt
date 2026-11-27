package com.mrboomdev.scrollix.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.ui.components.AvatarImage

/**
 * Everything the global message tab renders, so the panel stays a plain shell.
 * The narrowing itself lives in the filters dialog opened from the search bar.
 */
internal data class MessageSearchState(
    val results: List<ChatMessage>,
    /** Account id and display name, for the account labels of the results. */
    val accountOptions: List<Pair<String, String>>,
    val hasSearched: Boolean,
    val isSearching: Boolean,
    val error: String?
)

/**
 * Global message search. It fans out to every signed in account and is started
 * manually, because a keystroke must not query every provider.
 */
@Composable
internal fun MessageSearchTab(
    modifier: Modifier = Modifier,
    state: MessageSearchState,
    query: String,
    entries: List<AccountDialogEntry>,
    onSearch: () -> Unit,
    onOpen: (ChatMessage) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                enabled = query.isNotBlank() && !state.isSearching,
                onClick = onSearch
            ) {
                Text("Search messages")
            }
        }

        state.error?.let { message ->
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        when {
            state.isSearching && state.results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            !state.hasSearched && state.results.isEmpty() -> EmptySearchState(
                text = if (query.isBlank()) {
                    "Type a query and tap Search messages"
                } else {
                    "Tap Search messages to search every account"
                },
                modifier = Modifier.fillMaxSize()
            )

            state.results.isEmpty() -> EmptySearchState(
                text = "No messages found",
                modifier = Modifier.fillMaxSize()
            )

            else -> MessageResultRows(
                modifier = Modifier.fillMaxSize(),
                messages = state.results,
                entries = entries,
                showAccount = state.accountOptions.size > 1,
                accountNames = state.accountOptions.toMap(),
                onOpen = onOpen
            )
        }

        if (state.isSearching && state.results.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun MessageResultRows(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    entries: List<AccountDialogEntry>,
    showAccount: Boolean,
    accountNames: Map<String, String>,
    onOpen: (ChatMessage) -> Unit
) {
    val dialogs = remember(entries) {
        entries.associateBy { entry -> "${entry.account.id}:${entry.dialog.providerDialogId}" }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(
            items = messages,
            key = { message ->
                "${message.accountId}:${message.providerDialogId}:${message.messageId}"
            }
        ) { message ->
            val entry = dialogs["${message.accountId}:${message.providerDialogId}"]
            val title = entry?.dialog?.title ?: "Message"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(message) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarImage(
                    avatar = entry?.dialog?.avatar ?: Avatar.fromName(title),
                    size = 44.dp
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (showAccount) {
                            Text(
                                modifier = Modifier.padding(start = 6.dp),
                                text = accountNames[message.accountId].orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = message.postSnippet(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
