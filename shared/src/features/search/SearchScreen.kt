package com.mrboomdev.scrollix.features.search

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.model.PublicChatSearchKind
import com.mrboomdev.scrollix.data.storage.DeviceDownloadedFile
import com.mrboomdev.scrollix.features.dialog.DialogScreen
import com.mrboomdev.scrollix.features.dialog.search.DialogSearchFilter
import com.mrboomdev.scrollix.features.home.key
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.filter_list_icon
import com.mrboomdev.scrollix.ui.icons.search_icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSearchBarState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val ChatKinds = setOf(DialogKind.Direct, DialogKind.Secret, DialogKind.Group)
private val ChannelKinds = setOf(DialogKind.Channel)
private val BotKinds = setOf(DialogKind.Bot)

internal const val SearchPageSize = 30

internal enum class SearchTab(val title: String, val publicKind: PublicChatSearchKind) {
    Chats("Chats", PublicChatSearchKind.Any),
    Messages("Messages", PublicChatSearchKind.Any),
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
internal fun SearchScreen(
    initialQuery: String = "",
    onBack: () -> Unit,
    onOpenDialog: (accountId: String, providerDialogId: String, messageId: Long?) -> Unit,
    onOpenProfile: (accountId: String, providerDialogId: String) -> Unit,
    onOpenUserProfile: (accountId: String, providerDialogId: String, userId: Long) -> Unit,
    /** Selection shared with the Home tab so the two-pane chat pane survives tab switches. */
    selectedDialogAccountId: String? = null,
    selectedDialogProviderDialogId: String? = null,
    selectedDialogMessageId: Long? = null,
    onSelectDialog: ((accountId: String, providerDialogId: String, messageId: Long?) -> Unit)? = null,
    viewModel: SearchScreenViewModel = viewModel { SearchScreenViewModel() }
) {
    val isTwoPane = isTwoPaneLayout()
    val entries by ApiManager.dialogs.collectAsState()
    val accountState by ApiManager.accountState.collectAsState()

    val accountOptions = remember(accountState.accounts) {
        accountState.accounts.map { account -> account.id to account.displayName }
    }

    val messageSearchState = MessageSearchState(
        results = viewModel.visibleMessageResults,
        accountOptions = accountOptions,
        hasSearched = viewModel.messageSearched,
        isSearching = viewModel.isSearchingMessages,
        error = viewModel.messageError
    )

    // The filters dialog is opened from the search bar and applies to the
    // Messages tab only, so its button appears while that tab is selected.
    var showFilters by remember { mutableStateOf(false) }

    val filtersActive = viewModel.messageFilter != DialogSearchFilter.All ||
        viewModel.messageDateRange.isActive ||
        viewModel.messageAccountId != null

    LaunchedEffect(Unit) {
        viewModel.initialize(initialQuery)
    }

    val query = viewModel.query
    val selectedTab = viewModel.selectedTab
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

    val remoteEntries = viewModel.remoteEntries
    val isSearchingChats = viewModel.isSearchingChats
    val postResults = viewModel.postResults
    val postResultDialogs = viewModel.postResultDialogs
    val postSearchQuery = viewModel.postSearchQuery
    val postNextOffset = viewModel.postNextOffset
    val postQuota = viewModel.postQuota
    val postLimitsExceeded = viewModel.postLimitsExceeded
    val postSearched = viewModel.postSearched
    val isSearchingPosts = viewModel.isSearchingPosts
    val postError = viewModel.postError
    val deviceDownloads = viewModel.deviceDownloads
    val downloadsDirectory = viewModel.downloadsDirectory
    val isSearchingDownloads = viewModel.isSearchingDownloads

    val trimmedQuery = viewModel.trimmedQuery

    if (!isTwoPane) {
        SearchPanel(
            modifier = Modifier.fillMaxSize(),
            query = query,
            onQueryChange = { viewModel.updateQuery(it) },
            selectedTab = selectedTab,
            onSelectTab = { viewModel.selectTab(it) },
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
            messages = messageSearchState,
            showMessageFilterAction = selectedTab == SearchTab.Messages,
            isMessageFiltersActive = filtersActive,
            onOpenMessageFilters = { showFilters = true },
            onSearchMessages = { viewModel.searchMessages() },
            onSearchPosts = { viewModel.searchPosts(false) },
            onLoadMorePosts = { viewModel.searchPosts(true) },
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

        MessageFilters(
            viewModel = viewModel,
            accountOptions = accountOptions,
            show = showFilters,
            onDismiss = { showFilters = false }
        )

        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.38f).coerceIn(300.dp, 400.dp)

        MessageFilters(
            viewModel = viewModel,
            accountOptions = accountOptions,
            show = showFilters,
            onDismiss = { showFilters = false }
        )

        Row(modifier = Modifier.fillMaxSize()) {
            SearchPanel(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight(),
                query = query,
                onQueryChange = { viewModel.updateQuery(it) },
                selectedTab = selectedTab,
                onSelectTab = { viewModel.selectTab(it) },
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
                messages = messageSearchState,
                showMessageFilterAction = selectedTab == SearchTab.Messages,
                isMessageFiltersActive = filtersActive,
                onOpenMessageFilters = { showFilters = true },
                onSearchMessages = { viewModel.searchMessages() },
                onSearchPosts = { viewModel.searchPosts(false) },
                onLoadMorePosts = { viewModel.searchPosts(true) },
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
                                viewModel.updateQuery(hashtag)
                                viewModel.selectTab(SearchTab.Posts)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    messages: MessageSearchState,
    showMessageFilterAction: Boolean,
    isMessageFiltersActive: Boolean,
    onOpenMessageFilters: () -> Unit,
    onSearchMessages: () -> Unit,
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
        val searchBarState = rememberSearchBarState()
        val searchBarInputState = rememberTextFieldState()
        
        SearchBar(
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp, end = 8.dp)
                .fillMaxWidth(),
            state = searchBarState,
            
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                inputFieldColors = TextFieldDefaults.colors(
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.secondary,
                    unfocusedTextColor = MaterialTheme.colorScheme.secondary
                )
            ),
            
            inputField = { 
                SearchBarDefaults.InputField(
                    searchBarInputState, 
                    searchBarState, 
                    onSearch = {}, 
                    
                    leadingIcon = {
                        Icon(
                            imageVector = search_icon,
                            contentDescription = null
                        )
                    },
                    
                    placeholder = { 
                        Text("Search") 
                    },
                    trailingIcon = {
                        if (showMessageFilterAction) {
                            IconButton(onClick = onOpenMessageFilters) {
                                Icon(
                                    tint = if (isMessageFiltersActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                    imageVector = filter_list_icon,
                                    contentDescription = "Search filters"
                                )
                            }
                        }
                    }
                )
            }
        )
        
        LaunchedEffect(searchBarInputState.text) {
            onQueryChange(searchBarInputState.text.toString())
        }

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

        // Tabs are pages, like the folder pager of the home screen: swiping
        // moves the selection and tapping a tab scrolls to its page.
        val tabs = SearchTab.entries
        val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) {
            tabs.size
        }

        LaunchedEffect(selectedTab) {
            val page = selectedTab.ordinal

            if (pagerState.currentPage != page) {
                pagerState.animateScrollToPage(page)
            }
        }

        LaunchedEffect(pagerState.settledPage) {
            tabs.getOrNull(pagerState.settledPage)?.let { tab ->
                if (tab != selectedTab) {
                    onSelectTab(tab)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIndex ->
            when (tabs[pageIndex]) {
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

                SearchTab.Messages -> MessageSearchTab(
                    modifier = Modifier.fillMaxSize(),
                    state = messages,
                    query = trimmedQuery,
                    entries = entries,
                    onSearch = onSearchMessages,
                    onOpen = onOpenPost
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
}

/**
 * The filters dialog of the global message search. Applying it narrows the
 * loaded page; changing the account invalidates the page, so the next Search
 * tap queries the narrowed scope.
 */
@Composable
private fun MessageFilters(
    viewModel: SearchScreenViewModel,
    accountOptions: List<Pair<String, String>>,
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    SearchFiltersDialog(
        filter = viewModel.messageFilter,
        range = viewModel.messageDateRange,
        accounts = accountOptions,
        accountId = viewModel.messageAccountId,
        onDismiss = onDismiss,
        onApply = { filter, range, account ->
            onDismiss()
            viewModel.setMessageAccountFilter(account)
            viewModel.applyMessageFilters(filter, range)
        }
    )
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
