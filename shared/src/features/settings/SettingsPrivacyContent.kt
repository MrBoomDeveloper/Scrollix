package com.mrboomdev.scrollix.features.settings

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.LastSeenVisibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun SettingsPrivacyContent(accountId: String? = null) {
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val accounts = accountState.accounts.filter { account ->
        (accountId == null || account.id == accountId) &&
            sessionStates[account.id] is AuthState.Ready
    }

    Text(
        text = "These settings are stored on the provider's servers and apply to " +
            "every device signed in to the account.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    if (accounts.isEmpty()) {
        Text(
            text = "Sign in to an account to change its privacy settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        return
    }

    accounts.forEach { account ->
        AccountLastSeenSettings(account.id, account.displayName)
    }
}

@Composable
private fun AccountLastSeenSettings(accountId: String, displayName: String) {
    val session = remember(accountId) { ApiManager.session(accountId) }
    val scope = rememberCoroutineScope()
    val supported = session?.supportsLastSeenPrivacy == true
    var visibility by remember(accountId) { mutableStateOf<LastSeenVisibility?>(null) }
    var busy by remember(accountId) { mutableStateOf(false) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId, supported) {
        if (!supported) return@LaunchedEffect

        visibility = session?.loadLastSeenVisibility()?.getOrNull()
    }

    fun select(value: LastSeenVisibility) {
        val currentSession = session ?: return
        if (busy || value == visibility) return

        busy = true
        error = null

        scope.launch {
            currentSession.setLastSeenVisibility(value)
                .onSuccess { visibility = value }
                .onFailure { failure ->
                    error = failure.message ?: "Failed to change the last seen setting"
                }

            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Registered once for both rows, so a search for "Last seen" scrolls
            // here and tints the account block.
            .settingsAnchor("Last seen")
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Last seen and online",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = when {
                !supported -> "This provider has no last seen setting."
                visibility == null -> "Loading server settings..."
                else -> "Who can see when you were last online."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LastSeenVisibility.entries.forEach { option ->
                if (option == visibility) {
                    Button(onClick = {}) {
                        Text(option.title)
                    }
                } else {
                    TextButton(
                        enabled = supported && !busy,
                        onClick = { select(option) }
                    ) {
                        Text(option.title)
                    }
                }
            }
        }

        error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (busy) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
}
