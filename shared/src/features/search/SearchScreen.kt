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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.model.PublicChatSearchKind
import com.mrboomdev.scrollix.data.storage.DeviceDownloadedFile
import com.mrboomdev.scrollix.data.storage.deviceDownloadsDirectory
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.data.storage.listDeviceDownloads
import com.mrboomdev.scrollix.features.dialog.DialogScreen
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.attach_file_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val ChatKinds = setOf(DialogKind.Direct, DialogKind.Secret, DialogKind.Group)
private val ChannelKinds = setOf(DialogKind.Channel)
private val BotKinds = setOf(DialogKind.Bot)

private const val SearchPageSize = 30

private enum class SearchTab(val title: String, val publicKind: PublicChatSearchKind) {
    Chats("Chats", PublicChatSearchKind.Any),
    Channels("Channels", PublicChatSearchKind.Channels),
    Bots("Bots", PublicChatSearchKind.Bots),
    Posts("Posts", PublicChatSearchKind.Any),
    Downloads("Downloads", PublicChatSearchKind.Any)
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
    onOpenUserProfile: (accountId: String, providerDialogId: String, userId: Long) -> Unit,
    /** Selection shared with the Home tab so the two-pane chat pane survives tab switches. */
    selectedDialogAccountId: String? = null,
    selectedDialogProviderDialogId: String? = null,
    selectedDialogMessageId: Long? = null,
    onSelectDialog: ((accountId: String, providerDialogId: String, messageId: Long?) -> Unit)? = null
) {
    val isTwoPane = isTwoPaneLayout()
    val entries by ApiManager.dialogs.collectAsState()
    val scope = rememberCoroutineScope()

    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var selectedTab by remember(initialQuery) {
        mutableStateOf(
            if (initialQuery.trimStart().startsWith("#")) SearchTab.Posts else SearchTab.Chats
        )
    }
    // Mirror the app-level selection when entering Search, so a chat opened on
    // the Home tab stays in the right pane after switching tabs.
    var selected by remember(
        selectedDialogAccountId,
        selectedDialogProviderDialogId,
        selectedDialogMessageId
    ) {
        mutableStateOf(
            if (selectedDialogAccountId != null && selectedDialogProviderDialogId != null) {
                SearchSelection(
                    accountId = selectedDialogAccountId,
                    providerDialogId = selectedDialogProviderDialogId,
                    messageId = selectedDialogMessageId
                )
            } else {
                null
            }
        )
    }

    var remoteEntries by remember {
        mutableStateOf<List<AccountDialogEntry>>(emptyList())
    }
    var isSearchingChats by remember { mutableStateOf(false) }

    // Public post search is deliberately manual: every page may spend one of
    // the account's free daily searches.
    var postResults by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var postResultDialogs by remember { mutableStateOf<List<Dialog>>(emptyList()) }
    var postSearchQuery by remember { mutableStateOf<String?>(null) }
    var postNextOffset by remember { mutableStateOf("") }
    var postQuota by remember { mutableStateOf<PostSearchQuota?>(null) }
    var postLimitsExceeded by remember { mutableStateOf(false) }
    var postSearched by remember { mutableStateOf(false) }
    var isSearchingPosts by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }

    var deviceDownloads by remember { mutableStateOf<List<DeviceDownloadedFile>>(emptyList()) }
    var downloadsDirectory by remember { mutableStateOf("") }
    var isSearchingDownloads by remember { mutableStateOf(false) }

    val trimmedQuery = query.trim()

    // Chats, channels and bots are searched both on the account and across
    // public chats the account has not joined yet.
    LaunchedEffect(trimmedQuery, selectedTab) {
        if (
            trimmedQuery.isEmpty() ||
            selectedTab == SearchTab.Posts ||
            selectedTab == SearchTab.Downloads
        ) {
            remoteEntries = emptyList()
            isSearchingChats = false
            return@LaunchedEffect
        }

        isSearchingChats = true
        delay(300.milliseconds)
        remoteEntries = ApiManager.searchChats(
            query = trimmedQuery,
            limit = 50,
            publicKind = selectedTab.publicKind
        )
        isSearchingChats = false
    }

    // The quota is free to read, so it is refreshed for the current query.
    // Individual searches replace it with the value from their page.
    LaunchedEffect(selectedTab, trimmedQuery) {
        if (selectedTab != SearchTab.Posts) return@LaunchedEffect

        delay(400.milliseconds)
        ApiManager.loadPublicPostSearchQuota(trimmedQuery)
            .onSuccess { quota -> postQuota = quota }
    }

    // Editing the query invalidates results, but searching still needs an
    // explicit tap so a keystroke never spends a free search.
    LaunchedEffect(trimmedQuery, selectedTab) {
        if (postSearchQuery == null) return@LaunchedEffect
        if (trimmedQuery == postSearchQuery) return@LaunchedEffect

        postResults = emptyList()
        postResultDialogs = emptyList()
        postSearchQuery = null
        postNextOffset = ""
        postSearched = false
        postLimitsExceeded = false
        postError = null
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != SearchTab.Downloads) return@LaunchedEffect

        isSearchingDownloads = true
        downloadsDirectory = deviceDownloadsDirectory()
        deviceDownloads = listDeviceDownloads()
        isSearchingDownloads = false
    }

    fun launchPostSearch(loadMore: Boolean) {
        if (isSearchingPosts) return

        val searchQuery = if (loadMore) postSearchQuery ?: trimmedQuery else trimmedQuery
        if (searchQuery.isBlank()) return
        if (loadMore && (postNextOffset.isBlank() || postLimitsExceeded)) return

        val offset = if (loadMore) postNextOffset else ""

        if (!loadMore) {
            postResults = emptyList()
            postResultDialogs = emptyList()
            postNextOffset = ""
            postLimitsExceeded = false
            postError = null
        }

        isSearchingPosts = true
        postSearched = true

        scope.launch {
            ApiManager.searchPublicPosts(
                query = searchQuery,
                offset = offset,
                limit = SearchPageSize
            ).onSuccess { page ->
                postSearchQuery = searchQuery
                postResults = (if (loadMore) postResults + page.messages else page.messages)
                    .distinctBy { message ->
                        "${message.accountId}:${message.providerDialogId}:${message.messageId}"
                    }

                postResultDialogs = (if (loadMore) {
                    postResultDialogs + page.dialogs
                } else {
                    page.dialogs
                }).distinctBy { dialog ->
                    "${dialog.accountId}:${dialog.providerDialogId}"
                }

                postNextOffset = page.nextOffset
                postLimitsExceeded = page.areLimitsExceeded
                page.quota?.let { quota -> postQuota = quota }
            }.onFailure { error ->
                postError = error.message ?: "Could not search posts"
            }

            isSearchingPosts = false
        }
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
            isSearchingChats = isSearchingChats,
            deviceDownloads = deviceDownloads,
            downloadsDirectory = downloadsDirectory,
            isSearchingDownloads = isSearchingDownloads,
            posts = postResults,
            postDialogs = postResultDialogs,
            postSearched = postSearched,
            postSearchQuery = postSearchQuery,
            postNextOffset = postNextOffset,
            postQuota = postQuota,
            postLimitsExceeded = postLimitsExceeded,
            isSearchingPosts = isSearchingPosts,
            postError = postError,
            onSearchPosts = { launchPostSearch(false) },
            onLoadMorePosts = { launchPostSearch(true) },
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
                isSearchingChats = isSearchingChats,
                deviceDownloads = deviceDownloads,
                downloadsDirectory = downloadsDirectory,
                isSearchingDownloads = isSearchingDownloads,
                posts = postResults,
                postDialogs = postResultDialogs,
                postSearched = postSearched,
                postSearchQuery = postSearchQuery,
                postNextOffset = postNextOffset,
                postQuota = postQuota,
                postLimitsExceeded = postLimitsExceeded,
                isSearchingPosts = isSearchingPosts,
                postError = postError,
                onSearchPosts = { launchPostSearch(false) },
                onLoadMorePosts = { launchPostSearch(true) },
                onOpenEntry = { entry ->
                    selected = SearchSelection(
                        accountId = entry.account.id,
                        providerDialogId = entry.dialog.providerDialogId
                    )

                    onSelectDialog?.invoke(
                        entry.account.id,
                        entry.dialog.providerDialogId,
                        null
                    )
                },
                onOpenPost = { message ->
                    selected = SearchSelection(
                        accountId = message.accountId,
                        providerDialogId = message.providerDialogId,
                        messageId = message.messageId
                    )

                    onSelectDialog?.invoke(
                        message.accountId,
                        message.providerDialogId,
                        message.messageId
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
    isSearchingChats: Boolean,
    deviceDownloads: List<DeviceDownloadedFile>,
    downloadsDirectory: String,
    isSearchingDownloads: Boolean,
    posts: List<ChatMessage>,
    postDialogs: List<Dialog>,
    postSearched: Boolean,
    postSearchQuery: String?,
    postNextOffset: String,
    postQuota: PostSearchQuota?,
    postLimitsExceeded: Boolean,
    isSearchingPosts: Boolean,
    postError: String?,
    onSearchPosts: () -> Unit,
    onLoadMorePosts: () -> Unit,
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
                Text(if (selectedTab == SearchTab.Posts) "Search public posts" else "Search")
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

        when (selectedTab) {
            SearchTab.Chats -> DialogResults(
                entries = entries,
                remoteEntries = remoteEntries,
                query = trimmedQuery,
                kinds = ChatKinds,
                isSearching = isSearchingChats,
                onOpen = onOpenEntry
            )

            SearchTab.Channels -> DialogResults(
                entries = entries,
                remoteEntries = remoteEntries,
                query = trimmedQuery,
                kinds = ChannelKinds,
                isSearching = isSearchingChats,
                onOpen = onOpenEntry
            )

            SearchTab.Bots -> DialogResults(
                entries = entries,
                remoteEntries = remoteEntries,
                query = trimmedQuery,
                kinds = BotKinds,
                isSearching = isSearchingChats,
                onOpen = onOpenEntry
            )

            SearchTab.Posts -> Column(modifier = Modifier.fillMaxSize()) {
                PostSearchToolbar(
                    query = trimmedQuery,
                    quota = postQuota,
                    limitsExceeded = postLimitsExceeded,
                    isSearching = isSearchingPosts,
                    error = postError,
                    onSearch = onSearchPosts
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

                PostResults(
                    modifier = Modifier.weight(1f),
                    posts = posts,
                    entries = entries,
                    resultDialogs = postDialogs,
                    query = postSearchQuery ?: trimmedQuery,
                    hasSearched = postSearched,
                    isSearching = isSearchingPosts,
                    hasMore = postNextOffset.isNotBlank() && !postLimitsExceeded,
                    onOpen = onOpenPost,
                    onLoadMore = onLoadMorePosts
                )
            }

            SearchTab.Downloads -> DownloadResults(
                deviceDownloads = deviceDownloads,
                directory = downloadsDirectory,
                query = trimmedQuery,
                isSearching = isSearchingDownloads
            )
        }
    }
}

@Composable
private fun PostSearchToolbar(
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
private fun DialogResults(
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
private fun DownloadResults(
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
private fun LazyScrollPagination(
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
private fun EmptySearchState(text: String, modifier: Modifier = Modifier) {
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

private fun formatWait(seconds: Int): String {
    return when {
        seconds >= 3_600 -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m"
        seconds >= 60 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }
}
