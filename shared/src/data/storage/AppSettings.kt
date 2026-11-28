package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.features.home.key

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val DefaultQuickReactions = listOf("👍", "❤️", "🔥", "🎉", "😁", "😢")

/** 0 disables the media cache limit. */
const val UnlimitedMediaCacheBytes: Long = 0L

/** Default budget for cached stickers, custom emoji and avatars. */
const val DefaultMediaCacheLimitBytes: Long = 512L * 1024 * 1024

/** Presets offered in Settings; each value is in megabytes. */
val MediaCacheLimitOptionsMb = listOf(128L, 256L, 512L, 1024L, 2048L)

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
    /**
     * Target language of the chat translator as a bare code ("ru"). Null
     * follows the device language, so a stale choice never survives a system
     * language change.
     */
    val translationLanguage: String? = null,
    val defaultHomeTab: String = "direct",
    /** Settings schema version, used to apply one-time default changes. */
    val settingsVersion: Int = 0,
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
    val hasCustomWallpaper: Boolean = false,
    val wallpaperUpdatedAt: Long = 0,
    val emojiPickerTab: Int = 0,
    /**
     * Budget for the media cache (stickers, custom emoji, avatars). Oldest
     * entries are evicted when it is exceeded. [UnlimitedMediaCacheBytes]
     * disables the budget.
     */
    val mediaCacheLimitBytes: Long = DefaultMediaCacheLimitBytes,
    /**
     * Local data-saver policy: what the app may load without a tap. This is
     * deliberately not a provider setting, so each install controls it.
     */
    val dataSaving: DataSavingSettings = DataSavingSettings(),
    /**
     * Telegram compresses photos on its servers when they are sent as photos.
     * With this off, a picked photo is sent as a document instead, which keeps
     * the original bytes; the switch is a per-install choice.
     */
    val compressPhotos: Boolean = true,
    /**
     * Accessibility: skip the chat appearance animation and the shared-element
     * profile transitions. Nothing else depends on it, so a screen that forgets
     * to read it still works correctly.
     */
    val reduceMotion: Boolean = false,
    /**
     * Opt-in: write a crash report with recent log lines to this device when
     * the process crashes. Reports are never uploaded automatically.
     */
    val crashReportsEnabled: Boolean = false,
    /**
     * Opt-in: record redacted provider protocol frames in the diagnostics log.
     * Off by default because frames can contain message text and identifiers.
     */
    val protocolDumpsEnabled: Boolean = false,
    /**
     * Chats whose cached media is exempt from the media cache limit, keyed by
     * [keepMediaKey]. Only media referenced by the chat's cached messages is
     * protected; pages that are no longer cached can still be evicted.
     */
    val keepMediaDialogIds: Set<String> = emptySet(),
    /**
     * The first-run screen was shown and dismissed. Existing installs never see
     * it: they already have accounts, so the screen is skipped regardless.
     */
    val onboardingCompleted: Boolean = false
)

/** Stable settings key for a chat's keep-media flag. */
fun keepMediaKey(accountId: String, providerDialogId: String): String =
    "$accountId:$providerDialogId"

internal const val CurrentSettingsVersion = 1

/**
 * Upgrades settings written by an older build. Pure so every migration step is
 * covered by tests and can be applied to imported backups as well.
 */
internal fun migrateSettings(settings: AppSettings): AppSettings {
    if (settings.settingsVersion >= CurrentSettingsVersion) return settings

    // Older files stored "all" as the implicit default. Preserve explicit
    // choices of the other tabs while moving the untouched default to DM.
    return settings.copy(
        defaultHomeTab = if (settings.defaultHomeTab == "all") "direct" else settings.defaultHomeTab,
        settingsVersion = CurrentSettingsVersion
    )
}

object AppSettingsStore {
    private const val FILE_NAME = SettingsFileName
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

    /** Reads a backup without writing anything, so the UI can show its changes. */
    internal fun previewImport(text: String): SettingsImportPreview {
        return previewSettingsBackup(json, text, _state.value)
    }

    /** Writes settings the user already saw in a preview. */
    internal fun applyImport(settings: AppSettings) {
        // Wallpapers aren't part of the backup file, so only claim one when
        // this device actually has it.
        update {
            settings.copy(
                hasCustomWallpaper = settings.hasCustomWallpaper && readWallpaper() != null
            )
        }
    }

    /**
     * Applies settings from a backup, returning false when it can't be read or
     * comes from a newer build.
     */
    fun importJson(text: String): Boolean {
        val preview = previewImport(text)
        if (preview !is SettingsImportPreview.Ready) return false
        applyImport(preview.settings)
        return true
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
        val defaults = AppSettings().copy(settingsVersion = CurrentSettingsVersion)

        val text = runCatching { storage.readText(FILE_NAME) }.getOrNull()
            ?: return defaults

        val decoded = runCatching {
            json.decodeFromString(AppSettings.serializer(), text)
        }

        if (decoded.isFailure) {
            // Do not let the next save overwrite settings the user might still
            // want; keep a copy and start from defaults instead.
            StorageIntegrity.quarantine(
                storage = storage,
                fileName = FILE_NAME,
                content = text,
                reason = decoded.exceptionOrNull()?.message ?: "unreadable settings"
            )
            return defaults
        }

        val settings = decoded.getOrThrow()
        val migrated = migrateSettings(settings)
        if (migrated == settings) return settings

        runCatching {
            storage.writeText(FILE_NAME, json.encodeToString(AppSettings.serializer(), migrated))
        }

        return migrated
    }
}
