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

internal const val CurrentAccountStoreVersion = 1

@Serializable
data class AccountStoreState(
    /** Schema version; files written before versioning decode as 0. */
    val version: Int = 0,
    val accounts: List<AccountRecord> = emptyList(),
    val activeAccountId: String? = null
)

/**
 * Upgrades `accounts.json`. Duplicate ids (possible after an interrupted
 * write) collapse to the newest record, and an active account that no longer
 * exists falls back to the first one. Pure for migration tests.
 */
internal fun migrateAccountStore(state: AccountStoreState): AccountStoreState {
    // A newer build owns this file; an older one must not rewrite it.
    if (state.version > CurrentAccountStoreVersion) return state

    val seen = mutableSetOf<String>()
    val accounts = state.accounts.asReversed()
        .filter { account -> seen.add(account.id) }
        .asReversed()

    val active = state.activeAccountId?.takeIf { id ->
        accounts.any { account -> account.id == id }
    } ?: accounts.firstOrNull()?.id

    return state.copy(
        version = CurrentAccountStoreVersion,
        accounts = accounts,
        activeAccountId = active
    )
}

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
            ?: return migrateAccountStore(AccountStoreState())

        return runCatching {
            val decoded = json.decodeFromString<AccountStoreState>(stored)
            val migrated = migrateAccountStore(decoded)

            // Persist the upgraded shape once, so a legacy file is not
            // re-migrated on every launch.
            if (migrated != decoded) {
                runCatching { persist(migrated) }
            }

            migrated
        }.getOrElse {
            println("WARNING: Failed to read $file: ${it.message}")
            migrateAccountStore(AccountStoreState())
        }
    }

    private fun persist(state: AccountStoreState) {
        storage.writeText(file, json.encodeToString(state))
    }

    private fun normalize(state: AccountStoreState): AccountStoreState =
        migrateAccountStore(state)
}
