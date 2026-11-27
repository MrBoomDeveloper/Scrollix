package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.provider.AccountSession

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Sets how long a message in this chat lives before it is deleted for
 * everyone. The value is stored on the server, so every device of the account
 * picks it up; the chat's current value is read first instead of assuming it is
 * what this client last set.
 */
@Composable
internal fun AutoDeleteTimeDialog(
    session: AccountSession,
    providerDialogId: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var current by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(providerDialogId) {
        session.loadMessageAutoDeleteTime(providerDialogId)
            .onSuccess { seconds ->
                current = seconds
                selected = seconds
            }
            .onFailure { failure ->
                error = failure.message ?: "Failed to load the auto-delete timer"
            }
    }

    fun apply() {
        scope.launch {
            busy = true
            error = null

            session.setMessageAutoDeleteTime(providerDialogId, selected)
                .onSuccess { onDismiss() }
                .onFailure { failure ->
                    error = failure.message ?: "Failed to change the auto-delete timer"
                }

            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Auto-delete messages") },
        text = {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "New messages in this chat are deleted for everyone after the selected time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                val loaded = current

                if (loaded == null) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    autoDeletePresets(loaded).forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = preset.seconds }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected == preset.seconds,
                                onClick = { selected = preset.seconds }
                            )

                            Text(
                                modifier = Modifier.padding(start = 4.dp),
                                text = preset.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = current != null && !busy && selected != current,
                onClick = { apply() }
            ) {
                Text(if (busy) "Saving…" else "Apply")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
