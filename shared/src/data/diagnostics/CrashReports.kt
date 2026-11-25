package com.mrboomdev.scrollix.data.diagnostics

import com.mrboomdev.scrollix.data.AppMode
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.data.storage.createPlatformStorage
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.getPlatform
import com.mrboomdev.scrollix.installPlatformCrashHandler

import kotlin.time.Instant

const val CrashReportFileName = "diagnostics/last-crash.txt"
const val NonFatalReportFileName = "diagnostics/last-nonfatal.txt"
private const val MaxCrashReportChars = 64_000
private const val MaxCrashLogEntries = 80

/** Reads and writes the last crash report; storage is injectable for tests. */
class CrashReportStore(private val storage: PlatformStorage) {
    fun save(report: String) = save(CrashReportFileName, report)

    fun read(): String? = storage.readText(CrashReportFileName)

    fun clear() {
        storage.delete(CrashReportFileName)
    }

    /** Handled failures are kept separately from an uncaught crash. */
    fun saveNonFatal(report: String) = save(NonFatalReportFileName, report)

    fun readNonFatal(): String? = storage.readText(NonFatalReportFileName)

    fun clearNonFatal() {
        storage.delete(NonFatalReportFileName)
    }

    private fun save(fileName: String, report: String) {
        val stored = if (report.length > MaxCrashReportChars) {
            report.take(MaxCrashReportChars) + "\n... crash report truncated ...\n"
        } else {
            report
        }

        storage.writeText(fileName, stored)
    }
}

/**
 * Local crash capture. A report is written only when the user enabled the
 * "Save crash reports" setting; the crash is then handed back to the previous
 * handler, so this never hides a failure. Nothing is uploaded automatically.
 */
object CrashReports {
    private val store by lazy { CrashReportStore(createPlatformStorage()) }
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        installPlatformCrashHandler { threadName, throwable ->
            record(threadName, throwable)
        }
        AppLog.attachNonFatalHandler { tag, message, throwable ->
            recordNonFatal(tag, message, throwable)
        }
    }

    fun record(threadName: String, throwable: Throwable) {
        val enabled = runCatching {
            AppSettingsStore.state.value.crashReportsEnabled
        }.getOrDefault(false)

        if (!enabled) return

        val report = runCatching {
            buildCrashReport(
                threadName = threadName,
                throwable = throwable,
                timestampMillis = currentTimeMillis(),
                platform = getPlatform().name,
                edition = if (AppMode.isTestServerMode) "Test server" else "Production",
                storageProfile = AppMode.storageProfileName,
                logEntries = AppLog.snapshot()
            )
        }.getOrNull() ?: return

        runCatching { store.save(report) }
    }

    /** Captures a handled failure; only written when crash reports are enabled. */
    fun recordNonFatal(tag: String, message: String, throwable: Throwable) {
        val enabled = runCatching {
            AppSettingsStore.state.value.crashReportsEnabled
        }.getOrDefault(false)

        if (!enabled) return

        val report = runCatching {
            buildNonFatalReport(
                tag = tag,
                message = message,
                throwable = throwable,
                timestampMillis = currentTimeMillis(),
                platform = getPlatform().name,
                edition = if (AppMode.isTestServerMode) "Test server" else "Production",
                storageProfile = AppMode.storageProfileName,
                logEntries = AppLog.snapshot()
            )
        }.getOrNull() ?: return

        runCatching { store.saveNonFatal(report) }
    }

    fun read(): String? = runCatching { store.read() }.getOrNull()

    fun readNonFatal(): String? = runCatching { store.readNonFatal() }.getOrNull()

    fun clear() {
        runCatching { store.clear() }
    }

    fun clearNonFatal() {
        runCatching { store.clearNonFatal() }
    }
}

/**
 * Pure report builder so the crash file format is covered by tests. It contains
 * environment facts, the full exception stack and the most recent log lines.
 */
fun buildCrashReport(
    threadName: String,
    throwable: Throwable,
    timestampMillis: Long,
    platform: String,
    edition: String,
    storageProfile: String,
    logEntries: List<LogEntry>
): String = buildString {
    appendLine("Scrollix crash report")
    appendLine("Kind: uncaught")
    appendLine("Generated: ${Instant.fromEpochMilliseconds(timestampMillis)}")
    appendLine("Platform: $platform")
    appendLine("Edition: $edition")
    appendLine("Storage profile: $storageProfile")
    appendLine("Thread: $threadName")
    appendLine()
    appendLine("Exception:")
    appendLine(throwable.stackTraceToString().trim())
    appendLine()
    appendReportLog(logEntries)
}

/**
 * The handled-failure counterpart of [buildCrashReport]. It keeps the log tag
 * and message that describe what the app was doing when the failure surfaced.
 */
fun buildNonFatalReport(
    tag: String,
    message: String,
    throwable: Throwable,
    timestampMillis: Long,
    platform: String,
    edition: String,
    storageProfile: String,
    logEntries: List<LogEntry>
): String = buildString {
    appendLine("Scrollix non-fatal report")
    appendLine("Kind: handled")
    appendLine("Generated: ${Instant.fromEpochMilliseconds(timestampMillis)}")
    appendLine("Platform: $platform")
    appendLine("Edition: $edition")
    appendLine("Storage profile: $storageProfile")
    appendLine("Context: $tag | $message")
    appendLine()
    appendLine("Exception:")
    appendLine(throwable.stackTraceToString().trim())
    appendLine()
    appendReportLog(logEntries)
}

private fun StringBuilder.appendReportLog(logEntries: List<LogEntry>) {
    appendLine("Recent log:")

    if (logEntries.isEmpty()) {
        appendLine("No events recorded.")
    } else {
        logEntries.takeLast(MaxCrashLogEntries).forEach { entry ->
            appendLine(
                "[${Instant.fromEpochMilliseconds(entry.timestampMillis)}] " +
                    "${entry.level.label} ${entry.tag} | ${entry.message}"
            )

            entry.details?.lineSequence()?.forEach { line ->
                appendLine("    $line")
            }
        }
    }
}
