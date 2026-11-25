package com.mrboomdev.scrollix.features.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.model.PublicChatSearchKind
import com.mrboomdev.scrollix.data.storage.DeviceDownloadedFile
import com.mrboomdev.scrollix.data.storage.deviceDownloadsDirectory
import com.mrboomdev.scrollix.data.storage.listDeviceDownloads
import com.mrboomdev.scrollix.features.dialog.DialogScreen
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val ChatKinds = setOf(DialogKind.Direct, DialogKind.Secret, DialogKind.Group)
private val ChannelKinds = setOf(DialogKind.Channel)
private val BotKinds = setOf(DialogKind.Bot)

internal const val SearchPageSize = 30

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

internal fun ChatMessage.postSnippet(): String {
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
