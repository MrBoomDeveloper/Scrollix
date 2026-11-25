package com.mrboomdev.scrollix.features.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrboomdev.scrollix.data.AppMode
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.diagnostics.CrashReports
import com.mrboomdev.scrollix.data.diagnostics.DiagnosticsFacts
import com.mrboomdev.scrollix.data.diagnostics.DiagnosticsFileLog
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.getPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the diagnostics payloads — the saved log and the opt-in crash reports —
 * plus the copy/delete state of their cards. Reading and clearing happen off
 * the main thread; the clipboard itself stays in the composable.
 */
internal class DiagnosticsScreenViewModel : ViewModel() {
    var copied by mutableStateOf(false)
        private set

    var crashReport by mutableStateOf<String?>(null)
        private set

    var crashCopied by mutableStateOf(false)
        private set

    var nonFatalReport by mutableStateOf<String?>(null)
        private set

    var nonFatalCopied by mutableStateOf(false)
        private set

    var savedLog by mutableStateOf<String?>(null)
        private set

    var savedLogCopied by mutableStateOf(false)
        private set

    var protocolDumps by mutableStateOf(AppSettingsStore.state.value.protocolDumpsEnabled)
        private set

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            crashReport = withContext(Dispatchers.IO) { CrashReports.read() }
            nonFatalReport = withContext(Dispatchers.IO) { CrashReports.readNonFatal() }
            savedLog = withContext(Dispatchers.IO) { DiagnosticsFileLog.read() }
        }
    }

    fun report(accountCount: Int): String {
        return AppLog.report(
            DiagnosticsFacts(
                platform = getPlatform().name,
                edition = if (AppMode.isTestServerMode) "Test server" else "Production",
                storageProfile = AppMode.storageProfileName,
                accounts = accountCount,
                generatedAtMillis = currentTimeMillis()
            )
        )
    }

    fun markCopied() {
        copied = true
    }

    fun markCrashCopied() {
        crashCopied = true
    }

    fun markNonFatalCopied() {
        nonFatalCopied = true
    }

    fun markSavedLogCopied() {
        savedLogCopied = true
    }

    fun clearLog() {
        AppLog.clear()
        savedLog = null
        savedLogCopied = false
        copied = false

        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticsFileLog.clear()
        }
    }

    fun deleteCrash() {
        crashReport = null
        crashCopied = false

        viewModelScope.launch(Dispatchers.IO) {
            CrashReports.clear()
        }
    }

    fun deleteNonFatal() {
        nonFatalReport = null
        nonFatalCopied = false

        viewModelScope.launch(Dispatchers.IO) {
            CrashReports.clearNonFatal()
        }
    }

    fun clearSavedLog() {
        savedLog = null
        savedLogCopied = false

        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticsFileLog.clear()
        }
    }

    fun updateProtocolDumps(enabled: Boolean) {
        protocolDumps = enabled
        AppLog.protocolDumpsEnabled = enabled
        AppSettingsStore.update { it.copy(protocolDumpsEnabled = enabled) }
    }
}
