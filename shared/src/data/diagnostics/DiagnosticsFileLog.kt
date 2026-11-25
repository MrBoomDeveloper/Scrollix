package com.mrboomdev.scrollix.data.diagnostics

import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.data.storage.createPlatformStorage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal const val DiagnosticsLogFileName = "diagnostics/scrollix.log"
internal const val DiagnosticsLogBackupFileName = "diagnostics/scrollix.log.1"

/** Rotate before the file grows past roughly 256k characters. */
internal const val MaxDiagnosticsLogChars = 256_000
private const val DiagnosticsBackupChars = 128_000

/**
 * Writes the diagnostics log to a rotating file below the storage root. It is
 * installed once by [com.mrboomdev.scrollix.data.api.ApiManager] and uses a
 * channel so logging calls never block on disk I/O. Nothing is uploaded.
 */
object DiagnosticsFileLog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<LogEntry>(capacity = Channel.UNLIMITED)

    private var storage: PlatformStorage? = null
    private var installed = false

    fun install(storage: PlatformStorage = createPlatformStorage()) {
        if (installed) return
        installed = true
        this.storage = storage

        AppLog.attachSink { entry -> channel.trySend(entry) }

        scope.launch {
            for (entry in channel) {
                runCatching { appendDiagnosticsEntry(storage, entry) }
            }
        }
    }

    fun read(): String? {
        val storage = storage ?: return null
        return runCatching { storage.readText(DiagnosticsLogFileName) }.getOrNull()
    }

    fun clear() {
        val storage = storage ?: return
        runCatching { storage.delete(DiagnosticsLogFileName) }
        runCatching { storage.delete(DiagnosticsLogBackupFileName) }
    }
}

/**
 * Appends one entry, moving the current file to `.1` and keeping its newest
 * half once [maxChars] would be exceeded. Pure storage logic so rotation is
 * covered without coroutines.
 */
internal fun appendDiagnosticsEntry(
    storage: PlatformStorage,
    entry: LogEntry,
    maxChars: Int = MaxDiagnosticsLogChars
) {
    val line = formatLogEntry(entry) + "\n"
    val current = storage.readText(DiagnosticsLogFileName).orEmpty()

    if (current.isNotEmpty() && current.length + line.length > maxChars) {
        storage.writeText(
            DiagnosticsLogBackupFileName,
            current.takeLast(maxChars.coerceAtMost(DiagnosticsBackupChars))
        )
        storage.writeText(DiagnosticsLogFileName, "")
    }

    storage.appendText(DiagnosticsLogFileName, line)
}
