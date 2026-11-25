package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.diagnostics.AppLog


/** Names of the files the app writes at startup that must never be lost silently. */
internal const val SettingsFileName = "settings.json"
internal const val OutboxFileName = "outbox.json"

/** Suffix of the copy kept when a state file cannot be decoded. */
internal const val CorruptFileSuffix = ".corrupt"

/** One unreadable state file that was set aside instead of overwritten. */
internal data class QuarantinedCopy(
    val fileName: String,
    val sizeBytes: Long
) {
    val label: String
        get() = fileName.removeSuffix(CorruptFileSuffix)
}

/**
 * Startup integrity for the state files. A file that cannot be decoded used to
 * fall back to defaults in memory and then be overwritten by the next save,
 * which threw away whatever the user had queued or configured. Now the bytes
 * are copied to `<name>.corrupt` first, the original is removed so the next
 * launch starts clean, and Diagnostics lists the copy for inspection.
 */
internal object StorageIntegrity {
    private val stateFiles = listOf(SettingsFileName, OutboxFileName)

    /** Copies [content] aside, deletes the unreadable original and logs it. */
    fun quarantine(
        storage: PlatformStorage,
        fileName: String,
        content: String,
        reason: String
    ): String? {
        val copyName = fileName + CorruptFileSuffix

        return runCatching {
            storage.writeText(copyName, content)
            storage.delete(fileName)
            AppLog.warning(
                "storage",
                "Set unreadable $fileName aside as $copyName: $reason"
            )
            copyName
        }.getOrNull()
    }

    /** Copies present on this device, with their size for the Diagnostics list. */
    fun copies(storage: PlatformStorage): List<QuarantinedCopy> {
        return stateFiles.mapNotNull { fileName ->
            val copyName = fileName + CorruptFileSuffix

            if (!runCatching { storage.exists(copyName) }.getOrDefault(false)) {
                return@mapNotNull null
            }

            QuarantinedCopy(
                fileName = copyName,
                sizeBytes = runCatching { storage.size(copyName) }.getOrDefault(0L)
            )
        }
    }

    fun deleteCopies(storage: PlatformStorage) {
        stateFiles.forEach { fileName ->
            runCatching { storage.delete(fileName + CorruptFileSuffix) }
        }
    }
}
