package com.mrboomdev.scrollix.data.outbox

import com.mrboomdev.scrollix.data.storage.OutboxFileName
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.data.storage.StorageIntegrity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal const val CurrentOutboxVersion = 3

/** A queue larger than this is unbounded disk usage with no way to inspect it. */
internal const val MaxOutboxEntries = 500

/**
 * Persists the outgoing queue. Entries survive a restart so a message sent
 * while offline is not lost; a `Sending` entry found at startup was interrupted
 * and is put back into `Queued`.
 */
class OutboxStore(
    private val storage: PlatformStorage,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    private val file = OutboxFileName
    private val mutex = Mutex()
    private val _state = MutableStateFlow(read())
    val state: StateFlow<OutboxState> = _state.asStateFlow()

    fun find(id: String): OutboxEntry? {
        return _state.value.entries.firstOrNull { entry -> entry.id == id }
    }

    fun findByLocalMessageId(localMessageId: Long): OutboxEntry? {
        return _state.value.entries.firstOrNull { entry ->
            entry.localMessageId == localMessageId
        }
    }

    suspend fun add(entry: OutboxEntry) {
        update { state ->
            state.copy(
                entries = (state.entries.filterNot { it.id == entry.id } + entry)
                    .takeLast(MaxOutboxEntries)
            )
        }
    }

    suspend fun remove(id: String) {
        update { state ->
            state.copy(entries = state.entries.filterNot { entry -> entry.id == id })
        }
    }

    suspend fun removeAccount(accountId: String) {
        update { state ->
            state.copy(
                entries = state.entries.filterNot { entry -> entry.accountId == accountId }
            )
        }
    }

    /**
     * Drops entries whose account no longer exists on this device, so a queue
     * left behind by a removed account does not retry forever.
     */
    suspend fun pruneUnknownAccounts(knownAccountIds: Set<String>) {
        val current = _state.value
        val kept = entriesForKnownAccounts(current.entries, knownAccountIds)
        if (kept.size == current.entries.size) return

        update { state -> state.copy(entries = kept) }
    }

    /** Applies [transform] and returns the updated entry, or null when it is gone. */
    suspend fun update(
        id: String,
        transform: (OutboxEntry) -> OutboxEntry
    ): OutboxEntry? = mutex.withLock {
        val current = _state.value
        val existing = current.entries.firstOrNull { entry -> entry.id == id }
            ?: return@withLock null

        val updated = transform(existing)
        val next = normalize(
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.id == id) updated else entry
                }
            )
        )

        persist(next)
        _state.value = next
        updated
    }

    /**
     * Marks entries left in `Sending` by a killed process as `Queued` again.
     * Only called once at startup, never while a send is in flight.
     */
    suspend fun recoverInterrupted() {
        mutex.withLock {
            val current = _state.value
            val recovered = current.entries.map { entry ->
                if (entry.state == OutboxEntryState.Sending) {
                    entry.copy(state = OutboxEntryState.Queued)
                } else {
                    entry
                }
            }

            if (recovered == current.entries) return@withLock

            val next = normalize(current.copy(entries = recovered))
            persist(next)
            _state.value = next
        }
    }

    private suspend fun update(transform: (OutboxState) -> OutboxState) {
        mutex.withLock {
            val next = normalize(transform(_state.value))
            persist(next)
            _state.value = next
        }
    }

    private fun read(): OutboxState {
        val stored = runCatching { storage.readText(file) }.getOrNull()
            ?: return migrateOutbox(OutboxState())

        val decoded = runCatching { json.decodeFromString<OutboxState>(stored) }

        if (decoded.isFailure) {
            // The queue is the only copy of unsent messages, so an unreadable
            // file is kept aside instead of being silently replaced by an
            // empty queue on the next write.
            StorageIntegrity.quarantine(
                storage = storage,
                fileName = file,
                content = stored,
                reason = decoded.exceptionOrNull()?.message ?: "unreadable queue"
            )
            return migrateOutbox(OutboxState())
        }

        val migrated = migrateOutbox(decoded.getOrThrow())

        if (migrated != decoded.getOrThrow()) {
            runCatching { persist(migrated) }
        }

        return migrated
    }

    private fun persist(state: OutboxState) {
        storage.writeText(file, json.encodeToString(state))
    }

    private fun normalize(state: OutboxState): OutboxState = migrateOutbox(state)
}

/**
 * Upgrades the queue file. Duplicate ids (an interrupted write) collapse to the
 * newest copy, and the oldest entries are dropped past [MaxOutboxEntries]. A
 * file written by a newer build is left alone.
 *
 * Version 1 stored text sends without a [OutboxEntry.kind]; the serializable
 * default upgrades those entries to [OutboxEntryKind.Text] while decoding.
 * Version 3 added [OutboxEntry.paused] and [OutboxEntry.progress], whose
 * defaults keep a version 2 file valid: an unpaused entry at no progress.
 */
internal fun migrateOutbox(state: OutboxState): OutboxState {
    if (state.version > CurrentOutboxVersion) return state

    val seen = mutableSetOf<String>()
    val entries = state.entries.asReversed()
        .filter { entry -> seen.add(entry.id) }
        .asReversed()
        .takeLast(MaxOutboxEntries)

    return state.copy(
        version = CurrentOutboxVersion,
        entries = entries
    )
}/** Keeps only entries that belong to an account this device still knows. */
internal fun entriesForKnownAccounts(
    entries: List<OutboxEntry>,
    knownAccountIds: Set<String>
): List<OutboxEntry> = entries.filter { entry -> entry.accountId in knownAccountIds }

/** Exponential-ish backoff; the app may stay offline for hours. */
internal fun outboxRetryDelayMillis(attempts: Int): Long {
    return when {
        attempts <= 1 -> 5_000L
        attempts == 2 -> 15_000L
        attempts == 3 -> 60_000L
        else -> 300_000L
    }
}
