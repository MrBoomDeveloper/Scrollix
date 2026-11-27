package com.mrboomdev.scrollix.data.storage

import kotlinx.serialization.json.Json

/**
 * Result of reading a settings backup before anything is written. The UI shows
 * the changes and only then applies [Ready.settings].
 */
internal sealed interface SettingsImportPreview {
    data class Ready(
        val settings: AppSettings,
        /** Human-readable list of the values the file changes. */
        val changes: List<String>
    ) : SettingsImportPreview

    /** The file is unreadable or comes from a newer build. */
    data class Refused(val reason: String) : SettingsImportPreview
}

/**
 * Reads a backup without applying it. A file from a newer build is refused
 * instead of imported: unknown fields would be dropped and the next save would
 * silently rewrite the newer file in the old shape.
 */
internal fun previewSettingsBackup(
    json: Json,
    text: String,
    current: AppSettings,
    currentVersion: Int = CurrentSettingsVersion
): SettingsImportPreview {
    val parsed = runCatching {
        json.decodeFromString(AppSettings.serializer(), text)
    }.getOrElse {
        return SettingsImportPreview.Refused(
            "This file is not a Scrollix settings backup."
        )
    }

    if (parsed.settingsVersion > currentVersion) {
        return SettingsImportPreview.Refused(
            "This backup was written by a newer Scrollix (settings version " +
                "${parsed.settingsVersion}, this build reads $currentVersion). " +
                "Update the app before restoring it."
        )
    }

    val migrated = migrateSettings(parsed)
    return SettingsImportPreview.Ready(
        settings = migrated,
        changes = settingsChanges(current, migrated)
    )
}

/**
 * Lists the backup values that differ from [current]. Pure so the preview and
 * its wording are covered by tests; the order follows the settings screen.
 */
internal fun settingsChanges(current: AppSettings, imported: AppSettings): List<String> {
    val changes = mutableListOf<String>()

    fun <T> compare(label: String, before: T, after: T) {
        if (before != after) {
            changes += "$label: $before → $after"
        }
    }

    fun compareCount(label: String, before: Int, after: Int) {
        if (before != after) {
            changes += "$label: $before → $after"
        }
    }

    compare("Default home tab", current.defaultHomeTab, imported.defaultHomeTab)
    compare("Show the \"All\" tab", current.showAllChatsTab, imported.showAllChatsTab)
    compare("Single row filters", current.singleRowFilters, imported.singleRowFilters)
    compare("Merge duplicate chats", current.mergeDuplicateDialogs, imported.mergeDuplicateDialogs)
    compare("Use Telegram folders", current.useNativeFolders, imported.useNativeFolders)
    compare(
        "Recent emoji and stickers",
        current.showRecentStickersAndEmojis,
        imported.showRecentStickersAndEmojis
    )
    compare("System emoji", current.showSystemEmojis, imported.showSystemEmojis)
    compareCount("Hidden emoji", current.disabledEmojis.size, imported.disabledEmojis.size)
    compare("Quick reactions", current.quickReactions, imported.quickReactions)
    compareCount(
        "Pinned custom reactions",
        current.pinnedCustomReactions.size,
        imported.pinnedCustomReactions.size
    )
    compare("Double-tap reaction", current.doubleTapReaction, imported.doubleTapReaction)
    compare(
        "Translation language",
        current.translationLanguage ?: "Device",
        imported.translationLanguage ?: "Device"
    )
    compareCount("Folder order entries", current.folderOrder.size, imported.folderOrder.size)
    compareCount("Sticker pack orders", current.stickerPackOrders.size, imported.stickerPackOrders.size)
    compareCount("Emoji pack orders", current.emojiPackOrders.size, imported.emojiPackOrders.size)
    compare(
        "Media cache limit",
        formatBackupSize(current.mediaCacheLimitBytes),
        formatBackupSize(imported.mediaCacheLimitBytes)
    )
    compareCount("Kept chats", current.keepMediaDialogIds.size, imported.keepMediaDialogIds.size)
    compare("Save crash reports", current.crashReportsEnabled, imported.crashReportsEnabled)
    compare("Protocol dumps", current.protocolDumpsEnabled, imported.protocolDumpsEnabled)
    compare(
        "Auto-play GIFs",
        current.dataSaving.animations,
        imported.dataSaving.animations
    )
    compare(
        "Auto-play round video notes",
        current.dataSaving.videoNotes,
        imported.dataSaving.videoNotes
    )
    compare("Reduce motion", current.reduceMotion, imported.reduceMotion)

    return changes
}

private fun formatBackupSize(bytes: Long): String = when {
    bytes == UnlimitedMediaCacheBytes -> "Unlimited"
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> "$bytes bytes"
}
