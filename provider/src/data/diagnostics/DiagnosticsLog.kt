package com.mrboomdev.scrollix.data.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Severity of a [LogEntry], ordered from least to most important. */
enum class LogLevel(val label: String) {
    Debug("DEBUG"),
    Info("INFO"),
    Warning("WARN"),
    Error("ERROR")
}

/** One line of the in-memory diagnostics log. */
data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
)

/**
 * Receives every new entry, for example to write a rotating file log. It runs
 * on the logging thread, so an implementation must hand work off instead of
 * doing blocking I/O inline.
 */
fun interface DiagnosticsLogSink {
    fun onEntry(entry: LogEntry)
}

/**
 * Environment facts printed at the top of a copied diagnostics report. They
 * are supplied by the UI because the provider module does not know which
 * platform or edition it is running on.
 */
data class DiagnosticsFacts(
    val platform: String = "",
    val edition: String = "",
    val storageProfile: String = "",
    val accounts: Int? = null,
    val generatedAtMillis: Long = 0L
)

/**
 * A bounded, thread-safe log of the last [capacity] events the app considered
 * worth keeping. It stays in memory only: the report is meant to be copied by
 * the user from the diagnostics screen, not uploaded anywhere.
 *
 * [clock] is injectable so formatting and ordering are testable.
 */
class DiagnosticsLog(
    val capacity: Int = DefaultCapacity,
    private val clock: () -> Long = ::nowMillis
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Entries oldest first, newest last. Collectable by the UI. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun debug(tag: String, message: String) =
        record(LogLevel.Debug, tag, message)

    fun info(tag: String, message: String) =
        record(LogLevel.Info, tag, message)

    fun warning(tag: String, message: String, throwable: Throwable? = null) =
        record(LogLevel.Warning, tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        record(LogLevel.Error, tag, message, throwable)

    /** Records one entry and returns it, or null when the log is disabled. */
    fun record(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ): LogEntry? {
        if (capacity <= 0) return null

        val entry = LogEntry(
            timestampMillis = clock(),
            level = level,
            tag = tag.trim().ifEmpty { "app" },
            message = message.trim().ifEmpty { "(no message)" },
            details = throwable?.describe()
        )

        _entries.update { current ->
            if (current.size >= capacity) {
                current.takeLast(capacity - 1) + entry
            } else {
                current + entry
            }
        }

        return entry
    }

    fun snapshot(): List<LogEntry> = _entries.value

    fun clear() {
        _entries.value = emptyList()
    }

    /** A plain-text report ready for the clipboard. */
    fun report(facts: DiagnosticsFacts = DiagnosticsFacts()): String {
        return formatDiagnosticsReport(facts, snapshot())
    }

    companion object {
        const val DefaultCapacity: Int = 400
    }
}

/**
 * The process-wide log. Providers write to it, the diagnostics screen reads it.
 * Entries stay in memory; an installed [DiagnosticsLogSink] may also persist
 * them. The sink is optional so the provider module stays platform-free.
 */
object AppLog {
    private val log = DiagnosticsLog()
    private var sink: DiagnosticsLogSink? = null
    private var nonFatalHandler: ((tag: String, message: String, throwable: Throwable) -> Unit)? = null

    /**
     * Set from the persisted diagnostics setting at startup. Protocol frames
     * are not recorded while this is false, so the default build stays quiet.
     */
    @Volatile
    var protocolDumpsEnabled: Boolean = false

    val entries: StateFlow<List<LogEntry>> get() = log.entries

    /** Installs or removes the persistence sink; the last one wins. */
    fun attachSink(sink: DiagnosticsLogSink?) {
        this.sink = sink
    }

    /**
     * Installs the local non-fatal crash recorder. It sees errors logged with
     * a throwable, so handled failures can be exported like real crashes.
     */
    fun attachNonFatalHandler(
        handler: ((tag: String, message: String, throwable: Throwable) -> Unit)?
    ) {
        nonFatalHandler = handler
    }

    fun debug(tag: String, message: String) =
        write(LogLevel.Debug, tag, message)

    fun info(tag: String, message: String) =
        write(LogLevel.Info, tag, message)

    fun warning(tag: String, message: String, throwable: Throwable? = null) =
        write(LogLevel.Warning, tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        write(LogLevel.Error, tag, message, throwable)

        if (throwable != null) {
            runCatching { nonFatalHandler?.invoke(tag, message, throwable) }
        }
    }

    /**
     * Records one provider frame when protocol dumps are enabled. Tokens,
     * keys and credentials are replaced before the payload reaches the log.
     */
    fun protocol(provider: String, direction: String, payload: String?) {
        if (!protocolDumpsEnabled) return

        val body = redactProtocolPayload(payload.orEmpty())
        if (body.isBlank()) return

        debug("protocol/$provider", "$direction $body")
    }

    fun snapshot(): List<LogEntry> = log.snapshot()

    fun clear() = log.clear()

    fun report(facts: DiagnosticsFacts): String = log.report(facts)

    private fun write(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val entry = log.record(level, tag, message, throwable) ?: return
        runCatching { sink?.onEntry(entry) }
    }
}

/** Renders the header facts and every entry, oldest first. */
fun formatDiagnosticsReport(
    facts: DiagnosticsFacts,
    entries: List<LogEntry>
): String = buildString {
    appendLine("Scrollix diagnostics")
    appendLine("Generated: ${formatTimestamp(facts.generatedAtMillis)}")

    if (facts.platform.isNotBlank()) {
        appendLine("Platform: ${facts.platform}")
    }

    if (facts.edition.isNotBlank()) {
        appendLine("Edition: ${facts.edition}")
    }

    if (facts.storageProfile.isNotBlank()) {
        appendLine("Storage profile: ${facts.storageProfile}")
    }

    facts.accounts?.let { accounts ->
        appendLine("Accounts: $accounts")
    }

    val errors = entries.count { it.level == LogLevel.Error }
    val warnings = entries.count { it.level == LogLevel.Warning }
    appendLine("Log entries: ${entries.size} ($errors error(s), $warnings warning(s))")

    if (entries.isEmpty()) {
        appendLine()
        appendLine("No events were recorded.")
    } else {
        appendLine()

        entries.forEach { entry -> appendLine(formatLogEntry(entry)) }
    }
}

/** One entry as a plain-text line, with details indented below it. */
fun formatLogEntry(entry: LogEntry): String = buildString {
    append("[${formatTimestamp(entry.timestampMillis)}] ")
    append("${entry.level.label} ${entry.tag} | ${entry.message}")

    entry.details?.lineSequence()?.forEach { line ->
        appendLine()
        append("    $line")
    }
}

/** ISO-8601 UTC, already locale independent. */
fun formatTimestamp(timestampMillis: Long): String =
    Instant.fromEpochMilliseconds(timestampMillis).toString()

@OptIn(ExperimentalTime::class)
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * Redacts credential-looking values from a protocol dump and caps its length.
 * Message text is intentionally kept: the user enabled dumps to diagnose a
 * concrete failure, and the log stays on the device unless they copy it.
 */
fun redactProtocolPayload(payload: String, maxChars: Int = 1_600): String {
    val jsonRedacted = SensitiveJsonValue.replace(payload) { match ->
        "${match.groupValues[1]}\"<redacted>\""
    }
    val formRedacted = SensitiveFormValue.replace(jsonRedacted) { match ->
        "${match.groupValues[1]}<redacted>"
    }

    return if (formRedacted.length > maxChars) {
        formRedacted.take(maxChars) + "... (truncated)"
    } else {
        formRedacted
    }
}

private val SensitiveJsonValue = Regex(
    """(?i)("(?:access_token|token|key|auth|password|phone|code|hash|api_id|api_hash|session)"\s*:\s*)"(?:\\.|[^"\\])*"""
)

private val SensitiveFormValue = Regex(
    """(?i)((?:access_token|token|key|auth|password|phone|code|hash)=)[^&\s]+"""
)

/**
 * A throwable may carry a chain of causes. Keep the chain (it often explains a
 * TDLib failure) but not a full stack trace, which would swamp the report.
 */
private fun Throwable.describe(): String {
    val builder = StringBuilder(this::class.simpleName ?: "Throwable")
    message?.takeIf { it.isNotBlank() }?.let { builder.append(": $it") }

    var current: Throwable? = cause
    var depth = 0

    while (current != null && depth < 4) {
        // Guard against a cycle (`cause === this`).
        if (current === this) break

        builder.appendLine()
        builder.append("Caused by: ")
        builder.append(current::class.simpleName ?: "Throwable")
        current.message?.takeIf { it.isNotBlank() }?.let { builder.append(": $it") }

        current = current.cause
        depth++
    }

    return builder.toString()
}
