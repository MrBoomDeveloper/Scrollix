package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AccountDialogFolder
import com.mrboomdev.scrollix.data.model.AccountDownloadedFile
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.DownloadedFile
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.PostSearchPage
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.model.PublicChatSearchKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.DialogAggregator
import com.mrboomdev.scrollix.data.provider.MessengerProvider
import com.mrboomdev.scrollix.data.provider.ProviderRegistry
import com.mrboomdev.scrollix.data.storage.AccountStore
import com.mrboomdev.scrollix.data.storage.AccountStoreState
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.data.storage.createPlatformStorage
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.data.storage.randomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

object ApiManager {
    private val _accountState = MutableStateFlow(AccountStoreState())
    val accountState: StateFlow<AccountStoreState> = _accountState.asStateFlow()

    private val _dialogs = MutableStateFlow<List<AccountDialogEntry>>(emptyList())
    val dialogs: StateFlow<List<AccountDialogEntry>> = _dialogs.asStateFlow()

    private val _folders = MutableStateFlow<List<AccountDialogFolder>>(emptyList())
    val folders: StateFlow<List<AccountDialogFolder>> = _folders.asStateFlow()

    private val _sessionStates = MutableStateFlow<Map<String, AuthState>>(emptyMap())
    val sessionStates: StateFlow<Map<String, AuthState>> = _sessionStates.asStateFlow()

    private val _dialogsLoading = MutableStateFlow<Set<String>>(emptySet())
    val dialogsLoading: StateFlow<Set<String>> = _dialogsLoading.asStateFlow()

    private val _downloads = MutableStateFlow<List<AccountDownloadedFile>>(emptyList())
    val downloads: StateFlow<List<AccountDownloadedFile>> = _downloads.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, AccountSession>>(emptyMap())

    /** Every live account session, used by the cross-provider sticker picker. */
    val sessions: StateFlow<Map<String, AccountSession>> = _sessions.asStateFlow()
    private val _dialogsByAccount = MutableStateFlow<Map<String, List<Dialog>>>(emptyMap())
    private val _foldersByAccount = MutableStateFlow<Map<String, List<DialogFolder>>>(emptyMap())
    private val _downloadsByAccount = MutableStateFlow<Map<String, List<DownloadedFile>>>(emptyMap())

    private var appScope = newScope()
    private var platformStorage: PlatformStorage? = null
    private var accountStore: AccountStore? = null
    private var initialized = false

    val providers: List<MessengerProvider>
        get() = ProviderRegistry.all()

    fun init() {
        if (initialized) return

        if (appScope.coroutineContext[Job]?.isActive != true) {
            appScope = newScope()
        }

        val storage = runCatching { createPlatformStorage() }.getOrElse { exception ->
            println("Failed to create app storage: ${exception.message}")
            return
        }

        val store = AccountStore(storage)
        platformStorage = storage
        accountStore = store
        initialized = true

        // Files cached by older builds were keyed by TDLib file ids, which are
        // not stable across restarts and could surface another file's bytes.
        // Only those legacy entries are pruned; the current media cache, keyed
        // by remote unique ids, is kept.
        CacheStore.pruneLegacyMedia()
        _accountState.value = store.state.value

        appScope.launch {
            store.state.collect { state ->
                _accountState.value = state
                recomputeDialogs()
                recomputeFolders()
            }
        }

        // The cached list is seeded before any session starts, so the home
        // screen has every dialog (avatar, title, last message, unread count)
        // on the first frame instead of an empty list plus a spinner.
        appScope.launch {
            restoreCachedDialogs()
            startAccounts()
        }
    }

    /** Restores the persisted dialog list of every account. */
    private suspend fun restoreCachedDialogs() {
        val accounts = accountStore?.state?.value?.accounts ?: return

        val restored = accounts.associate { account ->
            account.id to CacheStore.loadDialogs(account.id)
        }.filterValues { dialogs -> dialogs.isNotEmpty() }

        if (restored.isEmpty()) return

        _dialogsByAccount.update { current ->
            // Anything a session already produced wins over the cached copy.
            restored + current
        }

        recomputeDialogs()
    }

    fun session(accountId: String): AccountSession? {
        _sessions.value[accountId]?.let { return it }

        if (!initialized) {
            init()
        }

        val account = accountStore?.account(accountId) ?: return null
        val session = createSession(account) ?: return null
        appScope.launch { session.start() }
        return session
    }

    suspend fun addAccount(providerId: String): String {
        if (!initialized) init()

        val store = accountStore ?: error("Account storage is not initialized")
        val provider = ProviderRegistry.get(providerId)
            ?: error("Unknown messenger provider: $providerId")

        val now = currentTimeMillis()
        val account = AccountRecord(
            id = randomId(),
            providerId = providerId,
            displayName = provider.displayName,
            isPending = true,
            addedAt = now,
            lastUsedAt = now
        )

        store.add(account)

        val session = createSession(account)
            ?: run {
                store.remove(account.id)
                error("Failed to create a session for $providerId")
            }

        appScope.launch {
            session.start()
        }

        return account.id
    }

    suspend fun removeAccount(accountId: String) {
        val session = _sessions.value[accountId]

        if (session != null) {
            val loggedOut = withTimeoutOrNull(10_000.milliseconds) {
                runCatching { session.logout() }.isSuccess
            }

            if (loggedOut != true) {
                runCatching { session.close() }
            }
        }

        _sessions.update { it - accountId }
        _sessionStates.update { it - accountId }
        _dialogsLoading.update { it - accountId }
        _dialogsByAccount.update { it - accountId }
        _foldersByAccount.update { it - accountId }
        _downloadsByAccount.update { it - accountId }
        platformStorage?.delete("accounts/$accountId")
        CacheStore.clearAccount(accountId)
        accountStore?.remove(accountId)
        recomputeDialogs()
        recomputeFolders()
        recomputeDownloads()
    }

    suspend fun cancelPendingAccount(accountId: String) {
        val account = accountStore?.account(accountId) ?: return
        if (account.isPending) {
            removeAccount(accountId)
        }
    }

    suspend fun setDialogPinned(
        accountId: String,
        providerDialogId: String,
        isPinned: Boolean,
        folderId: Int? = null
    ): Result<Unit> {
        val session = session(accountId)
            ?: return Result.failure(IllegalStateException("Account session not found"))

        return session.setDialogPinned(providerDialogId, isPinned, folderId)
    }

    suspend fun deleteDialog(
        accountId: String,
        providerDialogId: String,
        revoke: Boolean = true
    ): Result<Unit> {
        val session = session(accountId)
            ?: return Result.failure(IllegalStateException("Account session not found"))

        val result = session.deleteDialog(providerDialogId, revoke)

        if (result.isSuccess) {
            _dialogs.update { entries ->
                entries.filterNot { entry ->
                    entry.account.id == accountId &&
                        entry.dialog.providerDialogId == providerDialogId
                }
            }
        }

        return result
    }

    suspend fun setUserBlocked(
        accountId: String,
        userId: Long,
        blocked: Boolean
    ): Result<Unit> {
        val session = session(accountId)
            ?: return Result.failure(IllegalStateException("Account session not found"))

        return session.setUserBlocked(userId, blocked)
    }

    suspend fun loadUserProfile(
        accountId: String,
        providerDialogId: String,
        userId: Long? = null
    ): Result<UserProfileDetails> {
        val session = session(accountId)
            ?: return Result.failure(IllegalStateException("Account session not found"))

        return session.loadUserProfile(providerDialogId, userId)
    }

    suspend fun setChatSubscribed(
        accountId: String,
        providerDialogId: String,
        subscribed: Boolean
    ): Result<Unit> {
        val session = session(accountId)
            ?: return Result.failure(IllegalStateException("Account session not found"))

        return session.setChatSubscribed(providerDialogId, subscribed)
    }

    suspend fun votePoll(
        accountId: String,
        providerDialogId: String,
        messageId: Long,
        optionIds: List<String>
    ): Result<Unit> {
        val session = session(accountId)
            ?: return Result.failure(IllegalStateException("Account session not found"))

        return session.votePoll(providerDialogId, messageId, optionIds)
    }

    suspend fun searchGlobalMessages(
        query: String,
        limit: Int = 50
    ): Result<List<ChatMessage>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())

        val sessions = _sessions.value.values.toList()
        if (sessions.isEmpty()) return Result.success(emptyList())

        val results = sessions.flatMap { session ->
            session.searchGlobalMessages(trimmed, limit).getOrDefault(emptyList())
        }

        return Result.success(
            results
                .sortedByDescending { message -> message.timestamp }
                .take(limit)
        )
    }

    /**
     * Chat search across every signed in account: first the chats already on
     * the account, then public chats the account has not joined yet. Public
     * results come from one account because they are the same for every
     * account, while private chats stay account specific.
     */
    suspend fun searchChats(
        query: String,
        limit: Int = 50,
        publicKind: PublicChatSearchKind = PublicChatSearchKind.Any
    ): List<AccountDialogEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val accounts = accountStore?.state?.value?.accounts ?: _accountState.value.accounts

        val ownEntries = _sessions.value.entries.flatMap { (accountId, session) ->
            val account = accounts.firstOrNull { it.id == accountId }
                ?: return@flatMap emptyList()

            session.searchChats(trimmed, limit)
                .getOrDefault(emptyList())
                .map { dialog ->
                    AccountDialogEntry(account = account, dialog = dialog)
                }
        }

        val publicEntries = searchPublicChatsOnce(trimmed, limit, publicKind)

        return (ownEntries + publicEntries).distinctBy { entry ->
            "${entry.account.id}:${entry.dialog.providerDialogId}"
        }
    }

    private suspend fun searchPublicChatsOnce(
        query: String,
        limit: Int,
        kind: PublicChatSearchKind
    ): List<AccountDialogEntry> {
        for ((account, session) in orderedSessions()) {
            val dialogs = session.searchPublicChats(query, limit, kind)
                .getOrDefault(emptyList())

            if (dialogs.isNotEmpty()) {
                return dialogs.map { dialog ->
                    AccountDialogEntry(account = account, dialog = dialog)
                }
            }
        }

        return emptyList()
    }

    /**
     * One page of the provider's global public post search. The page is fetched
     * with a single account, so the free search quota shown by the UI always
     * belongs to the same account that spends it.
     */
    suspend fun searchPublicPosts(
        query: String,
        offset: String = "",
        limit: Int = 30
    ): Result<PostSearchPage> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(PostSearchPage())

        var unsupported: Throwable? = null

        for ((_, session) in orderedSessions()) {
            val result = session.searchPublicPosts(
                query = trimmed,
                offset = offset,
                limit = limit
            )

            if (result.isSuccess) return result

            val error = result.exceptionOrNull()
            if (error !is UnsupportedOperationException) {
                return Result.failure(error ?: IllegalStateException("Post search failed"))
            }

            unsupported = error
        }

        return Result.failure(
            unsupported ?: UnsupportedOperationException("Post search is not supported")
        )
    }

    suspend fun loadPublicPostSearchQuota(query: String): Result<PostSearchQuota> {
        var unsupported: Throwable? = null

        for ((_, session) in orderedSessions()) {
            val result = session.loadPublicPostSearchQuota(query)
            if (result.isSuccess) return result

            val error = result.exceptionOrNull()
            if (error !is UnsupportedOperationException) {
                return Result.failure(error ?: IllegalStateException("Quota is unavailable"))
            }

            unsupported = error
        }

        return Result.failure(
            unsupported ?: UnsupportedOperationException("Quota is unavailable")
        )
    }

    /** The active account comes first because it owns the search quota. */
    private fun orderedSessions(): List<Pair<AccountRecord, AccountSession>> {
        val accounts = accountStore?.state?.value?.accounts ?: _accountState.value.accounts
        val activeId = accountStore?.state?.value?.activeAccountId
            ?: _accountState.value.activeAccountId

        val sessions = _sessions.value
        val orderedIds = listOfNotNull(activeId) +
            sessions.keys.filterNot { id -> id == activeId }

        return orderedIds.mapNotNull { accountId ->
            val account = accounts.firstOrNull { it.id == accountId }
                ?: return@mapNotNull null
            val session = sessions[accountId] ?: return@mapNotNull null
            account to session
        }
    }

    suspend fun setActiveAccount(accountId: String) {
        val store = accountStore ?: return
        store.updateAccount(accountId) {
            it.copy(lastUsedAt = currentTimeMillis())
        }
        store.setActive(accountId)
    }

    suspend fun shutdown() {
        val sessions = _sessions.value.values.toList()

        for (session in sessions) {
            withTimeoutOrNull(5_000.milliseconds) {
                session.close()
            }
        }

        _sessions.value = emptyMap()
        _sessionStates.value = emptyMap()
        _dialogsLoading.value = emptySet()
        _dialogsByAccount.value = emptyMap()
        _foldersByAccount.value = emptyMap()
        _downloadsByAccount.value = emptyMap()
        _dialogs.value = emptyList()
        _folders.value = emptyList()
        _downloads.value = emptyList()
        appScope.cancel()
        initialized = false
    }

    private suspend fun startAccounts() {
        val store = accountStore ?: return
        val state = store.state.value
        val activeAccountId = state.activeAccountId

        val accounts = state.accounts.sortedByDescending {
            if (it.id == activeAccountId) 1 else 0
        }

        for ((index, account) in accounts.withIndex()) {
            if (!initialized) return
            if (index > 0) delay(250.milliseconds)

            val session = createSession(account) ?: continue
            runCatching { session.start() }.onFailure { exception ->
                _sessionStates.update {
                    it + (account.id to AuthState.Error(
                        exception.message ?: "Failed to start account session"
                    ))
                }
            }
        }
    }

    private fun createSession(account: AccountRecord): AccountSession? {
        _sessions.value[account.id]?.let { return it }

        val store = accountStore ?: return null
        if (store.account(account.id) == null) return null

        val storage = platformStorage ?: return null
        val provider = ProviderRegistry.get(account.providerId) ?: return null

        val session = provider.createSession(
            account = account,
            storage = storage,
            scope = appScope
        )

        _sessions.update { it + (account.id to session) }
        _sessionStates.update { it + (account.id to session.authState.value) }
        observeSession(account.id, session)
        return session
    }

    private fun observeSession(accountId: String, session: AccountSession) {
        appScope.launch {
            session.authState.collect { state ->
                if (_sessions.value[accountId] !== session) return@collect
                _sessionStates.update { it + (accountId to state) }

                val store = accountStore ?: return@collect
                val account = store.account(accountId) ?: return@collect

                when (state) {
                    is AuthState.Ready -> {
                        if (account.isPending) {
                            store.updateAccount(accountId) {
                                it.copy(
                                    isPending = false,
                                    lastUsedAt = currentTimeMillis()
                                )
                            }
                        }

                        if (store.state.value.activeAccountId == null) {
                            store.setActive(accountId)
                        }
                    }

                    // Pending means "added but never authenticated". It is
                    // set when the account is added and cleared on Ready.
                    // Re-marking an established account as pending here made
                    // a later Login Back call cancelPendingAccount() delete
                    // the account and its stored session just because it
                    // failed to load or was temporarily disconnected.
                    is AuthState.Challenge,
                    is AuthState.Error,
                    is AuthState.Closed -> Unit

                    is AuthState.Initializing -> Unit
                }
            }
        }

        appScope.launch {
            session.profile.collect { profile ->
                if (_sessions.value[accountId] !== session) return@collect
                if (profile == null) return@collect

                accountStore?.updateAccount(accountId) {
                    it.copy(
                        displayName = profile.displayName,
                        nickname = profile.nickname,
                        avatar = profile.avatar
                    )
                }
            }
        }

        appScope.launch {
            session.dialogs.collect { dialogs ->
                if (_sessions.value[accountId] !== session) return@collect

                // Sessions start with an empty flow. Replacing the cached list
                // with it made the chat list blink empty until the provider
                // finished loading; the real list replaces it when it arrives.
                if (dialogs.isEmpty() && _dialogsByAccount.value[accountId]
                        .orEmpty().isNotEmpty()
                ) {
                    return@collect
                }

                _dialogsByAccount.update { it + (accountId to dialogs) }
                recomputeDialogs()
            }
        }

        appScope.launch {
            session.folders.collect { folders ->
                if (_sessions.value[accountId] !== session) return@collect
                _foldersByAccount.update { it + (accountId to folders) }
                recomputeFolders()
            }
        }

        appScope.launch {
            session.isLoadingDialogs.collect { loading ->
                if (_sessions.value[accountId] !== session) return@collect
                _dialogsLoading.update { active ->
                    if (loading) active + accountId else active - accountId
                }
            }
        }

        appScope.launch {
            session.downloads.collect { downloads ->
                if (_sessions.value[accountId] !== session) return@collect
                _downloadsByAccount.update { it + (accountId to downloads) }
                recomputeDownloads()
            }
        }
    }

    private fun recomputeDialogs() {
        val accounts = accountStore?.state?.value?.accounts ?: _accountState.value.accounts
        _dialogs.value = DialogAggregator.aggregate(
            accounts = accounts,
            dialogsByAccount = _dialogsByAccount.value
        )
    }

    private fun recomputeDownloads() {
        val accounts = accountStore?.state?.value?.accounts ?: _accountState.value.accounts
        val downloadsByAccount = _downloadsByAccount.value

        _downloads.value = accounts
            .flatMap { account ->
                downloadsByAccount[account.id].orEmpty().map { file ->
                    AccountDownloadedFile(account = account, file = file)
                }
            }
            .sortedByDescending { entry -> entry.file.downloadedAt }
    }

    private fun recomputeFolders() {
        val accounts = accountStore?.state?.value?.accounts ?: _accountState.value.accounts
        val foldersByAccount = _foldersByAccount.value

        _folders.value = accounts.flatMap { account ->
            foldersByAccount[account.id].orEmpty().map { folder ->
                AccountDialogFolder(
                    account = account,
                    folder = folder
                )
            }
        }
    }

    private fun newScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
