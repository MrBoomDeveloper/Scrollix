package com.mrboomdev.scrollix.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Scroll anchor of a dialog: the message that was at the top of the viewport
 * and how far it was scrolled. [atBottom] wins over the anchor when true, so
 * a chat left at its newest message keeps following new messages.
 */
@Serializable
data class DialogScrollPosition(
    val anchorMessageId: Long,
    val offset: Int = 0,
    val atBottom: Boolean = false
)

@Serializable
private data class DialogScrollPositionsState(
    val positions: Map<String, DialogScrollPosition> = emptyMap()
)

/**
 * Remembers where the user left every dialog, so reopening a chat — including
 * after a full app restart — returns to the same message instead of always
 * landing on the newest one.
 */
object DialogScrollPositionStore {
    private const val FILE_NAME = "dialog_positions.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val storage by lazy { createPlatformStorage() }

    private val _positions = MutableStateFlow(read())
    val positions: StateFlow<Map<String, DialogScrollPosition>> = _positions.asStateFlow()

    fun get(accountId: String, providerDialogId: String): DialogScrollPosition? {
        return _positions.value[key(accountId, providerDialogId)]
    }

    fun save(accountId: String, providerDialogId: String, position: DialogScrollPosition) {
        val updated = _positions.value + (key(accountId, providerDialogId) to position)
        _positions.value = updated

        runCatching {
            storage.writeText(
                FILE_NAME,
                json.encodeToString(
                    DialogScrollPositionsState.serializer(),
                    DialogScrollPositionsState(updated)
                )
            )
        }
    }

    private fun key(accountId: String, providerDialogId: String): String {
        return "$accountId:$providerDialogId"
    }

    private fun read(): Map<String, DialogScrollPosition> {
        return runCatching {
            val text = storage.readText(FILE_NAME) ?: return@runCatching emptyMap()

            json.decodeFromString(
                DialogScrollPositionsState.serializer(),
                text
            ).positions
        }.getOrNull() ?: emptyMap()
    }
}
