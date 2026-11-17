package com.mrboomdev.scrollix.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppSettings(
    val showAllChatsTab: Boolean = false,
    val singleRowFilters: Boolean = false,
    val useNativeFolders: Boolean = true
)

object AppSettingsStore {
    private const val FILE_NAME = "settings.json"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val storage by lazy { createPlatformStorage() }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_state.value)
        _state.value = updated

        runCatching {
            storage.writeText(
                FILE_NAME,
                json.encodeToString(AppSettings.serializer(), updated)
            )
        }
    }

    private fun read(): AppSettings {
        return runCatching {
            val text = storage.readText(FILE_NAME) ?: return@runCatching null
            json.decodeFromString(AppSettings.serializer(), text)
        }.getOrNull() ?: AppSettings()
    }
}
