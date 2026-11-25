package com.mrboomdev.scrollix.features.home

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.api.ConnectionRetry
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.ui.icons.wifi_off_icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
 * connection. The provider reconnects on its own and the queue is retried on a
 * fixed ten-second pulse, so the strip shows that countdown instead of an
 * action the user has to keep pressing.
 */
@Composable
fun ConnectionBanner(modifier: Modifier = Modifier) {
    val accountState by ApiManager.accountState.collectAsState()
    val sessions by ApiManager.sessions.collectAsState()
    val connections by ApiManager.connectionStates.collectAsState(initial = emptyMap())
    val queued by OutboxManager.entries.collectAsState()
    val remainingMillis by ConnectionRetry.remainingMillis.collectAsState()

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
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp),
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
            }

            // Counts down to the next retry, then refills: the bar is the
            // timer the banner used to expose as a "Retry now" button.
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp),
                progress = { remainingMillis / ConnectionRetry.IntervalMillis.toFloat() },
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.errorContainer
            )
        }
    }
}
