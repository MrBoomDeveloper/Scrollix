package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.AppShortcut

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Lists the desktop shortcuts. Esc is included because it is handled by the
 * window and the navigation stack rather than by [AppShortcut].
 */
@Composable
fun ShortcutCheatSheetDialog(onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text("Keyboard shortcuts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Shortcuts work from any chat or settings screen on the " +
                        "desktop. Ctrl works as ⌘ on macOS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                ShortcutEntry(title = "Go back or close an overlay", keys = "Esc")

                AppShortcut.entries.forEach { shortcut ->
                    ShortcutEntry(title = shortcut.title, keys = shortcut.keys)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ShortcutEntry(title: String, keys: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                text = keys,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
