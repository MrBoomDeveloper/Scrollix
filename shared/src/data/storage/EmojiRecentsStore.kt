package com.mrboomdev.scrollix.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class EmojiRecentsState(
    val emojis: List<String> = emptyList()
)

/** Keeps the most recently used unicode emoji for the picker's first rows. */
object EmojiRecentsStore {
    private const val FILE_NAME = "emoji_recents.json"
    private const val MAX_RECENTS = 32

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val storage by lazy { createPlatformStorage() }

    private val _recents = MutableStateFlow(read())
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    fun remember(emoji: String) {
        if (emoji.isBlank()) return

        write(
            (listOf(emoji) + _recents.value.filterNot { it == emoji })
                .take(MAX_RECENTS)
        )
    }

    /** Drops a single emoji from the recents list. */
    fun forget(emoji: String) {
        write(_recents.value.filterNot { it == emoji })
    }

    fun clear() {
        write(emptyList())
    }

    private fun write(emojis: List<String>) {
        _recents.value = emojis

        runCatching {
            storage.writeText(
                FILE_NAME,
                json.encodeToString(
                    EmojiRecentsState.serializer(),
                    EmojiRecentsState(emojis)
                )
            )
        }
    }

    private fun read(): List<String> {
        return runCatching {
            val text = storage.readText(FILE_NAME) ?: return@runCatching emptyList()

            json.decodeFromString(
                EmojiRecentsState.serializer(),
                text
            ).emojis
        }.getOrNull() ?: emptyList()
    }
}
