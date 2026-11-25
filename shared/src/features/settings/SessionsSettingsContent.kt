package com.mrboomdev.scrollix.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.ActiveSessionInfo
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import kotlinx.coroutines.launch

/**
 * Server-side device list for the selected account. The current session is
 * labelled and cannot be terminated from here; every other row has an explicit
 * action, and there is a bulk "all other sessions" action behind a confirmation.
 */
@Composable
fun SettingsSessionsContent() {
    val accountState by ApiManager.accountState.collectAsState()
    val accounts = accountState.accounts
    val scope = rememberCoroutineScope()
    var pickedAccountId by remember { mutableStateOf<String?>(null) }

    val selectedAccountId = pickedAccountId
        ?.takeIf { id -> accounts.any { it.id == id } }
        ?: accountState.activeAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: accounts.firstOrNull()?.id

    var sessions by remember(selectedAccountId) {
        mutableStateOf<List<ActiveSessionInfo>?>(null)
    }
    var error by remember(selectedAccountId) { mutableStateOf<String?>(null) }
    var refreshKey by remember(selectedAccountId) { mutableStateOf(0) }
    var busyId by remember(selectedAccountId) { mutableStateOf<Long?>(null) }
    var confirmTerminateAll by remember { mutableStateOf(false) }

    LaunchedEffect(selectedAccountId, refreshKey) {
        val accountId = selectedAccountId

        if (accountId == null) {
            sessions = emptyList()
            return@LaunchedEffect
        }

        val session = ApiManager.session(accountId)

        if (session == null) {
            sessions = emptyList()
            error = "This account has no active session."
            return@LaunchedEffect
        }

        sessions = null
        error = null
        session.loadActiveSessions()
            .onSuccess { loaded -> sessions = loaded }
            .onFailure { failure ->
                sessions = emptyList()
                error = failure.message ?: "Could not load the signed-in devices."
            }
    }

    fun terminate(sessionId: Long) {
        val session = selectedAccountId?.let { ApiManager.session(it) } ?: return

        busyId = sessionId
        error = null
        scope.launch {
            session.terminateSession(sessionId)
                .onSuccess { refreshKey++ }
                .onFailure { failure ->
                    error = failure.message ?: "Could not terminate the device."
                }
            busyId = null
        }
    }

    fun terminateAll() {
        val session = selectedAccountId?.let { ApiManager.session(it) } ?: return

        busyId = AllSessionsBusyId
        error = null
        scope.launch {
            session.terminateOtherSessions()
                .onSuccess { refreshKey++ }
                .onFailure { failure ->
                    error = failure.message ?: "Could not terminate the other devices."
                }
            busyId = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Devices signed in",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Telegram keeps this list on the server. Terminating a device " +
                "signs it out everywhere, including the official apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        if (accounts.isEmpty()) {
            Text(
                text = "Add an account to manage its sessions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            return@Column
        }

        if (accounts.size > 1) {
            AccountChips(
                accounts = accounts,
                selectedAccountId = selectedAccountId,
                onSelect = { id -> pickedAccountId = id }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Active sessions",
                style = MaterialTheme.typography.titleSmall
            )

            TextButton(
                enabled = busyId == null,
                onClick = { refreshKey++ }
            ) {
                Text("Refresh")
            }
        }

        val loaded = sessions

        when {
            loaded == null -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )

                Text(
                    text = "Loading signed-in devices...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            loaded.isEmpty() -> Text(
                text = error ?: "No other devices are signed in to this account.",
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                }
            )

            else -> loaded.forEach { info ->
                SessionCard(
                    session = info,
                    busy = busyId != null,
                    onTerminate = { terminate(info.id) }
                )
            }
        }

        if (error != null && loaded?.isNotEmpty() == true) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = busyId == null && sessions.orEmpty().any { info -> !info.isCurrent },
            onClick = { confirmTerminateAll = true }
        ) {
            Text(
                if (busyId == AllSessionsBusyId) {
                    "Terminating other sessions..."
                } else {
                    "Terminate all other sessions"
                }
            )
        }
    }

    if (confirmTerminateAll) {
        AlertDialog(
            onDismissRequest = { confirmTerminateAll = false },
            title = { Text("Terminate other sessions?") },
            text = {
                Text(
                    "Every device except this one will be signed out of the account. " +
                        "You can sign them back in later."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTerminateAll = false
                        terminateAll()
                    }
                ) {
                    Text("Terminate")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTerminateAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SessionCard(
    session: ActiveSessionInfo,
    busy: Boolean,
    onTerminate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = session.applicationName.ifBlank { "Unknown app" },
                    style = MaterialTheme.typography.titleSmall
                )

                if (session.isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            text = "This device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = session.deviceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            if (session.locationLabel.isNotBlank()) {
                Text(
                    text = session.locationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Text(
                text = "Last active " + sessionTimestampLabel(
                    nowMillis = currentTimeMillis(),
                    timestampMillis = session.lastActiveAtMillis
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            if (session.isPasswordPending) {
                Text(
                    text = "Waiting for a two-step verification password",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!session.isCurrent) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                )

                TextButton(
                    enabled = !busy,
                    onClick = onTerminate
                ) {
                    Text(if (busy) "Working..." else "Terminate")
                }
            }
        }
    }
}

@Composable
private fun AccountChips(
    accounts: List<com.mrboomdev.scrollix.data.model.AccountRecord>,
    selectedAccountId: String?,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        accounts.forEach { account ->
            if (account.id == selectedAccountId) {
                Button(onClick = {}) {
                    Text(account.displayName)
                }
            } else {
                TextButton(onClick = { onSelect(account.id) }) {
                    Text(account.displayName)
                }
            }
        }
    }
}

private const val AllSessionsBusyId = -1L
