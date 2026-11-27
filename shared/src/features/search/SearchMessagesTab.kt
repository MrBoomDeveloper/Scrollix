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
import com.mrboomdev.scrollix.ui.components.EmptyState
import com.mrboomdev.scrollix.ui.components.ErrorState
import com.mrboomdev.scrollix.ui.components.SkeletonList
import com.mrboomdev.scrollix.ui.icons.search_icon

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

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        when {
            state.isSearching && state.results.isEmpty() -> SkeletonList(
                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                rows = 8
            )

            state.error != null && state.results.isEmpty() -> ErrorState(
                modifier = Modifier.fillMaxSize(),
                title = "Search failed",
                message = state.error,
                onRetry = onSearch
            )

            !state.hasSearched && state.results.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = "Search messages",
                message = if (query.isBlank()) {
                    "Type a query and tap Search messages."
                } else {
                    "Tap Search messages to search every account."
                },
                icon = search_icon
            )

            state.results.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = "No messages found",
                message = "Try a different word or widen the filters.",
                icon = search_icon
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

    // With several accounts signed in, results are grouped under the account
    // they came from instead of repeating its name on every row. A single
    // account needs no headers.
    val groups = remember(messages, showAccount) {
        if (showAccount) {
            messages.groupBy { message -> message.accountId }.toList()
        } else {
            listOf("" to messages)
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        groups.forEach { (accountId, group) ->
            if (showAccount && accountId.isNotBlank()) {
                item(key = "account:$accountId") {
                    Text(
                        modifier = Modifier.padding(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = 2.dp
                        ),
                        text = accountNames[accountId] ?: accountId,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(
                items = group,
                key = { message ->
                    "${message.accountId}:${message.providerDialogId}:${message.messageId}"
                }
            ) { message ->
                val entry = dialogs["${message.accountId}:${message.providerDialogId}"]

                MessageResultRow(
                    message = message,
                    entry = entry,
                    onOpen = onOpen
                )
            }
        }
    }
}

@Composable
private fun MessageResultRow(
    message: ChatMessage,
    entry: AccountDialogEntry?,
    onOpen: (ChatMessage) -> Unit
) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

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
