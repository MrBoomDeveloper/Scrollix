package com.mrboomdev.scrollix.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.ui.icons.wifi_off_icon

/**
 * Text of the offline banner, or null when every live account is connected.
 * Account names are limited so a fleet of accounts cannot push the action off
 * the screen; the queued count is added only when something is actually waiting.
 */
internal fun offlineBannerMessage(
    accountNames: List<String>,
    queuedCount: Int
): String? {
    if (accountNames.isEmpty()) return null

    val names = when (accountNames.size) {
        1 -> accountNames.single()
        2 -> "${accountNames[0]} and ${accountNames[1]}"
        else -> "${accountNames[0]}, ${accountNames[1]} and " +
            "${accountNames.size - 2} more"
    }

    val queue = when {
        queuedCount <= 0 -> ""
        queuedCount == 1 -> " · 1 message waiting"
        else -> " · $queuedCount messages waiting"
    }

    return "Offline — $names$queue"
}

/**
 * Persistent strip above the chat list while an account has lost its
 * connection. The provider reconnects on its own; the action only restarts the
 * outbox backoff, so queued work does not sit until the next scheduled attempt.
 */
@Composable
fun ConnectionBanner(modifier: Modifier = Modifier) {
    val accountState by ApiManager.accountState.collectAsState()
    val sessions by ApiManager.sessions.collectAsState()
    val connections by ApiManager.connectionStates.collectAsState(initial = emptyMap())
    val queued by OutboxManager.entries.collectAsState()

    // An account without a live session is still signing in, not offline.
    val offlineNames = accountState.accounts
        .filter { account ->
            sessions[account.id] != null && connections[account.id] == false
        }
        .map { account -> account.displayName }

    val waiting = queued.count { entry -> entry.state != OutboxEntryState.Sending }
    val message = offlineBannerMessage(offlineNames, waiting) ?: return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                imageVector = wifi_off_icon,
                contentDescription = null
            )

            Text(
                modifier = Modifier.weight(1f),
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            TextButton(onClick = { OutboxManager.retryAll() }) {
                Text(
                    text = "Retry now",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
