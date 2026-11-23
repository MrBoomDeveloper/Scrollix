package com.mrboomdev.scrollix.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val DefaultQuickReactions = listOf("👍", "❤️", "🔥", "🎉", "😁", "😢")

/**
 * A reaction that can be either a Unicode emoji or a custom emoji sticker.
 * Stored in settings so pinned reactions and the double-tap reaction survive
 * restarts and are included in settings backups.
 */
@Serializable
data class ReactionPreference(
    val emoji: String = "",
    val customEmojiId: Long? = null
) {
    val key: String
        get() = customEmojiId?.let { id -> "custom:$id" } ?: "emoji:$emoji"
}

@Serializable
data class AppSettings(
    val quickReactions: List<String> = DefaultQuickReactions,
    /** Custom emoji reactions pinned next to the plain emoji ones. */
    val pinnedCustomReactions: List<ReactionPreference> = emptyList(),
    /** Reaction sent by double tapping a message; null means the first pinned one. */
    val doubleTapReaction: ReactionPreference? = null,
    val defaultHomeTab: String = "direct",
    /** Settings schema version, used to apply one-time default changes. */
    val settingsVersion: Int = 0,
    /** User order of folder chips; keys are "all", "folder:<account:id>" or "tab:<index>". */
    val folderOrder: List<String> = emptyList(),
    /** Per-account order of installed sticker packs (ids). */
    val stickerPackOrders: Map<String, List<Long>> = emptyMap(),
    /** Per-account order of installed custom-emoji packs (ids). */
    val emojiPackOrders: Map<String, List<Long>> = emptyMap(),
    val showAllChatsTab: Boolean = false,
    val singleRowFilters: Boolean = false,
    /**
     * Collapses a chat that several accounts share into one row with the
     * account avatars in a column. Off by default: merging hides the separate
     * account copies, so it is an explicit choice.
     */
    val mergeDuplicateDialogs: Boolean = false,
    val showRecentStickersAndEmojis: Boolean = true,
    val showSystemEmojis: Boolean = true,
    val disabledEmojis: Set<String> = emptySet(),
    val useNativeFolders: Boolean = true,
    val hasCustomWallpaper: Boolean = false,
    val wallpaperUpdatedAt: Long = 0,
    val emojiPickerTab: Int = 0
)

private const val CurrentSettingsVersion = 1

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

    /** Serializes the current settings so they can be written into a backup. */
    fun exportJson(): String {
        return json.encodeToString(AppSettings.serializer(), _state.value)
    }

    /** Applies settings from a backup, returning false when it can't be read. */
    fun importJson(text: String): Boolean {
        return runCatching {
            val restored = json.decodeFromString(AppSettings.serializer(), text)

            // Wallpapers aren't part of the backup file, so only claim one
            // when this device actually has it.
            update {
                restored.copy(
                    hasCustomWallpaper = restored.hasCustomWallpaper && readWallpaper() != null
                )
            }
        }.isSuccess
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
        val settings = runCatching {
            val text = storage.readText(FILE_NAME) ?: return@runCatching null
            json.decodeFromString(AppSettings.serializer(), text)
        }.getOrNull() ?: return AppSettings().copy(settingsVersion = CurrentSettingsVersion)

        if (settings.settingsVersion >= CurrentSettingsVersion) return settings

        // Older files stored "all" as the implicit default. Preserve explicit
        // choices of the other tabs while moving the untouched default to DM.
        val migrated = settings.copy(
            defaultHomeTab = if (settings.defaultHomeTab == "all") "direct" else settings.defaultHomeTab,
            settingsVersion = CurrentSettingsVersion
        )

        runCatching {
            storage.writeText(FILE_NAME, json.encodeToString(AppSettings.serializer(), migrated))
        }

        return migrated
    }
}
