package com.mrboomdev.scrollix.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val DefaultQuickReactions = listOf("👍", "❤️", "🔥", "🎉", "😁", "😢")

@Serializable
data class AppSettings(
    val quickReactions: List<String> = DefaultQuickReactions,
    val defaultHomeTab: String = "all",
    val showAllChatsTab: Boolean = false,
    val singleRowFilters: Boolean = false,
    val showRecentStickersAndEmojis: Boolean = true,
    val showSystemEmojis: Boolean = true,
    val disabledEmojis: Set<String> = emptySet(),
    val useNativeFolders: Boolean = true,
    val hasCustomWallpaper: Boolean = false,
    val wallpaperUpdatedAt: Long = 0
)

object AppSettingsStore {
    private const val FILE_NAME = "settings.json"
    private const val WALLPAPER_FILE = "wallpaper.img"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val storage by lazy { createPlatformStorage() }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun saveWallpaper(bytes: ByteArray) {
        runCatching { storage.writeBytes(WALLPAPER_FILE, bytes) }
        update {
            it.copy(
                hasCustomWallpaper = true,
                wallpaperUpdatedAt = currentTimeMillis()
            )
        }
    }

    fun removeWallpaper() {
        runCatching { storage.delete(WALLPAPER_FILE) }
        update {
            it.copy(
                hasCustomWallpaper = false,
                wallpaperUpdatedAt = currentTimeMillis()
            )
        }
    }

    fun readWallpaper(): ByteArray? {
        return runCatching { storage.readBytes(WALLPAPER_FILE) }.getOrNull()
    }

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
