package com.mrboomdev.scrollix.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SavedSearchState(
    /** Schema version, so a future shape change can migrate in place. */
    val version: Int = 1,
    val queries: List<String> = emptyList()
)

/** Cap on saved queries, so the chip row stays a row and not an archive. */
internal const val MaxSavedSearches = 20

/**
 * Puts [query] first and drops any entry that differs only in case or
 * surrounding spaces, keeping the newest [limit] entries. A blank query is
 * ignored, so tapping "Save" with an empty field does nothing.
 */
internal fun withSavedQuery(
    saved: List<String>,
    query: String,
    limit: Int = MaxSavedSearches
): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return saved

    return (listOf(trimmed) + saved.filterNot { it.equals(trimmed, ignoreCase = true) })
        .take(limit)
}

/** Removes one saved query, matching the way [withSavedQuery] stores it. */
internal fun withoutSavedQuery(saved: List<String>, query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return saved

    return saved.filterNot { it.equals(trimmed, ignoreCase = true) }
}

/**
 * Queries the user pinned from the global message search. They live next to the
 * other small JSON stores, so a corrupt file only costs the pinned list instead
 * of the whole settings object.
 */
object SavedSearchStore {
    private const val FILE_NAME = "saved-searches.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val storage by lazy { createPlatformStorage() }

    private val _queries = MutableStateFlow(read())
    val queries: StateFlow<List<String>> = _queries.asStateFlow()

    /** Pins [query], moving it to the front when it was already saved. */
    fun save(query: String) {
        write(withSavedQuery(_queries.value, query))
    }

    fun forget(query: String) {
        write(withoutSavedQuery(_queries.value, query))
    }

    private fun write(queries: List<String>) {
        if (queries == _queries.value) return

        _queries.value = queries

        runCatching {
            storage.writeText(
                FILE_NAME,
                json.encodeToString(
                    SavedSearchState.serializer(),
                    SavedSearchState(queries = queries)
                )
            )
        }
    }

    private fun read(): List<String> {
        return runCatching {
            val text = storage.readText(FILE_NAME) ?: return@runCatching emptyList()

            json.decodeFromString(
                SavedSearchState.serializer(),
                text
            ).queries
        }.getOrNull().orEmpty()
    }
}
