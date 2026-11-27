package com.mrboomdev.scrollix.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Local auto-play policy. Nothing here is stored on the provider: the app
 * decides on this device what it loads without a tap.
 *
 * [animations] covers GIFs and animated stickers, [videoNotes] covers round
 * video messages. Photos, videos and files are only fetched after a tap, so
 * they have no automatic download to turn off. The two switches are
 * independent; older settings files that carried a preset profile still decode
 * because unknown fields are ignored.
 */
@Serializable
data class DataSavingSettings(
    val animations: Boolean = true,
    val videoNotes: Boolean = true
)

/** Traffic counted on this device for one account. */
@Serializable
data class LocalDataUsage(
    val sentBytes: Long = 0L,
    val receivedBytes: Long = 0L,
    val sinceMillis: Long = 0L
) {
    val totalBytes: Long get() = sentBytes + receivedBytes
}

@Serializable
private data class LocalDataUsageState(
    val version: Int = 1,
    val accounts: Map<String, LocalDataUsage> = emptyMap()
)

/**
 * Traffic statistics kept in this device's storage instead of read from the
 * provider. Media downloads count as received bytes, text sent from this device
 * counts as sent bytes; traffic that never passes the app is not visible here.
 */
object DataUsageStore {
    private const val FILE_NAME = "data-usage.json"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val storage by lazy { createPlatformStorage() }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Map<String, LocalDataUsage>> = _state.asStateFlow()

    fun snapshot(accountId: String): LocalDataUsage =
        _state.value[accountId] ?: LocalDataUsage()

    fun record(accountId: String, sentBytes: Long = 0L, receivedBytes: Long = 0L) {
        if (accountId.isBlank()) return
        if (sentBytes <= 0 && receivedBytes <= 0) return

        val now = currentTimeMillis()

        _state.value = _state.value.toMutableMap().apply {
            val previous = this[accountId]

            this[accountId] = LocalDataUsage(
                sentBytes = (previous?.sentBytes ?: 0L) + sentBytes.coerceAtLeast(0L),
                receivedBytes = (previous?.receivedBytes ?: 0L) + receivedBytes.coerceAtLeast(0L),
                sinceMillis = previous?.sinceMillis?.takeIf { it > 0 } ?: now
            )
        }

        write()
    }

    fun reset(accountId: String) {
        if (accountId !in _state.value) return

        _state.value = _state.value - accountId
        write()
    }

    /** Drops statistics of a removed account. */
    fun removeAccount(accountId: String) = reset(accountId)

    private fun write() {
        runCatching {
            storage.writeText(
                FILE_NAME,
                json.encodeToString(LocalDataUsageState.serializer(), LocalDataUsageState(accounts = _state.value))
            )
        }
    }

    private fun read(): Map<String, LocalDataUsage> {
        return runCatching {
            val text = storage.readText(FILE_NAME) ?: return emptyMap()
            json.decodeFromString(LocalDataUsageState.serializer(), text).accounts
        }.getOrDefault(emptyMap())
    }
}
