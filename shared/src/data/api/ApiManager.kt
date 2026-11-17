package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AccountDialogFolder
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.DialogAggregator
import com.mrboomdev.scrollix.data.provider.MessengerProvider
import com.mrboomdev.scrollix.data.provider.ProviderRegistry
import com.mrboomdev.scrollix.data.storage.AccountStore
import com.mrboomdev.scrollix.data.storage.AccountStoreState
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

    private val _sessions = MutableStateFlow<Map<String, AccountSession>>(emptyMap())
    private val _dialogsByAccount = MutableStateFlow<Map<String, List<Dialog>>>(emptyMap())
    private val _foldersByAccount = MutableStateFlow<Map<String, List<DialogFolder>>>(emptyMap())

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

        ProviderRegistry.register(TelegramProvider)

        val storage = runCatching { createPlatformStorage() }.getOrElse { exception ->
            println("Failed to create app storage: ${exception.message}")
            return
        }

        val store = AccountStore(storage)
        platformStorage = storage
        accountStore = store
        initialized = true
        _accountState.value = store.state.value

        appScope.launch {
            store.state.collect { state ->
                _accountState.value = state
                recomputeDialogs()
                recomputeFolders()
            }
        }

        appScope.launch {
            startAccounts()
        }
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
        platformStorage?.delete("accounts/$accountId")
        accountStore?.remove(accountId)
        recomputeDialogs()
        recomputeFolders()
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

    suspend fun loadUserProfile(
        accountId: String,
        providerDialogId: String,
        userId: Long? = null
    ): Result<UserProfileDetails> {
        val session = session(accountId)
            ?: return Result.failure(IllegalStateException("Account session not found"))

        return session.loadUserProfile(providerDialogId, userId)
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
        _dialogs.value = emptyList()
        _folders.value = emptyList()
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

                    is AuthState.Challenge,
                    is AuthState.Error,
                    is AuthState.Closed -> {
                        if (!account.isPending) {
                            store.updateAccount(accountId) {
                                it.copy(isPending = true)
                            }
                        }
                    }

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
    }

    private fun recomputeDialogs() {
        val accounts = accountStore?.state?.value?.accounts ?: _accountState.value.accounts
        _dialogs.value = DialogAggregator.aggregate(
            accounts = accounts,
            dialogsByAccount = _dialogsByAccount.value
        )
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
