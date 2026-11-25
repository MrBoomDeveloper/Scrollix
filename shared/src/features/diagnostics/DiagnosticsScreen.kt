package com.mrboomdev.scrollix.features.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.AppMode
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.diagnostics.CrashReports
import com.mrboomdev.scrollix.data.diagnostics.DiagnosticsFacts
import com.mrboomdev.scrollix.data.diagnostics.DiagnosticsFileLog
import com.mrboomdev.scrollix.data.diagnostics.LogEntry
import com.mrboomdev.scrollix.data.diagnostics.LogLevel
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.QuarantinedCopy
import com.mrboomdev.scrollix.data.storage.StorageIntegrity
import com.mrboomdev.scrollix.data.storage.createPlatformStorage
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.getPlatform
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.bug_report_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Shows the in-memory diagnostics log, its rotating file copy and the last
 * opt-in crash report so a user can see why something failed and copy the text
 * into a bug report. Nothing is uploaded automatically.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val entries by AppLog.entries.collectAsState()
    val accountState by ApiManager.accountState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    var crashCopied by remember { mutableStateOf(false) }
    var nonFatalReport by remember { mutableStateOf<String?>(null) }
    var nonFatalCopied by remember { mutableStateOf(false) }
    var savedLog by remember { mutableStateOf<String?>(null) }
    var savedLogCopied by remember { mutableStateOf(false) }
    var protocolDumps by remember {
        mutableStateOf(AppSettingsStore.state.value.protocolDumpsEnabled)
    }

    LaunchedEffect(Unit) {
        crashReport = withContext(Dispatchers.IO) { CrashReports.read() }
        nonFatalReport = withContext(Dispatchers.IO) { CrashReports.readNonFatal() }
        savedLog = withContext(Dispatchers.IO) { DiagnosticsFileLog.read() }
    }

    fun currentFacts() = DiagnosticsFacts(
        platform = getPlatform().name,
        edition = if (AppMode.isTestServerMode) "Test server" else "Production",
        storageProfile = AppMode.storageProfileName,
        accounts = accountState.accounts.size,
        generatedAtMillis = currentTimeMillis()
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = arrow_back_icon,
                        contentDescription = "Back"
                    )
                }

                Text(
                    modifier = Modifier.weight(1f),
                    text = "Diagnostics",
                    style = MaterialTheme.typography.titleLarge
                )

                TextButton(
                    onClick = {
                        AppLog.clear()
                        savedLog = null
                        savedLogCopied = false
                        copied = false
                        scope.launch(Dispatchers.IO) { DiagnosticsFileLog.clear() }
                    }
                ) {
                    Text("Clear")
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Recent warnings and errors are kept in memory so a " +
                        "bug report can include them. A rotating copy is saved on " +
                        "this device; nothing is uploaded automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(AppLog.report(currentFacts()))
                        )
                        copied = true
                    }
                ) {
                    Text(if (copied) "Copied to clipboard" else "Copy debug info")
                }

                ProtocolDumpsCard(
                    enabled = protocolDumps,
                    onToggle = { enabled ->
                        protocolDumps = enabled
                        AppLog.protocolDumpsEnabled = enabled
                        AppSettingsStore.update { it.copy(protocolDumpsEnabled = enabled) }
                    }
                )

                DiagnosticsFactsCard(
                    accountCount = accountState.accounts.size,
                    entryCount = entries.size
                )

                NetworkCard()

                StorageHealthCard()

                nonFatalReport?.let { report ->
                    NonFatalReportCard(
                        report = report,
                        copied = nonFatalCopied,
                        onCopy = {
                            clipboard.setText(AnnotatedString(report))
                            nonFatalCopied = true
                        },
                        onDelete = {
                            nonFatalReport = null
                            nonFatalCopied = false
                            scope.launch(Dispatchers.IO) { CrashReports.clearNonFatal() }
                        }
                    )
                }

                crashReport?.let { report ->
                    CrashReportCard(
                        report = report,
                        copied = crashCopied,
                        onCopy = {
                            clipboard.setText(AnnotatedString(report))
                            crashCopied = true
                        },
                        onDelete = {
                            crashReport = null
                            crashCopied = false
                            scope.launch(Dispatchers.IO) { CrashReports.clear() }
                        }
                    )
                }

                savedLog?.let { log ->
                    SavedLogCard(
                        log = log,
                        copied = savedLogCopied,
                        onCopy = {
                            clipboard.setText(AnnotatedString(log))
                            savedLogCopied = true
                        },
                        onClear = {
                            savedLog = null
                            savedLogCopied = false
                            scope.launch(Dispatchers.IO) { DiagnosticsFileLog.clear() }
                        }
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "No events recorded yet",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Errors from sessions, storage and links will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            items(
                count = entries.size,
                key = { index -> index }
            ) { index ->
                DiagnosticsEntryRow(entries[index])
            }
        }
    }
}

@Composable
private fun NetworkCard() {
    val accounts by ApiManager.accountState.collectAsState()
    val sessions by ApiManager.sessions.collectAsState()
    val connections by ApiManager.connectionStates.collectAsState(initial = emptyMap())
    val queued by OutboxManager.entries.collectAsState()

    val waiting = queued.count { entry -> entry.state == OutboxEntryState.Queued }
    val sending = queued.count { entry -> entry.state == OutboxEntryState.Sending }
    val failed = queued.count { entry -> entry.state == OutboxEntryState.Failed }
    val lastError = queued.lastOrNull { entry -> entry.lastError != null }?.lastError

    // The newest entry that has failed at least once carries the retry
    // timeline; older entries would only repeat the same failures.
    val retryHistory = queued.lastOrNull { entry ->
        entry.attemptHistory.isNotEmpty()
    }?.attemptHistory.orEmpty().takeLast(3)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Network",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            if (accounts.accounts.isEmpty()) {
                Text(
                    text = "No accounts are connected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                for (account in accounts.accounts) {
                    val hasSession = sessions[account.id] != null
                    val isConnected = connections[account.id]

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasSession && isConnected != false) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                        )

                        Text(
                            modifier = Modifier.weight(1f),
                            text = account.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = networkConnectionLabel(hasSession, isConnected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            FactRow("Outbox", outboxSummary(waiting, sending, failed))

            lastError?.let { error ->
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(8.dp),
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (retryHistory.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Retry timeline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    for (attempt in retryHistory.asReversed()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatLogTime(attempt.atMillis),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            Text(
                                modifier = Modifier.weight(1f),
                                text = "#${attempt.attempt} " +
                                    (attempt.error ?: "Failed"),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (queued.isNotEmpty()) {
                TextButton(onClick = { OutboxManager.retryAll() }) {
                    Text("Retry all")
                }
            }
        }
    }
}

/**
 * Shows the state files that could not be decoded at startup. They are kept
 * beside the originals as `<name>.corrupt` so unsent messages and settings are
 * not lost silently; the app already fell back to defaults.
 */
@Composable
private fun StorageHealthCard() {
    val scope = rememberCoroutineScope()
    var copies by remember { mutableStateOf<List<QuarantinedCopy>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        copies = withContext(Dispatchers.IO) {
            runCatching {
                StorageIntegrity.copies(createPlatformStorage())
            }.getOrDefault(emptyList())
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Storage health",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            val loaded = copies

            when {
                loaded == null -> Text(
                    text = "Checking the settings and queue files...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                loaded.isEmpty() -> Text(
                    text = "The settings and queue files were readable at startup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                else -> {
                    Text(
                        text = "A file that could not be read is kept here instead of " +
                            "being overwritten. The app started from defaults; inspect " +
                            "the copy before deleting it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    loaded.forEach { copy ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = copy.fileName,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    text = "${copy.sizeBytes} bytes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        StorageIntegrity.deleteCopies(createPlatformStorage())
                                    }
                                }
                                refreshKey++
                            }
                        }
                    ) {
                        Text("Delete copies")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolDumpsCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = bug_report_icon,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Protocol dumps",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = "Record redacted provider frames in the log. " +
                        "Tokens and keys are removed; message text may remain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun NonFatalReportCard(
    report: String,
    copied: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    ReportCard(
        title = "Last handled error",
        description = "Saved on this device because crash reports are enabled. " +
            "Nothing is uploaded automatically.",
        report = report,
        copied = copied,
        copyLabel = "Copy report",
        deleteLabel = "Delete",
        titleColor = MaterialTheme.colorScheme.tertiary,
        onCopy = onCopy,
        onDelete = onDelete
    )
}

@Composable
private fun CrashReportCard(
    report: String,
    copied: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    ReportCard(
        title = "Last crash report",
        description = "Saved on this device because crash reports are enabled. " +
            "Nothing is uploaded automatically.",
        report = report,
        copied = copied,
        copyLabel = "Copy report",
        deleteLabel = "Delete",
        titleColor = MaterialTheme.colorScheme.error,
        onCopy = onCopy,
        onDelete = onDelete
    )
}

@Composable
private fun SavedLogCard(
    log: String,
    copied: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit
) {
    ReportCard(
        title = "Saved log",
        description = "A rotating copy of the diagnostics log on this device. " +
            "It is never uploaded automatically.",
        report = log,
        copied = copied,
        copyLabel = "Copy log",
        deleteLabel = "Clear",
        titleColor = MaterialTheme.colorScheme.primary,
        onCopy = onCopy,
        onDelete = onClear
    )
}

@Composable
private fun ReportCard(
    title: String,
    description: String,
    report: String,
    copied: Boolean,
    copyLabel: String,
    deleteLabel: String,
    titleColor: Color,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(8.dp),
                text = report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) {
                    Text(if (copied) "Copied" else copyLabel)
                }

                TextButton(onClick = onDelete) {
                    Text(deleteLabel)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsFactsCard(accountCount: Int, entryCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FactRow("Platform", getPlatform().name)

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            FactRow(
                label = "Edition",
                value = if (AppMode.isTestServerMode) "Test server" else "Production"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            FactRow("Storage profile", AppMode.storageProfileName)

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            FactRow("Accounts", accountCount.toString())

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            FactRow("Log entries", entryCount.toString())
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DiagnosticsEntryRow(entry: LogEntry) {
    val accent = levelColor(entry.level)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatLogTime(entry.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )

            Text(
                text = entry.level.label,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )

            Text(
                modifier = Modifier.weight(1f),
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodyMedium
        )

        entry.details?.let { details ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(8.dp),
                text = details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
}

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.Error -> MaterialTheme.colorScheme.error
    LogLevel.Warning -> MaterialTheme.colorScheme.tertiary
    LogLevel.Info -> MaterialTheme.colorScheme.primary
    LogLevel.Debug -> MaterialTheme.colorScheme.secondary
}

private fun formatLogTime(timestampMillis: Long): String {
    val time = Instant.fromEpochMilliseconds(timestampMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    return buildString {
        append(time.hour.toString().padStart(2, '0'))
        append(':')
        append(time.minute.toString().padStart(2, '0'))
        append(':')
        append(time.second.toString().padStart(2, '0'))
    }
}
