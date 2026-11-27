package com.mrboomdev.scrollix.features.notifications

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.EmptyState
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.notifications_icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The notification center: chats whose provider-reported mention or reaction
 * counters are non-zero. It reads the same aggregated dialog list as the chat
 * list, so an event disappears from here as soon as the chat is read on any
 * client. Replies have no provider counter, so they are not listed separately.
 */
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    onOpenDialog: (AccountDialogEntry) -> Unit
) {
    val entries by ApiManager.dialogs.collectAsState()
    val loading by ApiManager.dialogsLoading.collectAsState()

    val events = entries.filter { entry ->
        entry.dialog.unreadMentionCount > 0 || entry.dialog.unreadReactionCount > 0
    }
    val accountIds = entries.map { entry -> entry.account.id }.toSet()

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
                    contentDescription = "Back"
                )
            }

            Text(
                modifier = Modifier.weight(1f),
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge
            )

            if (events.isNotEmpty()) {
                Text(
                    text = events.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        when {
            events.isEmpty() && loading.isNotEmpty() -> LoadingContent(
                modifier = Modifier.fillMaxSize(),
                text = "Checking mentions and reactions…"
            )

            events.isEmpty() && accountIds.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = "No accounts yet",
                message = "Add an account to see mentions and reactions here.",
                icon = notifications_icon
            )

            events.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = "You are all caught up",
                message = "Chats with unread mentions or reactions appear here.",
                icon = notifications_icon
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(
                    items = events,
                    key = { entry -> "${entry.account.id}:${entry.dialog.providerDialogId}" }
                ) { entry ->
                    NotificationRow(entry = entry, onClick = { onOpenDialog(entry) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    entry: AccountDialogEntry,
    onClick: () -> Unit
) {
    val dialog = entry.dialog
    val parts = buildList {
        if (dialog.unreadMentionCount > 0) {
            add(
                if (dialog.unreadMentionCount == 1) {
                    "1 mention"
                } else {
                    "${dialog.unreadMentionCount} mentions"
                }
            )
        }

        if (dialog.unreadReactionCount > 0) {
            add(
                if (dialog.unreadReactionCount == 1) {
                    "1 reaction"
                } else {
                    "${dialog.unreadReactionCount} reactions"
                }
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(
            avatar = dialog.avatar ?: Avatar.fromName(dialog.title),
            size = 40.dp
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = dialog.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = dialog.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = entry.account.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
