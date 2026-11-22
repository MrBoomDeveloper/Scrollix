package com.mrboomdev.scrollix.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AccountDownloadedFile
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.features.dialog.DialogScreen
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.attach_file_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val ChatKinds = setOf(DialogKind.Direct, DialogKind.Secret, DialogKind.Group)
private val ChannelKinds = setOf(DialogKind.Channel)
private val BotKinds = setOf(DialogKind.Bot)

private enum class SearchTab(val title: String) {
    Chats("Chats"),
    Channels("Channels"),
    Bots("Bots"),
    Posts("Posts"),
    Downloads("Downloads")
}

private data class SearchSelection(
    val accountId: String,
    val providerDialogId: String,
    val messageId: Long? = null
)

@Composable
fun SearchScreen(
    initialQuery: String = "",
    onBack: () -> Unit,
    onOpenDialog: (accountId: String, providerDialogId: String, messageId: Long?) -> Unit,
    onOpenProfile: (accountId: String, providerDialogId: String) -> Unit,
    onOpenUserProfile: (accountId: String, providerDialogId: String, userId: Long) -> Unit
) {
    val isTwoPane = isTwoPaneLayout()
    val entries by ApiManager.dialogs.collectAsState()
    val downloads by ApiManager.downloads.collectAsState()

    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var selectedTab by remember(initialQuery) {
        mutableStateOf(
            if (initialQuery.trimStart().startsWith("#")) SearchTab.Posts else SearchTab.Chats
        )
    }
    var selected by remember { mutableStateOf<SearchSelection?>(null) }
    var posts by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isSearchingPosts by remember { mutableStateOf(false) }
    var remoteEntries by remember {
        mutableStateOf<List<AccountDialogEntry>>(emptyList())
    }

    val trimmedQuery = query.trim()

    LaunchedEffect(trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            posts = emptyList()
            isSearchingPosts = false
            return@LaunchedEffect
        }

        isSearchingPosts = true
        delay(300.milliseconds)
        posts = ApiManager.searchGlobalMessages(trimmedQuery).getOrDefault(emptyList())
        isSearchingPosts = false
    }

    // Chats, channels and bots are searched on the server, so the results
    // include ones that haven't been loaded into the dialog list yet.
    LaunchedEffect(trimmedQuery, selectedTab) {
        if (
            trimmedQuery.isEmpty() ||
            selectedTab == SearchTab.Posts ||
            selectedTab == SearchTab.Downloads
        ) {
            remoteEntries = emptyList()
            return@LaunchedEffect
        }

        delay(300.milliseconds)
        remoteEntries = ApiManager.searchChats(trimmedQuery)
    }

    if (!isTwoPane) {
        SearchPanel(
            modifier = Modifier.fillMaxSize(),
            query = query,
            onQueryChange = { query = it },
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            onBack = onBack,
            entries = entries,
            remoteEntries = remoteEntries,
            downloads = downloads,
            posts = posts,
            isSearchingPosts = isSearchingPosts,
            onOpenEntry = { entry ->
                onOpenDialog(entry.account.id, entry.dialog.providerDialogId, null)
            },
            onOpenPost = { message ->
                onOpenDialog(
                    message.accountId,
                    message.providerDialogId,
                    message.messageId
                )
            }
        )

        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.38f).coerceIn(300.dp, 400.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            SearchPanel(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight(),
                query = query,
                onQueryChange = { query = it },
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onBack = onBack,
                entries = entries,
                remoteEntries = remoteEntries,
                downloads = downloads,
                posts = posts,
                isSearchingPosts = isSearchingPosts,
                onOpenEntry = { entry ->
                    selected = SearchSelection(
                        accountId = entry.account.id,
                        providerDialogId = entry.dialog.providerDialogId
                    )
                },
                onOpenPost = { message ->
                    selected = SearchSelection(
                        accountId = message.accountId,
                        providerDialogId = message.providerDialogId,
                        messageId = message.messageId
                    )
                }
            )

            VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val target = selected

                if (target == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select a search result",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    key(target.accountId, target.providerDialogId, target.messageId) {
                        DialogScreen(
                            accountId = target.accountId,
                            providerDialogId = target.providerDialogId,
                            initialMessageId = target.messageId,
                            onOpenProfile = {
                                onOpenProfile(target.accountId, target.providerDialogId)
                            },
                            onOpenUserProfile = { userId ->
                                onOpenUserProfile(
                                    target.accountId,
                                    target.providerDialogId,
                                    userId
                                )
                            },
                            onOpenHashtag = { hashtag ->
                                query = hashtag
                                selectedTab = SearchTab.Posts
                                selected = null
                            },
                            onBack = { selected = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTab: SearchTab,
    onSelectTab: (SearchTab) -> Unit,
    onBack: () -> Unit,
    entries: List<AccountDialogEntry>,
    remoteEntries: List<AccountDialogEntry>,
    downloads: List<AccountDownloadedFile>,
    posts: List<ChatMessage>,
    isSearchingPosts: Boolean,
    onOpenEntry: (AccountDialogEntry) -> Unit,
    onOpenPost: (ChatMessage) -> Unit
) {
    val trimmedQuery = query.trim()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = arrow_back_icon,
                    contentDescription = "Back"
                )
            }

            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = "Search",
                style = MaterialTheme.typography.titleLarge
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = search_icon,
                    contentDescription = null
                )
            },
            placeholder = {
                Text("Search")
            }
        )

        ScrollableTabRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {}
        ) {
            SearchTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                SearchTab.Chats -> DialogResults(
                    entries = entries,
                    remoteEntries = remoteEntries,
                    query = trimmedQuery,
                    kinds = ChatKinds,
                    onOpen = onOpenEntry
                )

                SearchTab.Channels -> DialogResults(
                    entries = entries,
                    remoteEntries = remoteEntries,
                    query = trimmedQuery,
                    kinds = ChannelKinds,
                    onOpen = onOpenEntry
                )

                SearchTab.Bots -> DialogResults(
                    entries = entries,
                    remoteEntries = remoteEntries,
                    query = trimmedQuery,
                    kinds = BotKinds,
                    onOpen = onOpenEntry
                )

                SearchTab.Posts -> PostResults(
                    posts = posts,
                    entries = entries,
                    query = trimmedQuery,
                    isSearching = isSearchingPosts,
                    onOpen = onOpenPost
                )

                SearchTab.Downloads -> DownloadResults(
                    downloads = downloads,
                    query = trimmedQuery
                )
            }
        }
    }
}

@Composable
private fun DialogResults(
    entries: List<AccountDialogEntry>,
    remoteEntries: List<AccountDialogEntry>,
    query: String,
    kinds: Set<DialogKind>,
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
            .take(100)
            .toList()
    }

    // Server results already match the query, so they don't need the local
    // text filter that the loaded dialogs go through.
    val results = remember(localResults, remoteEntries, kinds) {
        (remoteEntries.filter { entry -> entry.dialog.kind in kinds } + localResults)
            .distinctBy { entry -> "${entry.account.id}:${entry.dialog.providerDialogId}" }
            .take(100)
    }

    if (results.isEmpty()) {
        EmptySearchState("No results found")
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = results,
            key = { entry -> "${entry.account.id}:${entry.dialog.providerDialogId}" }
        ) { entry ->
            SearchDialogRow(entry = entry, onClick = { onOpen(entry) })
        }
    }
}

@Composable
private fun SearchDialogRow(entry: AccountDialogEntry, onClick: () -> Unit) {
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
private fun PostResults(
    posts: List<ChatMessage>,
    entries: List<AccountDialogEntry>,
    query: String,
    isSearching: Boolean,
    onOpen: (ChatMessage) -> Unit
) {
    if (isSearching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        return
    }

    if (posts.isEmpty()) {
        EmptySearchState(
            if (query.isBlank()) "Search posts in your channels" else "No posts found"
        )

        return
    }

    val dialogs = remember(entries) {
        entries.associate { entry ->
            "${entry.account.id}:${entry.dialog.providerDialogId}" to entry.dialog
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
    }
}

@Composable
private fun DownloadResults(
    downloads: List<AccountDownloadedFile>,
    query: String
) {
    val results = remember(downloads, query) {
        downloads.filter { entry ->
            query.isBlank() || entry.file.fileName.contains(query, ignoreCase = true)
        }
    }

    if (results.isEmpty()) {
        EmptySearchState(
            if (query.isBlank()) "No downloads yet" else "No downloads found"
        )

        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = results,
            key = { entry -> "${entry.file.accountId}:${entry.file.fileId}" }
        ) { entry ->
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
                        text = entry.file.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${formatFileSize(entry.file.size)} · " +
                            entry.account.displayName,
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

@Composable
private fun EmptySearchState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

private fun ChatMessage.postSnippet(): String {
    if (text.isNotBlank()) return text

    val pollValue = poll
    if (pollValue != null) return pollValue.question

    val media = media ?: return "Message"

    return when (media.kind) {
        MessageMediaKind.Photo -> "Photo"
        MessageMediaKind.Video -> "Video"
        MessageMediaKind.Animation -> "GIF"
        MessageMediaKind.Sticker -> "Sticker"
        MessageMediaKind.VideoNote -> "Video message"
        MessageMediaKind.VoiceNote -> "Voice message"
        MessageMediaKind.Audio -> media.fileName ?: "Audio"
        MessageMediaKind.File -> media.fileName ?: "File"
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB"
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }
}
