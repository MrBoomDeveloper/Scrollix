package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.model.AccountRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AccountStoreState(
    val accounts: List<AccountRecord> = emptyList(),
    val activeAccountId: String? = null
)

class AccountStore(
    private val storage: PlatformStorage,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    private val file = "accounts.json"
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AccountStoreState> = _state.asStateFlow()

    fun account(id: String): AccountRecord? {
        return _state.value.accounts.firstOrNull { it.id == id }
    }

    suspend fun add(account: AccountRecord) {
        update { state ->
            state.copy(
                accounts = state.accounts.filterNot { it.id == account.id } + account
            )
        }
    }

    suspend fun update(account: AccountRecord) {
        update { state ->
            state.copy(
                accounts = state.accounts.map {
                    if (it.id == account.id) account else it
                }
            )
        }
    }

    suspend fun updateAccount(
        id: String,
        transform: (AccountRecord) -> AccountRecord
    ) {
        update { state ->
            state.copy(
                accounts = state.accounts.map {
                    if (it.id == id) transform(it) else it
                }
            )
        }
    }

    suspend fun remove(id: String) {
        update { state ->
            val accounts = state.accounts.filterNot { it.id == id }
            state.copy(
                accounts = accounts,
                activeAccountId = when (state.activeAccountId) {
                    id -> accounts.firstOrNull()?.id
                    else -> state.activeAccountId
                }
            )
        }
    }

    suspend fun setActive(id: String?) {
        update { state ->
            state.copy(
                activeAccountId = id?.takeIf { candidate ->
                    state.accounts.any { it.id == candidate }
                }
            )
        }
    }

    private suspend fun update(transform: (AccountStoreState) -> AccountStoreState) {
        mutex.withLock {
            val updated = normalize(transform(_state.value))
            persist(updated)
            _state.value = updated
        }
    }

    private fun load(): AccountStoreState {
        val stored = runCatching { storage.readText(file) }.getOrNull()
            ?: return AccountStoreState()

        return runCatching {
            normalize(json.decodeFromString<AccountStoreState>(stored))
        }.getOrElse {
            println("WARNING: Failed to read $file: ${it.message}")
            AccountStoreState()
        }
    }

    private fun persist(state: AccountStoreState) {
        storage.writeText(file, json.encodeToString(state))
    }

    private fun normalize(state: AccountStoreState): AccountStoreState {
        val active = state.activeAccountId?.takeIf { candidate ->
            state.accounts.any { it.id == candidate }
        } ?: state.accounts.firstOrNull()?.id

        return state.copy(activeAccountId = active)
    }
}
