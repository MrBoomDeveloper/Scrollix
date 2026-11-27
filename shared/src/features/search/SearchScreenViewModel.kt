package com.mrboomdev.scrollix.features.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.storage.DeviceDownloadedFile
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.data.storage.deviceDownloadsDirectory
import com.mrboomdev.scrollix.data.storage.listDeviceDownloads
import com.mrboomdev.scrollix.features.dialog.DialogSearchFilter
import com.mrboomdev.scrollix.features.dialog.filterSearchMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns the search screen: the query, the active tab, debounced chat and public
 * post searches and the downloaded-files listing. Navigation selection stays
 * in the composable because it mirrors the app-level two-pane state.
 */
internal class SearchScreenViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set

    var selectedTab by mutableStateOf(SearchTab.Chats)
        private set

    var remoteEntries by mutableStateOf<List<AccountDialogEntry>>(emptyList())
        private set

    var isSearchingChats by mutableStateOf(false)
        private set

    var postResults by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var postResultDialogs by mutableStateOf<List<Dialog>>(emptyList())
        private set

    var postSearchQuery by mutableStateOf<String?>(null)
        private set

    var postNextOffset by mutableStateOf("")
        private set

    var postQuota by mutableStateOf<PostSearchQuota?>(null)
        private set

    var postLimitsExceeded by mutableStateOf(false)
        private set

    var postSearched by mutableStateOf(false)
        private set

    var isSearchingPosts by mutableStateOf(false)
        private set

    var postError by mutableStateOf<String?>(null)
        private set

    /** Global message search across every account, or the chosen account. */
    var messageResults by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var messageSearched by mutableStateOf(false)
        private set

    var isSearchingMessages by mutableStateOf(false)
        private set

    var messageError by mutableStateOf<String?>(null)
        private set

    /** Query the current [messageResults] belong to, so stale pages can drop. */
    var messageSearchQuery by mutableStateOf<String?>(null)
        private set

    var messageFilter by mutableStateOf(DialogSearchFilter.All)
        private set

    /** Date window applied to the loaded message page. */
    var messageDateRange by mutableStateOf(MessageDateRange())
        private set

    /** Account the message search is narrowed to; null searches every account. */
    var messageAccountId by mutableStateOf<String?>(null)
        private set

    /** Results after the media and date filters; the provider is not queried again. */
    val visibleMessageResults: List<ChatMessage>
        get() = filterMessagesByRange(
            messages = filterSearchMessages(messageResults, messageFilter),
            range = messageDateRange,
            nowMillis = currentTimeMillis()
        )

    var deviceDownloads by mutableStateOf<List<DeviceDownloadedFile>>(emptyList())
        private set

    var downloadsDirectory by mutableStateOf("")
        private set

    var isSearchingDownloads by mutableStateOf(false)
        private set

    private var chatsJob: Job? = null
    private var quotaJob: Job? = null
    private var downloadsLoaded = false
    private var initialized = false

    val trimmedQuery: String
        get() = query.trim()

    fun initialize(initialQuery: String) {
        if (initialized) return
        initialized = true

        query = initialQuery
        selectedTab = if (initialQuery.trimStart().startsWith("#")) {
            SearchTab.Posts
        } else {
            SearchTab.Chats
        }

        scheduleChatSearch()
        scheduleQuotaRefresh()
    }

    fun updateQuery(value: String) {
        query = value
        invalidatePostResults()
        invalidateMessageResults()
        scheduleChatSearch()
        scheduleQuotaRefresh()
    }

    /**
     * Global message search is manual like the post search: it fans out to every
     * account, so a keystroke must not start it.
     */
    fun searchMessages() {
        if (isSearchingMessages) return

        val searchQuery = trimmedQuery
        if (searchQuery.isEmpty()) return

        // A different account or query starts a new page, not an append.
        messageSearchQuery = searchQuery
        messageSearched = true
        messageError = null
        isSearchingMessages = true

        val accountIds = messageAccountId?.let { accountId -> setOf(accountId) }

        // The date narrowing is also sent to the provider, so a range search
        // does not fetch a full page just to drop most of it on this device.
        val (minTimestamp, maxTimestamp) = messageDateRange
            .boundsMillis(currentTimeMillis())

        viewModelScope.launch {
            ApiManager.searchGlobalMessages(
                query = searchQuery,
                limit = SearchPageSize,
                accountIds = accountIds,
                minTimestamp = minTimestamp,
                maxTimestamp = maxTimestamp
            ).onSuccess { results ->
                messageResults = results
            }.onFailure { failure ->
                messageError = failure.message ?: "Could not search messages"
            }

            isSearchingMessages = false
        }
    }

    fun applyMessageFilters(filter: DialogSearchFilter, range: MessageDateRange) {
        messageFilter = filter
        messageDateRange = range
    }

    /** Narrows the next search to one account; null searches every account. */
    fun setMessageAccountFilter(accountId: String?) {
        if (messageAccountId == accountId) return

        messageAccountId = accountId

        // The current page belongs to the previous scope, so it cannot stand.
        messageResults = emptyList()
        messageSearchQuery = null
        messageSearched = false
        messageError = null
    }

    fun selectTab(tab: SearchTab) {
        if (selectedTab == tab) return
        selectedTab = tab

        scheduleChatSearch()
        scheduleQuotaRefresh()

        if (tab == SearchTab.Downloads) loadDownloads()
    }

    /**
     * Public-post search is manual: each page may spend one of the account's
     * free daily searches, so a keystroke never starts one.
     */
    fun searchPosts(loadMore: Boolean) {
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

        viewModelScope.launch {
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

                postResultDialogs = (if (loadMore) postResultDialogs + page.dialogs else page.dialogs)
                    .distinctBy { dialog -> "${dialog.accountId}:${dialog.providerDialogId}" }

                postNextOffset = page.nextOffset
                postLimitsExceeded = page.areLimitsExceeded
                page.quota?.let { quota -> postQuota = quota }
            }.onFailure { error ->
                postError = error.message ?: "Could not search posts"
            }

            isSearchingPosts = false
        }
    }

    private fun scheduleChatSearch() {
        chatsJob?.cancel()

        val query = trimmedQuery
        if (
            query.isEmpty() ||
            selectedTab == SearchTab.Posts ||
            selectedTab == SearchTab.Messages ||
            selectedTab == SearchTab.Downloads
        ) {
            remoteEntries = emptyList()
            isSearchingChats = false
            return
        }

        isSearchingChats = true

        chatsJob = viewModelScope.launch {
            delay(300.milliseconds)
            remoteEntries = ApiManager.searchChats(
                query = query,
                limit = 50,
                publicKind = selectedTab.publicKind
            )
            isSearchingChats = false
        }
    }

    private fun scheduleQuotaRefresh() {
        quotaJob?.cancel()

        if (selectedTab != SearchTab.Posts) return

        quotaJob = viewModelScope.launch {
            delay(400.milliseconds)
            ApiManager.loadPublicPostSearchQuota(trimmedQuery)
                .onSuccess { quota -> postQuota = quota }
        }
    }

    private fun invalidateMessageResults() {
        if (messageSearchQuery == null) return
        if (trimmedQuery == messageSearchQuery) return

        messageResults = emptyList()
        messageSearchQuery = null
        messageSearched = false
        messageError = null
    }

    private fun invalidatePostResults() {
        if (postSearchQuery == null) return
        if (trimmedQuery == postSearchQuery) return

        postResults = emptyList()
        postResultDialogs = emptyList()
        postSearchQuery = null
        postNextOffset = ""
        postSearched = false
        postLimitsExceeded = false
        postError = null
    }

    private fun loadDownloads() {
        if (downloadsLoaded) return
        downloadsLoaded = true

        viewModelScope.launch {
            isSearchingDownloads = true
            downloadsDirectory = deviceDownloadsDirectory()
            deviceDownloads = listDeviceDownloads()
            isSearchingDownloads = false
        }
    }
}
