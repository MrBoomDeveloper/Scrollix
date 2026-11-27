package com.mrboomdev.scrollix.features.storage

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.ChatCacheUsage
import com.mrboomdev.scrollix.data.storage.DataUsageStore
import com.mrboomdev.scrollix.data.storage.LocalDataUsage
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.features.settings.formatDataSize
import com.mrboomdev.scrollix.ui.components.ConfirmDeleteDialog
import com.mrboomdev.scrollix.ui.components.EmptyState
import com.mrboomdev.scrollix.ui.components.SkeletonList
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.storage_icon
import com.mrboomdev.scrollix.ui.notice.NoticeCenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Storage on this device, grouped the way it can actually be deleted: by
 * category and by chat. Per-chat clearing removes the cached window and the
 * media it references but never the chat or its queued sends.
 */
@Composable
fun StorageCenterScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val usage by DataUsageStore.state.collectAsState()
    val dialogs by ApiManager.dialogs.collectAsState()
    var refreshKey by remember { mutableIntStateOf(0) }
    var sizes by remember { mutableStateOf<Map<CacheStore.Category, Long>>(emptyMap()) }
    var chats by remember { mutableStateOf<List<ChatCacheUsage>?>(null) }
    var pendingCategory by remember { mutableStateOf<CacheStore.Category?>(null) }
    var pendingChat by remember { mutableStateOf<ChatCacheUsage?>(null) }
    var pendingUsageReset by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            sizes = CacheStore.Category.entries.associateWith { category ->
                CacheStore.size(category)
            }

            chats = CacheStore.chatUsage()
        }
    }

    val titles = remember(dialogs) {
        dialogs.associate { entry ->
            "${entry.account.id}:${entry.dialog.providerDialogId}" to entry.dialog.title
        }
    }
    val accountNames = remember(dialogs) {
        dialogs.associate { entry -> entry.account.id to entry.account.displayName }
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
                    contentDescription = "Back"
                )
            }

            Text(
                modifier = Modifier.weight(1f),
                text = "Storage",
                style = MaterialTheme.typography.titleLarge
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "summary") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "On this device",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Total cached: ${formatDataSize(sizes.values.sum())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Text(
                        text = "Clearing cached data never removes chats, server " +
                            "history or queued messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            CacheStore.Category.entries.forEach { category ->
                item(key = "category:${category.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Text(
                                text = formatDataSize(sizes[category] ?: 0L),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        TextButton(onClick = { pendingCategory = category }) {
                            Text("Clear")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                }
            }

            item(key = "usage-title") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Traffic by account",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Only traffic that passes through Scrollix is counted; " +
                            "this is an approximation, not a provider report.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (usage.isEmpty()) {
                item(key = "usage-empty") {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = "No traffic recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                items(usage.toList(), key = { (accountId, _) -> "usage:$accountId" }) { pair ->
                    val (accountId, data) = pair
                    UsageRow(
                        name = accountNames[accountId] ?: accountId,
                        usage = data,
                        onReset = { pendingUsageReset = accountId }
                    )
                }
            }

            item(key = "chats-title") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Cached chats",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Each opened chat keeps its last messages and the media " +
                            "they reference, so it can be read again without a network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            val loadedChats = chats

            when {
                loadedChats == null -> item(key = "chats-loading") {
                    SkeletonList(
                        modifier = Modifier.padding(16.dp),
                        rows = 4
                    )
                }

                loadedChats.isEmpty() -> item(key = "chats-empty") {
                    EmptyState(
                        title = "Nothing cached",
                        message = "Open a chat and its messages will be cached here.",
                        icon = storage_icon
                    )
                }

                else -> items(
                    items = loadedChats,
                    key = { chat -> "chat:${chat.accountId}:${chat.providerDialogId}" }
                ) { chat ->
                    ChatCacheRow(
                        chat = chat,
                        title = titles["${chat.accountId}:${chat.providerDialogId}"]
                            ?: "Chat",
                        accountName = accountNames[chat.accountId],
                        onClear = { pendingChat = chat }
                    )
                }
            }
        }
    }

    pendingCategory?.let { category ->
        ConfirmDeleteDialog(
            title = "Clear ${category.title.lowercase()}?",
            message = "Cached ${category.title.lowercase()} is deleted from this " +
                "device after the undo window. Messages and dialogs stay.",
            confirmLabel = "Clear",
            onConfirm = {
                NoticeCenter.undo(
                    message = "Cached ${category.title.lowercase()} deleted",
                    undo = { refreshKey++ },
                    commit = {
                        scope.launch {
                            withContext(Dispatchers.IO) { CacheStore.clear(category) }
                            refreshKey++
                        }
                    }
                )
            },
            onDismiss = { pendingCategory = null }
        )
    }

    pendingChat?.let { chat ->
        ConfirmDeleteDialog(
            title = "Clear this chat's cache?",
            message = "Cached messages and media of this chat are deleted from " +
                "this device. The chat and its server history stay.",
            confirmLabel = "Clear",
            onConfirm = {
                NoticeCenter.undo(
                    message = "Chat cache deleted",
                    undo = { refreshKey++ },
                    commit = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                CacheStore.clearChatCache(
                                    accountId = chat.accountId,
                                    providerDialogId = chat.providerDialogId
                                )
                            }

                            refreshKey++
                        }
                    }
                )
            },
            onDismiss = { pendingChat = null }
        )
    }

    pendingUsageReset?.let { accountId ->
        ConfirmDeleteDialog(
            title = "Reset traffic counters?",
            message = "The sent and received byte counters of this account start " +
                "from zero again. Cached data is untouched.",
            confirmLabel = "Reset",
            onConfirm = { DataUsageStore.reset(accountId) },
            onDismiss = { pendingUsageReset = null }
        )
    }
}

@Composable
private fun UsageRow(
    name: String,
    usage: LocalDataUsage,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)

            Text(
                text = "${formatDataSize(usage.totalBytes)} · " +
                    "sent ${formatDataSize(usage.sentBytes)} · " +
                    "received ${formatDataSize(usage.receivedBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            if (usage.sinceMillis > 0) {
                Text(
                    text = "Since ${formatTime(usage.sinceMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        TextButton(onClick = onReset) {
            Text("Reset")
        }
    }
}

@Composable
private fun ChatCacheRow(
    chat: ChatCacheUsage,
    title: String,
    accountName: String?,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = buildString {
                    append(formatDataSize(chat.totalBytes))
                    append(" · messages ")
                    append(formatDataSize(chat.messageBytes))
                    append(" · media ")
                    append(formatDataSize(chat.mediaBytes))

                    if (accountName != null) {
                        append(" · ")
                        append(accountName)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        TextButton(onClick = onClear) {
            Text("Clear")
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
}
