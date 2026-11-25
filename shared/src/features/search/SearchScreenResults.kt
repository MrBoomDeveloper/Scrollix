package com.mrboomdev.scrollix.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.storage.DeviceDownloadedFile
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.attach_file_icon
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun PostSearchToolbar(
    query: String,
    quota: PostSearchQuota?,
    limitsExceeded: Boolean,
    isSearching: Boolean,
    error: String?,
    onSearch: () -> Unit
) {
    val remaining = quota?.remainingFreeQueryCount
    val currentQueryFree = quota?.isCurrentQueryFree == true
    val exhausted = limitsExceeded ||
        (remaining != null && remaining <= 0 && !currentQueryFree)
    val canSearch = query.isNotBlank() &&
        !isSearching &&
        !exhausted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        quota != null -> "${quota.remainingFreeQueryCount} of " +
                            "${quota.dailyFreeQueryCount} free searches left today"

                        else -> "Checking free search quota…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (exhausted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (exhausted) {
                    Text(
                        text = quota?.nextFreeQueryIn
                            ?.takeIf { seconds -> seconds > 0 }
                            ?.let { seconds -> "Next free search in ${formatWait(seconds)}" }
                            ?: "Public post search is unavailable right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        text = if (currentQueryFree) {
                            "This query is free to repeat."
                        } else {
                            "Each search is manual and may spend a free query."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Button(
                onClick = onSearch,
                enabled = canSearch
            ) {
                Text(if (isSearching) "Searching…" else "Search posts")
            }
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DialogResults(
    entries: List<AccountDialogEntry>,
    remoteEntries: List<AccountDialogEntry>,
    query: String,
    kinds: Set<DialogKind>,
    isSearching: Boolean,
    onOpen: (AccountDialogEntry) -> Unit
) {
    val localResults = remember(entries, query, kinds) {
        entries.asSequence()
            .filter { entry -> entry.dialog.kind in kinds }
            .filter { entry ->
                query.isBlank() ||
                    entry.dialog.title.contains(query, ignoreCase = true) ||
                    entry.dialog.preview.contains(query, ignoreCase = true) ||
                    entry.account.displayName.contains(query, ignoreCase = true)
            }
            .toList()
    }

    val results = remember(localResults, remoteEntries, kinds) {
        (localResults + remoteEntries.filter { entry -> entry.dialog.kind in kinds })
            .distinctBy { entry -> "${entry.account.id}:${entry.dialog.providerDialogId}" }
    }

    var visibleCount by remember(query, kinds) { mutableStateOf(SearchPageSize) }
    val listState = rememberLazyListState()

    LaunchedEffect(query, kinds) {
        visibleCount = SearchPageSize
    }

    if (results.isEmpty()) {
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            EmptySearchState("No results found")
        }

        return
    }

    LazyScrollPagination(
        listState = listState,
        loadedCount = visibleCount.coerceAtMost(results.size),
        totalCount = results.size,
        onLoadMore = {
            visibleCount = (visibleCount + SearchPageSize).coerceAtMost(results.size)
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        items(
            items = results.take(visibleCount),
            key = { entry -> "${entry.account.id}:${entry.dialog.providerDialogId}" }
        ) { entry ->
            SearchDialogRow(entry = entry, onClick = { onOpen(entry) })
        }
    }
}

@Composable
internal fun SearchDialogRow(entry: AccountDialogEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(
            avatar = entry.dialog.avatar ?: Avatar.fromName(entry.dialog.title),
            size = 44.dp
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = entry.dialog.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = entry.dialog.preview.ifBlank {
                    entry.dialog.statusText ?: entry.dialog.kind.name
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PostResults(
    modifier: Modifier = Modifier,
    posts: List<ChatMessage>,
    entries: List<AccountDialogEntry>,
    resultDialogs: List<Dialog>,
    query: String,
    hasSearched: Boolean,
    isSearching: Boolean,
    hasMore: Boolean,
    onOpen: (ChatMessage) -> Unit,
    onLoadMore: () -> Unit
) {
    if (!hasSearched && posts.isEmpty() && !isSearching) {
        EmptySearchState(
            if (query.isBlank()) {
                "Type a query and tap Search posts"
            } else {
                "Tap Search posts to search public channels"
            },
            modifier = modifier
        )

        return
    }

    if (isSearching && posts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        return
    }

    if (posts.isEmpty()) {
        EmptySearchState("No posts found", modifier = modifier)
        return
    }

    val dialogs = remember(entries, resultDialogs) {
        (entries.map { entry -> entry.dialog } + resultDialogs)
            .associateBy { dialog -> "${dialog.accountId}:${dialog.providerDialogId}" }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(listState, posts.size, hasMore, isSearching) {
        if (!hasMore || isSearching) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= posts.lastIndex - 2) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState
    ) {
        items(
            items = posts,
            key = { message ->
                "${message.accountId}:${message.providerDialogId}:${message.messageId}"
            }
        ) { message ->
            val dialog = dialogs["${message.accountId}:${message.providerDialogId}"]

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(message) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarImage(
                    avatar = dialog?.avatar ?: Avatar.fromName(dialog?.title ?: "Post"),
                    size = 44.dp
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = dialog?.title ?: "Post",
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

        if (isSearching) {
            item(key = "posts-loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
internal fun DownloadResults(
    deviceDownloads: List<DeviceDownloadedFile>,
    directory: String,
    query: String,
    isSearching: Boolean
) {
    val results = remember(deviceDownloads, query) {
        deviceDownloads.filter { file ->
            query.isBlank() || file.name.contains(query, ignoreCase = true)
        }
    }

    var visibleCount by remember(query, results) { mutableStateOf(SearchPageSize) }
    val listState = rememberLazyListState()

    LaunchedEffect(query, results) {
        visibleCount = SearchPageSize
    }

    if (results.isEmpty()) {
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            EmptySearchState(
                if (query.isBlank()) "No files in Downloads/Scrollix" else "No downloads found"
            )
        }

        return
    }

    LazyScrollPagination(
        listState = listState,
        loadedCount = visibleCount.coerceAtMost(results.size),
        totalCount = results.size,
        onLoadMore = {
            visibleCount = (visibleCount + SearchPageSize).coerceAtMost(results.size)
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        if (directory.isNotBlank()) {
            item(key = "downloads-directory") {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    text = directory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        items(
            items = results.take(visibleCount),
            key = { file -> "${file.path}:${file.modifiedAt}" }
        ) { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = attach_file_icon,
                        contentDescription = null
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = buildString {
                            append(formatFileSize(file.size))
                            if (file.modifiedAt > 0) {
                                append(" · ")
                                append(formatTime(file.modifiedAt))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Reveals more already loaded rows when the list approaches its end. */
@Composable
internal fun LazyScrollPagination(
    listState: LazyListState,
    loadedCount: Int,
    totalCount: Int,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(listState, loadedCount, totalCount) {
        if (loadedCount >= totalCount) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= loadedCount - 3) {
                    onLoadMore()
                }
            }
    }
}

@Composable
internal fun EmptySearchState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB"
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }
}

internal fun formatWait(seconds: Int): String {
    return when {
        seconds >= 3_600 -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m"
        seconds >= 60 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }
}
